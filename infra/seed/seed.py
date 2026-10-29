"""Demo example Issues: real street photos edited with AI, run through the real flow on the local stack.

    python seed.py candidates [key ...]   Mapillary photos around each scene, to choose the base photo
    python seed.py pick KEY IMAGE_ID      use that photo: moves the scene's point to where it was taken
    python seed.py run [key ...]          reports, "yo también", petitions, agency replies and checks (local backend)
    python seed.py reset KEY              deletes that scene's Issue locally, to run it again
    python seed.py export                 out/seed.sql + out/media.tar for production

The local backend must run with META_API_ENABLED=true and a PERSONAL_DATA_KEY (see README.md). Every model call goes
through the backend, so its cache and budget apply.
"""

import argparse
import json
import os
import random
import shutil
import sys
import tarfile
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import holidays
import psycopg
import requests
from psycopg import sql

SEED_DIR = Path(__file__).resolve().parent
ROOT = SEED_DIR.parents[1]
IMAGES = SEED_DIR / "images"
WORK = SEED_DIR / "work"
OUT = SEED_DIR / "out"
SCENES_FILE = SEED_DIR / "scenes.json"
STATE_FILE = WORK / "state.json"

BOGOTA = ZoneInfo("America/Bogota")
PASSWORD = "demo-seed-password"
POLICY_VERSION = "2026-10-28"  # PrivacyPolicy.CURRENT_VERSION
BUSINESS_DAYS = 15  # ResponseDeadline.BUSINESS_DAYS

FILED = {"FILED", "AGENCY_RESPONDED", "REPAIR_CLAIMED", "VERIFIED", "REOPENED", "NEEDS_MORE_EVIDENCE"}
RESPONDED = FILED - {"FILED"}
CHECKED = {"VERIFIED": "REPAIRED", "REOPENED": "NOT_REPAIRED", "NEEDS_MORE_EVIDENCE": "INCONCLUSIVE"}


class SeedError(Exception):
    pass


def load_env() -> dict[str, str]:
    env = {}
    for line in (ROOT / ".env").read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip("'\"")
    env.update(os.environ)
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


def media_root() -> Path:
    path = Path(ENV.get("MEDIA_STORAGE_PATH", "./data/media"))
    # spring-boot:run starts in backend/, so relative paths are relative to it.
    return path if path.is_absolute() else (ROOT / "backend" / path).resolve()


def load_scenes() -> dict:
    return json.loads(SCENES_FILE.read_text())


def selected(config: dict, keys: list[str]) -> list[dict]:
    scenes = config["scenes"]
    unknown = set(keys) - {s["key"] for s in scenes}
    if unknown:
        raise SeedError(f"unknown scenes: {', '.join(sorted(unknown))}")
    return [s for s in scenes if not keys or s["key"] in keys]


def load_state() -> dict:
    return json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else {}


def save_state(state: dict) -> None:
    WORK.mkdir(exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2))


# --- Mapillary ---------------------------------------------------------------------------------------------------

MAPILLARY_FIELDS = "id,captured_at,compass_angle,computed_geometry,geometry,thumb_2048_url,creator,is_pano"


def candidates(config: dict, keys: list[str]) -> None:
    token = ENV.get("MAPILLARY_TOKEN") or sys.exit("Set MAPILLARY_TOKEN in .env (free at mapillary.com/developer).")
    for scene in selected(config, keys):
        lat, lon = scene["point"]
        d = 0.0012  # ~130 m around the point
        r = requests.get(
            "https://graph.mapillary.com/images",
            timeout=30,
            params={
                "access_token": token,
                "fields": MAPILLARY_FIELDS,
                "limit": 60,
                "bbox": f"{lon - d},{lat - d},{lon + d},{lat + d}",
            },
        )
        r.raise_for_status()
        images = [i for i in r.json().get("data", []) if not i.get("is_pano")]
        images.sort(key=lambda i: i.get("captured_at", 0), reverse=True)
        target = WORK / "candidates" / scene["key"]
        shutil.rmtree(target, ignore_errors=True)
        target.mkdir(parents=True)
        for image in images[:8]:
            (target / f"{image['id']}.jpg").write_bytes(requests.get(image["thumb_2048_url"], timeout=60).content)
        (target / "meta.json").write_text(json.dumps(images[:8], indent=2))
        print(f"{scene['key']}: {min(len(images), 8)} candidates in {target.relative_to(ROOT)}")


