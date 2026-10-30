"""Runs the evaluation set through the real pipeline of the local backend and reports the metrics.

    python run_eval.py --subset small      a few items of each kind, to iterate on prompts cheaply
    python run_eval.py --subset full       every item, for the final numbers (writes results/)

Like infra/seed, it only talks to the local backend (META_API_ENABLED=true, a PERSONAL_DATA_KEY and ADMIN_TOKEN), so
the model cache and the budget cap apply: a second run of the same photos costs almost nothing.

- classification: each photo is reported with no text, so only the photo counts; the report is then discarded.
- negatives: photos with no damage must be rejected (PHOTO_REJECTED), never turned into a report.
- dedup: photo A is confirmed, photo B of the same type is reported 5 m away; B must join A only if it is the same
  damage.
- verification: the "before" photo becomes an Issue, a petition is filed, the agency (admin panel) says it repaired
  it, and the "after" photo is the check. The outcome must match the label; a REPAIRED where the label is not
  REPAIRED is the unsafe error.

Issues it creates carry seed_batch='eval' and are deleted at the start and the end (infra/seed/purge.sql).
"""

import argparse
import json
import sys
from collections import Counter, defaultdict
from datetime import UTC, datetime
from itertools import count
from pathlib import Path

import psycopg
import requests

EVAL = Path(__file__).resolve().parent
ROOT = EVAL.parent
RESULTS = EVAL / "results"
BATCH = "eval"
PASSWORD = "eval-password"
POLICY_VERSION = "2026-10-28"  # PrivacyPolicy.CURRENT_VERSION
REPORTS_PER_ACCOUNT = 5  # below REPORTS_PER_PHONE_PER_DAY, so reruns on the same day do not hit the limit
# One spot per item, 220 m apart, in Suba and Usaquén: far from the demo examples and inside the localities.
GRID_ORIGIN, GRID_STEP, GRID_COLUMNS = (4.7050, -74.0950), 0.002, 12
AGENCY_SAYS_REPAIRED = (
    "Respetado ciudadano: le informamos que la cuadrilla de mantenimiento atendió el daño reportado y realizó la "
    "reparación completa en el sitio. Damos por cerrada su solicitud."
)


class EvalError(Exception):
    pass


def load_env() -> dict[str, str]:
    env = {}
    for line in (ROOT / ".env").read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip("'\"")
    return env


ENV = load_env()
API = f"http://127.0.0.1:{ENV.get('BACKEND_PORT', '8081')}"


def db() -> psycopg.Connection:
    return psycopg.connect(
        host="127.0.0.1",
        port=ENV.get("POSTGRES_PORT", "5433"),
        dbname=ENV["POSTGRES_DB"],
        user=ENV["POSTGRES_USER"],
        password=ENV["POSTGRES_PASSWORD"],
        autocommit=True,
    )


def purge() -> None:
    script = (ROOT / "infra" / "seed" / "purge.sql").read_text().replace(":'batch'", f"'{BATCH}'")
    with db() as conn:
        conn.execute(script)


def expect(response: requests.Response, what: str) -> dict:
    if response.status_code >= 400:
        raise EvalError(f"{what}: HTTP {response.status_code} {response.text[:300]}")
    return response.json() if response.content else {}


class Citizens:
    """Fresh accounts for this run, each used for a few reports so the daily limit never gets in the way."""

    def __init__(self, run_id: str):
        self.run_id = run_id
        self.numbers = count(1)
        self.current: requests.Session | None = None
        self.used = 0

    def next(self) -> requests.Session:
        if self.current is None or self.used >= REPORTS_PER_ACCOUNT:
            self.current = self.account()
            self.used = 0
        self.used += 1
        return self.current

    def account(self) -> requests.Session:
        n = next(self.numbers)
        email = f"eval-{self.run_id}-{n:02d}@example.com"
        session = requests.Session()
        expect(
            session.post(
                f"{API}/api/auth/register",
                json={"email": email, "displayName": f"Evaluación {n}", "password": PASSWORD},
            ),
            "register",
        )
        with db() as conn:
            conn.execute("UPDATE user_account SET email_verified_at = now() WHERE email = %s", (email,))
        profile = {
            "idType": "CC",
            "idNumber": f"99{n:08d}",
            "firstNames": "Evaluación",
            "lastNames": "Prueba Automática",
            "mobilePhone": f"300{n:07d}",
            "address": None,
        }
        expect(
            session.put(
                f"{API}/api/me/filer-profile", json={"profile": profile, "acceptedPolicyVersion": POLICY_VERSION}
            ),
            "filer profile",
        )
        return session


