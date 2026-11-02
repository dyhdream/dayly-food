"""Builds the downloadable data package from the seed CSVs.

    python tools/build_data.py --version 2

Writes:
    data/index.json        small manifest the app polls
    data/data-v2.json      the payload (foods / servings / aliases)

The package is a full snapshot, not a diff: the app replaces rows by id, so shipping everything
keeps the client trivially simple and the payload is only tens of KB.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "tools" / "seed"
OUT = ROOT / "data"

FLOAT_FIELDS = ("kcal_100", "protein_100", "fat_100", "carb_100", "fiber_100", "sodium_100mg")


def read_csv(name: str) -> list[dict]:
    with (SEED / name).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def num(value: str) -> float | None:
    value = (value or "").strip()
    if not value:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def foods() -> list[dict]:
    rows = []
    for row in read_csv("foods.csv"):
        item = {
            "id": row["id"],
            "name": row["name"],
            "kind": row["kind"],
            "basis": row["basis"],
            "category": row["category"],
            "baseUnit": row["base_unit"],
            "source": row["source"],
            "confidence": row["confidence"],
            "verified": row["verified"] == "1",
        }
        if row.get("brand"):
            item["brand"] = row["brand"]
        if row.get("source_url"):
            item["sourceUrl"] = row["source_url"]
        if row.get("notes"):
            item["notes"] = row["notes"]
        for field, key in (
            ("kcal_100", "kcal100"),
            ("protein_100", "protein100"),
            ("fat_100", "fat100"),
            ("carb_100", "carb100"),
            ("fiber_100", "fiber100"),
            ("sodium_100mg", "sodium100"),
        ):
            value = num(row.get(field, ""))
            if value is not None:
                item[key] = value
        for required in ("kcal100", "protein100", "fat100", "carb100"):
            if required not in item:
                raise SystemExit(f"{row['id']} {row['name']}: 缺少 {required}")
        rows.append(item)
    return rows


def servings() -> list[dict]:
    return [
        {
            "id": row["id"],
            "foodId": row["food_id"],
            "label": row["label"],
            "grams": float(row["grams"]),
            "isDefault": row["is_default"] == "1",
            "sort": int(row["sort"] or 0),
        }
        for row in read_csv("servings.csv")
    ]


def aliases() -> list[dict]:
    return [
        {"foodId": row["food_id"], "alias": row["alias"], "kind": row["kind"]}
        for row in read_csv("aliases.csv")
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", type=int, required=True, help="数据版本号，必须比上一次大")
    args = parser.parse_args()

    payload = {
        "dataVersion": args.version,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "foods": foods(),
        "servings": servings(),
        "aliases": aliases(),
    }

    OUT.mkdir(exist_ok=True)
    data_file = OUT / f"data-v{args.version}.json"
    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    data_file.write_text(text, encoding="utf-8")

    digest = hashlib.sha256(data_file.read_bytes()).hexdigest()
    index = {
        "dataVersion": args.version,
        "file": data_file.name,
        "sha256": digest,
        "bytes": data_file.stat().st_size,
        "foods": len(payload["foods"]),
        "servings": len(payload["servings"]),
        "aliases": len(payload["aliases"]),
        "minAppVersion": "0.9.0",
    }
    (OUT / "index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(f"data-v{args.version}.json  {index['bytes'] / 1024:.1f} KB  sha256={digest[:16]}…")
    print(f"  foods={index['foods']} servings={index['servings']} aliases={index['aliases']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