def pick(config: dict, key: str, image_id: str) -> None:
    scene = selected(config, [key])[0]
    meta = json.loads((WORK / "candidates" / key / "meta.json").read_text())
    image = next((i for i in meta if i["id"] == image_id), None) or sys.exit(f"{image_id} is not a candidate of {key}")
    lon, lat = (image.get("computed_geometry") or image["geometry"])["coordinates"]
    scene["point"] = [round(lat, 6), round(lon, 6)]
    scene["base"] = {
        "mapillaryId": image_id,
        "creator": image.get("creator", {}).get("username"),
        "capturedAt": image.get("captured_at"),
    }
    (IMAGES / key).mkdir(parents=True, exist_ok=True)
    shutil.copy(WORK / "candidates" / key / f"{image_id}.jpg", IMAGES / key / "base.jpg")
    SCENES_FILE.write_text(json.dumps(config, indent=2, ensure_ascii=False) + "\n")
    print(f"{key}: base photo {image_id}, point moved to {scene['point']}")


def credit(scene: dict) -> str | None:
    base = scene.get("base")
    if not base:
        return None
    author = f"{base['creator']} en Mapillary" if base.get("creator") else "Mapillary"
    return f"Foto base: {author} (CC BY-SA 4.0), imagen {base['mapillaryId']}. Daño agregado con IA."


# --- Running the flow --------------------------------------------------------------------------------------------


def expect(response: requests.Response, what: str) -> dict:
    if response.status_code >= 400:
        raise SeedError(f"{what}: HTTP {response.status_code} {response.text[:300]}")
    return response.json() if response.content else {}


def accounts(config: dict) -> list[requests.Session]:
    sessions = []
    with db() as conn:
        for i, name in enumerate(config["accounts"]):
            email = f"demo{i + 1:02d}@example.com"
            session = requests.Session()
            login = session.post(f"{API}/api/auth/login", json={"email": email, "password": PASSWORD})
            if login.status_code >= 400:
                expect(
                    session.post(
                        f"{API}/api/auth/register", json={"email": email, "displayName": name, "password": PASSWORD}
                    ),
                    "register",
                )
            conn.execute(
                "UPDATE user_account SET email_verified_at = coalesce(email_verified_at, now()) WHERE email = %s",
                (email,),
            )
            sessions.append(session)
    return sessions


def photo(scene: dict, name: str) -> tuple[str, bytes, str]:
    path = IMAGES / scene["key"] / f"{name}.jpg"
    if not path.exists():
        raise SeedError(f"{scene['key']}: missing {path.relative_to(ROOT)}")
    return (path.name, path.read_bytes(), "image/jpeg")


def filer_profile(session: requests.Session, index: int, name: str) -> None:
    first, last = name.split(" ", 1)
    profile = {
        "idType": "CC",
        "idNumber": f"10{index + 1:02d}456789",
        "firstNames": first,
        "lastNames": last,
        "mobilePhone": f"30{index + 1:01d}5550{index + 1:03d}",
        "address": None,
    }
    expect(
        session.put(f"{API}/api/me/filer-profile", json={"profile": profile, "acceptedPolicyVersion": POLICY_VERSION}),
        "filer profile",
    )


