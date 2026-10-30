"""Finds real photos of urban damage for the evaluation set, to label them by eye.

    python collect.py commons KEY QUERY    Wikimedia Commons search: thumbnails, author and license in
                                           work/commons/KEY/ and a contact sheet work/sheets/commons-KEY.jpg
    python collect.py add-commons KEY N NAME   copies result N of that search into photos/NAME.jpg
    python collect.py scan [--boxes N]     Mapillary: random small boxes over Bogotá; keeps photos whose detections
                                           include a pothole, and one plain street photo per box (negatives)
    python collect.py sheets               contact sheets of the Mapillary candidates in work/sheets/
    python collect.py around IMAGE_ID      Mapillary photos of the same spot taken on other dates
    python collect.py add IMAGE_ID NAME    copies a Mapillary photo into photos/NAME.jpg (dashcam strip cropped)

Labels live in dataset.json and are written by hand after looking at the photos: a search or a detector only
proposes candidates. dataset.json keeps the source, author and license of every photo.
"""

import argparse
import json
import random
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image, ImageDraw

EVAL = Path(__file__).resolve().parent
ROOT = EVAL.parent
WORK = EVAL / "work"
PHOTOS = EVAL / "photos"
HITS = WORK / "hits.json"

# Urban Bogotá, roughly. Points outside the 20 localities are rejected by the backend anyway.
SOUTH, NORTH, WEST, EAST = 4.50, 4.78, -74.19, -74.03
BOX = 0.0015
DAMAGE_CLASSES = {"object--pothole"}
FIELDS = "id,captured_at,computed_geometry,geometry,is_pano,sequence,creator,detections.value"


def token() -> str:
    for line in (ROOT / ".env").read_text().splitlines():
        if line.startswith("MAPILLARY_TOKEN="):
            return line.split("=", 1)[1].strip().strip("'\"")
    sys.exit("MAPILLARY_TOKEN is missing in .env")


def graph(path: str, **params) -> dict | None:
    r = requests.get(f"https://graph.mapillary.com/{path}", timeout=90, params={"access_token": token(), **params})
    return r.json() if r.ok else None  # never print the URL: it carries the token


def point(image: dict) -> tuple[float, float]:
    lon, lat = (image.get("computed_geometry") or image["geometry"])["coordinates"]
    return lat, lon


def summary(image: dict, values: set[str]) -> dict:
    lat, lon = point(image)
    return {
        "id": image["id"],
        "lat": round(lat, 6),
        "lon": round(lon, 6),
        "capturedAt": datetime.fromtimestamp(image["captured_at"] / 1000, UTC).date().isoformat(),
        "sequence": image.get("sequence"),
        "author": (image.get("creator") or {}).get("username"),
        "damage": sorted(values & DAMAGE_CLASSES),
    }


def box_images(center: tuple[float, float]) -> list[dict]:
    lat, lon = center
    data = graph("images", fields=FIELDS, limit=50, bbox=f"{lon - BOX},{lat - BOX},{lon + BOX},{lat + BOX}")
    found = []
    for image in (data or {}).get("data", []):
        if image.get("is_pano"):
            continue
        values = {d["value"] for d in image.get("detections", {}).get("data", [])}
        found.append(summary(image, values))
    return found


def scan(boxes: int) -> None:
    WORK.mkdir(exist_ok=True)
    known = json.loads(HITS.read_text()) if HITS.exists() else {"damage": [], "plain": []}
    seen = {h["id"] for h in known["damage"] + known["plain"]}
    rng = random.Random()
    centers = [(rng.uniform(SOUTH, NORTH), rng.uniform(WEST, EAST)) for _ in range(boxes)]
    with ThreadPoolExecutor(8) as pool:
        for images in pool.map(box_images, centers):
            fresh = [i for i in images if i["id"] not in seen]
            seen.update(i["id"] for i in fresh)
            known["damage"] += [i for i in fresh if i["damage"]]
            # One photo without any damage class per box: candidates for "not damage" (rejection) items.
            plain = [i for i in fresh if not i["damage"]]
            if plain:
                known["plain"].append(rng.choice(plain))
    HITS.write_text(json.dumps(known, indent=1))
    print(f"{len(known['damage'])} photos with a damage class, {len(known['plain'])} without, in {HITS}")