class Spots:
    def __init__(self):
        self.n = count()

    def next(self) -> tuple[float, float]:
        k = next(self.n)
        lat0, lon0 = GRID_ORIGIN
        return lat0 + (k // GRID_COLUMNS) * GRID_STEP, lon0 + (k % GRID_COLUMNS) * GRID_STEP


def photo_file(dataset: dict, name: str) -> tuple[str, bytes, str]:
    path = (EVAL / dataset["photos"][name]["file"]).resolve()
    return (path.name, path.read_bytes(), "image/jpeg")


def report(session: requests.Session, dataset: dict, name: str, where: tuple[float, float]) -> dict:
    return expect(
        session.post(
            f"{API}/api/reports",
            files={"photo": photo_file(dataset, name)},
            data={"latitude": where[0], "longitude": where[1], "text": ""},
        ),
        f"report {name}",
    )


def confirm(session: requests.Session, analysis: dict, accepted: bool = True) -> dict:
    return expect(
        session.post(f"{API}/api/reports/{analysis['reportId']}/confirmation", json={"accepted": accepted}),
        "confirmation",
    )


def area(report_id: str) -> float | None:
    with db() as conn:
        row = conn.execute(
            "SELECT (analysis->>'areaFraction')::float FROM report WHERE id = %s", (report_id,)
        ).fetchone()
    return row[0] if row else None


def classify(dataset, items, citizens, spots) -> list[dict]:
    rows = []
    for item in items:
        session = citizens.next()
        analysis = report(session, dataset, item["photo"], spots.next())
        accepted = analysis["outcome"] == "AWAITING_CONFIRMATION"
        if accepted:
            confirm(session, analysis, accepted=False)
        expected = item.get("label")
        allowed = item.get("accept", [expected]) if expected else []
        row = {
            "photo": item["photo"],
            "label": expected,
            "outcome": analysis["outcome"],
            "damageType": analysis["damageType"],
            # A photo with no damage must be rejected outright, not sent to manual review as OTHER.
            "correct": (accepted and analysis["damageType"] in allowed)
            if expected
            else analysis["outcome"] == "PHOTO_REJECTED",
            "area": area(analysis["reportId"]) if accepted else None,
        }
        rows.append(row)
        print(
            f"  {item['photo']:22} {analysis['outcome']:22} {analysis['damageType'] or '-':26} "
            f"{'ok' if row['correct'] else 'MISS'}"
        )
    return rows


def deduplicate(dataset, pairs, citizens, spots) -> list[dict]:
    rows = []
    for pair in pairs:
        lat, lon = spots.next()
        first = report(citizens.next(), dataset, pair["a"], (lat, lon))
        row = {"a": pair["a"], "b": pair["b"], "same": pair["same"], "typeA": first["damageType"]}
        if first["outcome"] != "AWAITING_CONFIRMATION":
            row["error"] = f"A ended in {first['outcome']}"
            rows.append(row)
            print(f"  {pair['a']} + {pair['b']}: {row['error']}")
            continue
        issue = confirm(citizens.current, first)
        if not issue["newIssue"]:
            raise EvalError(f"{pair['a']} joined an existing Issue: move GRID_ORIGIN or purge the batch")
        tag(issue["issueId"])
        session = citizens.next()
        second = report(session, dataset, pair["b"], (lat + 5 / 111_320, lon))
        row["typeB"] = second["damageType"]
        joined = False
        if second["outcome"] == "AWAITING_CONFIRMATION":
            result = confirm(session, second)
            joined = not result["newIssue"] and result["issueId"] == issue["issueId"]
            tag(result["issueId"])
        row["joined"] = joined
        row["correct"] = joined == pair["same"]
        rows.append(row)
        print(
            f"  {pair['a']} + {pair['b']}: {'joined' if joined else 'separate'} "
            f"({'same' if pair['same'] else 'different'} damage) {'ok' if row['correct'] else 'MISS'}"
        )
    return rows


def verify(dataset, pairs, citizens, spots) -> list[dict]:
    rows = []
    for pair in pairs:
        session = citizens.next()
        first = report(session, dataset, pair["before"], spots.next())
        row = {
            "before": pair["before"],
            "after": pair["after"],
            "expected": pair["expected"],
            "synthetic": pair.get("synthetic", False),
        }
        if first["outcome"] != "AWAITING_CONFIRMATION":
            row["error"] = f"before photo ended in {first['outcome']}"
            rows.append(row)
            print(f"  {pair['before']} → {pair['after']}: {row['error']}")
            continue
        issue = confirm(session, first)
        tag(issue["issueId"])
        issue_id = issue["issueId"]
        expect(
            session.post(f"{API}/api/issues/{issue_id}/petitions", json={"acceptedPolicyVersion": POLICY_VERSION}),
            "petition",
        )
        reply = expect(
            requests.post(
                f"{API}/api/admin/issues/{issue_id}/agency-responses",
                headers={"X-Admin-Token": ENV["ADMIN_TOKEN"]},
                json={"body": AGENCY_SAYS_REPAIRED},
            ),
            "agency response",
        )
        if reply["status"] != "REPAIR_CLAIMED":
            row["error"] = f"agency reply read as {reply['classification']}"
            rows.append(row)
            print(f"  {pair['before']} → {pair['after']}: {row['error']}")
            continue
        check = expect(
            citizens.next().post(
                f"{API}/api/issues/{issue_id}/verifications", files={"photo": photo_file(dataset, pair["after"])}
            ),
            "verification",
        )
        row.update(outcome=check["outcome"], reason=check["reason"], correct=check["outcome"] == pair["expected"])
        rows.append(row)
        print(
            f"  {pair['before']} → {pair['after']}: {check['outcome']} (expected {pair['expected']}) "
            f"{'ok' if row['correct'] else 'MISS'} · {check['reason']}"
        )
    return rows


def tag(issue_id: str) -> None:
    with db() as conn:
        conn.execute("UPDATE issue SET seed_batch = %s WHERE id = %s", (BATCH, issue_id))


def ratio(hits: int, total: int) -> str:
    return f"{hits}/{total} ({100 * hits / total:.0f} %)" if total else "–"


def costs(since: datetime) -> dict:
    with db() as conn:
        spent = conn.execute(
            "SELECT purpose, count(*), coalesce(sum(cost_usd), 0) FROM model_call WHERE created_at >= %s GROUP BY 1",
            (since,),
        ).fetchall()
        # Average over every call ever made, not just this run: with the cache, a rerun pays for almost nothing.
        per_report = conn.execute(
            """
            SELECT coalesce(avg(cost_usd) FILTER (WHERE purpose = 'INTERPRET_REPORT'), 0)
                 + coalesce(avg(cost_usd) FILTER (WHERE purpose = 'VISION_ANALYZE'), 0)
            FROM model_call WHERE success
            """
        ).fetchone()[0]
    return {
        "thisRunUsd": float(sum(row[2] for row in spent)),
        "thisRunCalls": {row[0]: row[1] for row in spent},
        "averagePerReportUsd": float(per_report),
    }


def summarize(results: dict) -> str:
    lines = []
    damage = [r for r in results["classification"] if r["label"]]
    negatives = [r for r in results["classification"] if not r["label"]]
    lines.append(f"Classification (damage type, photo only): {ratio(sum(r['correct'] for r in damage), len(damage))}")
    by_type = defaultdict(list)
    for r in damage:
        by_type[r["label"]].append(r)
    for label, rows in sorted(by_type.items()):
        seen = Counter(r["damageType"] if r["outcome"] == "AWAITING_CONFIRMATION" else r["outcome"] for r in rows)
        misses = ", ".join(
            f"{k} ×{v}" for k, v in seen.items() if k not in rows[0].get("accept", [label]) and k != label
        )
        lines.append(
            f"  {label:26} {ratio(sum(r['correct'] for r in rows), len(rows))}"
            + (f"  misses: {misses}" if misses else "")
        )
    lines.append(f"Photos with no damage rejected: {ratio(sum(r['correct'] for r in negatives), len(negatives))}")
    accepted = [r for r in damage if r["outcome"] == "AWAITING_CONFIRMATION"]
    lines.append(
        f"SAM found the damage (area > 0) in accepted reports: "
        f"{ratio(sum((r['area'] or 0) > 0 for r in accepted), len(accepted))}"
    )
    pairs = [r for r in results["dedup"] if "joined" in r]
    same, different = [r for r in pairs if r["same"]], [r for r in pairs if not r["same"]]
    joined = [r for r in pairs if r["joined"]]
    lines.append(
        f"Dedup: same damage joined {ratio(sum(r['joined'] for r in same), len(same))}, "
        f"different damage kept apart {ratio(sum(not r['joined'] for r in different), len(different))}, "
        f"precision {ratio(sum(r['same'] for r in joined), len(joined))}"
    )
    checks = [r for r in results["verification"] if "outcome" in r]
    real = [r for r in checks if not r["synthetic"]]
    unsafe = [r for r in checks if r["outcome"] == "REPAIRED" and r["expected"] != "REPAIRED"]
    lines.append(
        f"Verification: {ratio(sum(r['correct'] for r in checks), len(checks))} "
        f"(real pairs {ratio(sum(r['correct'] for r in real), len(real))}); "
        f"wrongly verified as repaired: {len(unsafe)}"
    )
    errors = [r for r in results["dedup"] + results["verification"] if "error" in r]
    if errors:
        lines.append(f"Items that could not run: {len(errors)}")
    c = results["costs"]
    lines.append(f"Cost: this run ${c['thisRunUsd']:.4f}; average per report ${c['averagePerReportUsd']:.4f}")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--subset", choices=("small", "full"), default="small")
    args = parser.parse_args()
    dataset = json.loads((EVAL / "dataset.json").read_text())

    def pick(items):
        return [i for i in items if args.subset == "full" or i.get("small")]

    requests.get(f"{API}/actuator/health", timeout=5).raise_for_status()
    started = datetime.now(UTC)
    run_id = started.strftime("%Y%m%d%H%M%S")
    citizens, spots = Citizens(run_id), Spots()
    purge()
    try:
        print("Classification")
        rows = classify(dataset, pick(dataset["classification"]), citizens, spots)
        print("Photos with no damage")
        rows += classify(dataset, pick(dataset["negatives"]), citizens, spots)
        print("Dedup")
        dedup = deduplicate(dataset, pick(dataset["dedup"]), citizens, spots)
        print("Verification")
        checks = verify(dataset, pick(dataset["verification"]), citizens, spots)
    except EvalError as e:
        sys.exit(f"FAILED: {e}")
    finally:
        purge()
    results = {
        "subset": args.subset,
        "startedAt": started.isoformat(),
        "classification": rows,
        "dedup": dedup,
        "verification": checks,
        "costs": costs(started),
    }
    text = summarize(results)
    print("\n" + text)
    if args.subset == "full":
        RESULTS.mkdir(exist_ok=True)
        out = RESULTS / f"{started.date().isoformat()}.json"
        out.write_text(json.dumps({**results, "summary": text.splitlines()}, indent=1, ensure_ascii=False))
        print(f"\nWritten to {out}")


if __name__ == "__main__":
    main()