def run_scene(config: dict, scene: dict, sessions: list[requests.Session], state: dict) -> list[str]:
    key, target = scene["key"], scene["target"]
    warnings = []
    reporter = sessions[scene["reporter"]]
    lat, lon = scene["point"]

    analysis = expect(
        reporter.post(
            f"{API}/api/reports",
            files={"photo": photo(scene, "before")},
            data={"latitude": lat, "longitude": lon, "text": scene["context"]},
        ),
        "report",
    )
    if analysis["outcome"] != "AWAITING_CONFIRMATION":
        raise SeedError(f"{key}: the analysis ended in {analysis['outcome']}, nothing was created")
    if analysis["damageType"] != scene["damageType"]:
        raise SeedError(f"{key}: Muse saw {analysis['damageType']}, expected {scene['damageType']}; nothing created")
    confirmation = expect(
        reporter.post(f"{API}/api/reports/{analysis['reportId']}/confirmation", json={"accepted": True}), "confirmation"
    )
    issue_id = confirmation["issueId"]
    state[key] = {"issue": issue_id, "done": False}
    save_state(state)
    if not confirmation["newIssue"]:
        raise SeedError(f"{key}: joined an existing Issue {issue_id}; `reset {key}` and move the point")
    print(f"  Issue {issue_id} ({analysis['agency']}, {analysis['locality']}): {analysis['summary']}")

    for endorser in scene["endorsers"]:
        expect(sessions[endorser].post(f"{API}/api/issues/{issue_id}/endorsements"), "endorsement")

    if target in FILED:
        filer_profile(reporter, scene["reporter"], config["accounts"][scene["reporter"]])
        expect(
            reporter.post(f"{API}/api/issues/{issue_id}/petitions", json={"acceptedPolicyVersion": POLICY_VERSION}),
            "petition",
        )

    if target in RESPONDED:
        reply = expect(
            requests.post(
                f"{API}/api/admin/issues/{issue_id}/agency-responses",
                headers={"X-Admin-Token": ENV["ADMIN_TOKEN"]},
                json={"body": scene["agencyResponse"]},
            ),
            "agency response",
        )
        wanted = "EVASIVE" if target == "AGENCY_RESPONDED" else "RESOLVES"
        if reply["classification"] != wanted:
            warnings.append(f"reply read as {reply['classification']}, expected {wanted}")
        print(f"  Reply: {reply['classification']} → {reply['status']}")

    if target in CHECKED:
        after = photo(scene, "after")
        check = expect(
            sessions[scene["verifier"]].post(f"{API}/api/issues/{issue_id}/verifications", files={"photo": after}),
            "verification",
        )
        if check["outcome"] != CHECKED[target]:
            warnings.append(f"check gave {check['outcome']}, expected {CHECKED[target]}")
        print(f"  Check: {check['outcome']} → {check['status']} ({check['reason']})")

    with db() as conn:
        backdate(conn, scene, issue_id)
        conn.execute(
            "UPDATE issue SET seed_batch = %s, seed_credit = %s WHERE id = %s",
            (config["batch"], credit(scene), issue_id),
        )
    state[key]["done"] = True
    save_state(state)
    return warnings


def at(days_ago: int, key: str, step: str) -> datetime:
    """A daytime moment in Bogotá, days_ago days before today; stable for the same scene and step."""
    rng = random.Random(f"{key}/{step}")
    day = datetime.now(BOGOTA).date() - timedelta(days=days_ago)
    moment = datetime.combine(day, time(rng.randint(7, 18), rng.randint(0, 59)), BOGOTA)
    return min(moment, datetime.now(BOGOTA) - timedelta(minutes=30)).astimezone(UTC)


def due_date(filed_on: date) -> date:
    co = holidays.Colombia(years={filed_on.year, filed_on.year + 1})
    day, counted = filed_on, 0
    while counted < BUSINESS_DAYS:
        day += timedelta(days=1)
        if day.weekday() < 5 and day not in co:
            counted += 1
    return day