def thumbnail(image_id: str, size: str = "thumb_1024_url") -> Image.Image | None:
    meta = graph(image_id, fields=size)
    if not meta or size not in meta:
        return None
    r = requests.get(meta[size], timeout=90)
    return Image.open(BytesIO(r.content)).convert("RGB") if r.ok else None


def sheets() -> None:
    """3×3 grids with the image ids written on them: cheaper to review than one photo at a time."""
    known = json.loads(HITS.read_text())
    out = WORK / "sheets"
    out.mkdir(parents=True, exist_ok=True)
    for kind in ("damage", "plain"):
        ids = [h["id"] for h in known[kind]]
        with ThreadPoolExecutor(8) as pool:
            thumbs = list(pool.map(thumbnail, ids))
        cells = [(i, t) for i, t in zip(ids, thumbs, strict=True) if t is not None]
        for n in range(0, len(cells), 9):
            sheet = Image.new("RGB", (3 * 512, 3 * 384), "white")
            draw = ImageDraw.Draw(sheet)
            for k, (image_id, thumb) in enumerate(cells[n : n + 9]):
                x, y = (k % 3) * 512, (k // 3) * 384
                sheet.paste(thumb.resize((512, 384)), (x, y))
                draw.rectangle((x, y, x + 200, y + 22), fill="black")
                draw.text((x + 4, y + 4), image_id, fill="yellow")
            sheet.save(out / f"{kind}-{n // 9:02d}.jpg", quality=80)
    print(f"Contact sheets in {out}")


def around(image_id: str) -> None:
    meta = graph(image_id, fields="computed_geometry,geometry,captured_at")
    if not meta:
        sys.exit(f"{image_id}: not found")
    lat, lon = point(meta)
    d = 0.00012  # ~13 m
    data = graph("images", fields=FIELDS, limit=100, bbox=f"{lon - d},{lat - d},{lon + d},{lat + d}") or {}
    day = datetime.fromtimestamp(meta["captured_at"] / 1000, UTC).date()
    others = {}
    for image in data.get("data", []):
        if image.get("is_pano"):
            continue
        s = summary(image, {x["value"] for x in image.get("detections", {}).get("data", [])})
        if s["capturedAt"] != day.isoformat():
            others.setdefault(s["capturedAt"], s)  # one photo per date
    target = WORK / "around" / image_id
    target.mkdir(parents=True, exist_ok=True)
    for s in others.values():
        thumb = thumbnail(s["id"])
        if thumb:
            thumb.save(target / f"{s['capturedAt']}_{s['id']}.jpg", quality=85)
    (target / "images.json").write_text(json.dumps(list(others.values()), indent=1))
    print(f"{len(others)} other dates around {image_id} ({day}) in {target}")


def add(image_id: str, name: str) -> None:
    image = thumbnail(image_id, "thumb_2048_url")
    if image is None:
        sys.exit(f"{image_id}: no photo")
    w, h = image.size
    image = image.crop((0, 0, w, int(h * 0.955)))  # dashcam hood strip, as in infra/seed
    image.thumbnail((1024, 1024))  # the backend sends at most 1024 px to the models
    PHOTOS.mkdir(exist_ok=True)
    image.save(PHOTOS / f"{name}.jpg", quality=85)
    meta = graph(image_id, fields=FIELDS) or {}
    print(json.dumps(summary(meta, set()) if meta else {"id": image_id}))


COMMONS = "https://commons.wikimedia.org/w/api.php"
# Wikimedia asks for a descriptive User-Agent; without it, requests get throttled or refused.
COMMONS_HEADERS = {"User-Agent": "civic-repairs-eval/0.1 (https://github.com/dvchinx/Meta-Hackathon)"}


def commons_get(**params) -> dict:
    for attempt in range(4):
        r = requests.get(COMMONS, headers=COMMONS_HEADERS, timeout=60, params={"format": "json", **params})
        if r.ok and r.headers.get("content-type", "").startswith("application/json"):
            return r.json()
        time.sleep(5 * (attempt + 1))
    sys.exit(f"Commons kept refusing ({r.status_code})")


def commons(key: str, query: str, limit: int) -> None:
    data = commons_get(
        action="query",
        generator="search",
        gsrsearch=f"{query} filetype:bitmap",
        gsrnamespace=6,
        gsrlimit=limit,
        prop="imageinfo",
        iiprop="url|extmetadata|size",
        iiurlwidth=1280,
    )
    pages = sorted(data.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 0))
    target = WORK / "commons" / key
    target.mkdir(parents=True, exist_ok=True)
    found = []
    for page in pages:
        info = page["imageinfo"][0]
        meta = info.get("extmetadata", {})
        license_name = meta.get("LicenseShortName", {}).get("value", "")
        if not any(ok in license_name for ok in ("CC BY", "CC0", "Public domain")):
            continue
        r = requests.get(info["thumburl"], headers=COMMONS_HEADERS, timeout=60)
        time.sleep(0.5)
        if not r.ok:
            continue
        n = len(found)
        (target / f"{n:02d}.jpg").write_bytes(r.content)
        found.append(
            {
                "n": n,
                "title": page["title"],
                "url": info["descriptionurl"],
                "author": strip_html(meta.get("Artist", {}).get("value", "")),
                "license": license_name,
            }
        )
    (target / "results.json").write_text(json.dumps(found, indent=1, ensure_ascii=False))
    sheet_of([(str(f["n"]), Image.open(target / f"{f['n']:02d}.jpg").convert("RGB")) for f in found], f"commons-{key}")
    print(f"{len(found)} freely licensed photos for {query!r} in {target}")