def backdate(conn: psycopg.Connection, scene: dict, issue_id: str) -> None:
    """Spreads the scene over its timeline: the API stamped every step with the current time."""
    key, timeline = scene["key"], scene["timeline"]
    opened = at(timeline["opened"], key, "opened")
    filed = at(timeline["filed"], key, "filed") if "filed" in timeline else None
    replied = at(timeline["response"], key, "response") if "response" in timeline else None
    checked = at(timeline["verified"], key, "verified") if "verified" in timeline else None
    by_status = {
        "REPORTED": opened,
        "FILED": filed,
        "AGENCY_RESPONDED": replied,
        "REPAIR_CLAIMED": replied,
        "VERIFIED": checked,
        "REOPENED": checked,
        "NEEDS_MORE_EVIDENCE": checked,
    }

    conn.execute("UPDATE issue SET opened_at = %s WHERE id = %s", (opened, issue_id))
    conn.execute("UPDATE report SET created_at = %s, updated_at = %s WHERE issue_id = %s", (opened, opened, issue_id))
    conn.execute(
        """
        UPDATE media SET created_at = %s
        WHERE id IN (SELECT photo_media_id FROM report WHERE issue_id = %s)
           OR derived_from IN (SELECT photo_media_id FROM report WHERE issue_id = %s)
        """,
        (opened, issue_id, issue_id),
    )
    for seq, to_status in conn.execute(
        "SELECT seq, to_status FROM status_transition WHERE issue_id = %s", (issue_id,)
    ).fetchall():
        conn.execute(
            "UPDATE status_transition SET at = %s WHERE issue_id = %s AND seq = %s",
            (by_status[to_status], issue_id, seq),
        )

    endorsers = conn.execute(
        "SELECT reporter_id FROM issue_endorsement WHERE issue_id = %s ORDER BY created_at", (issue_id,)
    ).fetchall()
    until = filed or datetime.now(UTC)
    for i, (reporter_id,) in enumerate(endorsers):
        when = opened + (until - opened) * (i + 1) / (len(endorsers) + 1)
        conn.execute(
            "UPDATE issue_endorsement SET created_at = %s WHERE issue_id = %s AND reporter_id = %s",
            (when, issue_id, reporter_id),
        )

    if filed:
        conn.execute(
            "UPDATE petition SET filed_at = %s, due_date = %s WHERE issue_id = %s",
            (filed, due_date(filed.astimezone(BOGOTA).date()), issue_id),
        )
    if replied:
        conn.execute("UPDATE agency_response SET received_at = %s WHERE issue_id = %s", (replied, issue_id))
    if checked:
        conn.execute("UPDATE verification SET created_at = %s WHERE issue_id = %s", (checked, issue_id))
        conn.execute(
            """
            UPDATE media SET created_at = %s
            WHERE id IN (SELECT photo_media_id FROM verification WHERE issue_id = %s)
               OR derived_from IN (SELECT photo_media_id FROM verification WHERE issue_id = %s)
            """,
            (checked, issue_id, issue_id),
        )


def run(config: dict, keys: list[str]) -> None:
    requests.get(f"{API}/actuator/health", timeout=5).raise_for_status()
    sessions = accounts(config)
    state = load_state()
    problems = {}
    for scene in selected(config, keys):
        key = scene["key"]
        if key in state:
            print(f"{key}: {'already seeded' if state[key]['done'] else 'half done, run `reset ' + key + '`'}")
            continue
        print(f"{key} → {scene['target']}")
        try:
            warnings = run_scene(config, scene, sessions, state)
        except SeedError as e:
            problems[key] = [str(e)]
            print(f"  FAILED: {e}")
            continue
        if warnings:
            problems[key] = warnings
            print("  WARNING: " + "; ".join(warnings))
    if problems:
        print("\nTo retry a scene: adjust its photo or texts, `reset KEY` and `run KEY`.")


# --- Purge, reset and export -------------------------------------------------------------------------------------


def purge_sql(batch: str) -> str:
    return (SEED_DIR / "purge.sql").read_text().replace(":'batch'", "'" + batch.replace("'", "''") + "'")


def reset(key: str) -> None:
    state = load_state()
    if key not in state:
        sys.exit(f"{key} has not been seeded")
    batch = f"reset-{key}"
    with db() as conn:
        conn.execute("UPDATE issue SET seed_batch = %s WHERE id = %s", (batch, state[key]["issue"]))
        conn.execute(purge_sql(batch))
    issue_id = state.pop(key)["issue"]
    save_state(state)
    print(f"{key}: Issue {issue_id} deleted locally")


EXPORTED = {
    # table: (selection, excluded columns). Order matters: foreign keys.
    "issue": ("id = ANY(%(issues)s)", {"location"}),
    "status_transition": ("issue_id = ANY(%(issues)s)", {"id"}),
    "report": ("issue_id = ANY(%(issues)s)", set()),
    "issue_endorsement": ("issue_id = ANY(%(issues)s)", set()),
    "petition": ("issue_id = ANY(%(issues)s)", set()),
    "agency_response": ("issue_id = ANY(%(issues)s)", set()),
    "verification": ("issue_id = ANY(%(issues)s)", set()),
}