def strip_html(value: str) -> str:
    import re

    return re.sub(r"<[^>]+>", "", value).strip()


def sheet_of(cells: list[tuple[str, Image.Image]], name: str) -> None:
    out = WORK / "sheets"
    out.mkdir(parents=True, exist_ok=True)
    for n in range(0, len(cells), 12):
        sheet = Image.new("RGB", (4 * 400, 3 * 300), "white")
        draw = ImageDraw.Draw(sheet)
        for k, (label, image) in enumerate(cells[n : n + 12]):
            x, y = (k % 4) * 400, (k // 4) * 300
            sheet.paste(image.resize((400, 300)), (x, y))
            draw.rectangle((x, y, x + 40, y + 22), fill="black")
            draw.text((x + 4, y + 4), label, fill="yellow")
        sheet.save(out / f"{name}-{n // 12}.jpg", quality=80)


def add_commons(key: str, n: int, name: str) -> None:
    results = json.loads((WORK / "commons" / key / "results.json").read_text())
    meta = next(r for r in results if r["n"] == n)
    image = Image.open(WORK / "commons" / key / f"{n:02d}.jpg").convert("RGB")
    image.thumbnail((1024, 1024))  # the backend sends at most 1024 px to the models
    PHOTOS.mkdir(exist_ok=True)
    image.save(PHOTOS / f"{name}.jpg", quality=85)
    print(
        json.dumps(
            {
                "source": "commons",
                "title": meta["title"],
                "url": meta["url"],
                "author": meta["author"],
                "license": meta["license"],
            },
            ensure_ascii=False,
        )
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("commons")
    p.add_argument("key")
    p.add_argument("query")
    p.add_argument("--limit", type=int, default=24)
    p = sub.add_parser("add-commons")
    p.add_argument("key")
    p.add_argument("n", type=int)
    p.add_argument("name")
    p = sub.add_parser("scan")
    p.add_argument("--boxes", type=int, default=400)
    sub.add_parser("sheets")
    p = sub.add_parser("around")
    p.add_argument("image_id")
    p = sub.add_parser("add")
    p.add_argument("image_id")
    p.add_argument("name")
    args = parser.parse_args()
    match args.command:
        case "commons":
            commons(args.key, args.query, args.limit)
        case "add-commons":
            add_commons(args.key, args.n, args.name)
        case "scan":
            scan(args.boxes)
        case "sheets":
            sheets()
        case "around":
            around(args.image_id)
        case "add":
            add(args.image_id, args.name)


if __name__ == "__main__":
    main()