SEED_MEDIA = """
    WITH RECURSIVE m (id, depth) AS (
        SELECT id, 0 FROM media WHERE id IN (
            SELECT photo_media_id FROM report WHERE issue_id = ANY(%(issues)s)
            UNION SELECT cover_media_id FROM issue WHERE id = ANY(%(issues)s)
            UNION SELECT photo_media_id FROM verification WHERE issue_id = ANY(%(issues)s)
            UNION SELECT public_media_id FROM verification WHERE issue_id = ANY(%(issues)s))
        UNION SELECT media.id, m.depth + 1 FROM media JOIN m ON media.derived_from = m.id
    )
    SELECT to_jsonb(t) FROM media t JOIN (SELECT id, max(depth) AS depth FROM m GROUP BY id) d USING (id)
    ORDER BY d.depth
"""


def export(config: dict) -> None:
    with db() as conn:
        issues = [
            row[0]
            for row in conn.execute("SELECT id FROM issue WHERE seed_batch = %s ORDER BY opened_at", (config["batch"],))
        ]
        if not issues:
            sys.exit(f"No Issues of batch {config['batch']}: run the seed first.")
        params = {"issues": issues}
        # Originals first: derived media point at them.
        media = [row[0] for row in conn.execute(SEED_MEDIA, params).fetchall()]
        lines = [
            f"-- {len(issues)} demo example Issues, batch {config['batch']}. Generated by infra/seed/seed.py.",
            "BEGIN;",
        ]
        lines += [insert(conn, "media", row) for row in media]
        for table, (where, excluded) in EXPORTED.items():
            for row in json_rows(conn, table, where, params):
                lines.append(insert(conn, table, row, excluded))
        lines.append("COMMIT;")

    OUT.mkdir(exist_ok=True)
    (OUT / "seed.sql").write_text("\n".join(lines) + "\n")
    root = media_root()
    with tarfile.open(OUT / "media.tar", "w") as tar:
        for row in media:
            path = root / row["visibility"].lower() / row["id"]
            if not path.exists():
                raise SeedError(f"media file missing: {path}")
            tar.add(path, arcname=f"{row['visibility'].lower()}/{row['id']}")
    print(f"{len(issues)} Issues and {len(media)} media → {OUT / 'seed.sql'}, {OUT / 'media.tar'}")


def json_rows(conn: psycopg.Connection, table: str, where: str, params: dict) -> list[dict]:
    query = f"SELECT to_jsonb(t) FROM {table} t WHERE {where}"
    return [row[0] for row in conn.execute(query, params).fetchall()]


def insert(conn: psycopg.Connection, table: str, row: dict, excluded: set[str] = frozenset()) -> str:
    columns = [c for c in row if c not in excluded]
    values = [sql.Identifier(c) for c in columns]
    if table == "petition":
        # Seed accounts do not exist in production, and production numbers its own radicados.
        values = [
            sql.SQL("NULL")
            if c == "filer_user_id"
            else sql.SQL(
                "'SIM-' || to_char(filed_at AT TIME ZONE 'America/Bogota', 'YYYY') || '-' || "
                "lpad(nextval('petition_seq')::text, 6, '0')"
            )
            if c == "radicado"
            else sql.Identifier(c)
            for c in columns
        ]
    statement = sql.SQL(
        "INSERT INTO {table} ({columns}) SELECT {values} FROM jsonb_populate_record(NULL::{table}, "
        "{row}) ON CONFLICT DO NOTHING;"
    ).format(
        table=sql.Identifier(table),
        columns=sql.SQL(", ").join(map(sql.Identifier, columns)),
        values=sql.SQL(", ").join(values),
        row=sql.Literal(json.dumps(row, ensure_ascii=False)),
    )
    return statement.as_string(conn)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("candidates").add_argument("keys", nargs="*")
    picking = sub.add_parser("pick")
    picking.add_argument("key")
    picking.add_argument("image_id")
    sub.add_parser("run").add_argument("keys", nargs="*")
    sub.add_parser("reset").add_argument("key")
    sub.add_parser("export")
    args = parser.parse_args()

    config = load_scenes()
    try:
        match args.command:
            case "candidates":
                candidates(config, args.keys)
            case "pick":
                pick(config, args.key, args.image_id)
            case "run":
                run(config, args.keys)
            case "reset":
                reset(args.key)
            case "export":
                export(config)
    except SeedError as e:
        sys.exit(str(e))


if __name__ == "__main__":
    main()
