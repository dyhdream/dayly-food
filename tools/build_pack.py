"""Builds a regional dish pack and registers it in data/index.json.

Usage: python tools/build_pack.py hengyang
Source: tools/packs/<id>.csv  (name,kcal100,protein100,fat100,carb100,serving_label,serving_grams)

Packs are estimates, not lab data: rows are written with verified=false and a note saying so, and
the UI shows the pack's own description so nobody mistakes it for official figures.
"""
import csv
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKS = ROOT / "tools" / "packs"
DATA = ROOT / "data"

META = {
    "hengyang": {
        "name": "衡阳家常",
        "desc": "米粉、小炒、腊味这类衡阳日常吃法（社区菜谱推算，非实验室数据）",
        "prefix": "H",
    }
}


def build(pack_id: str, version: int = 1) -> None:
    meta = META[pack_id]
    source_csv = PACKS / f"{pack_id}.csv"
    foods, servings = [], []
    with open(source_csv, encoding="utf-8-sig") as fh:
        rows = [r for r in csv.DictReader(fh) if r.get("name", "").strip()]
    for index, row in enumerate(rows, start=1):
        fid = f"{meta['prefix']}{index:03d}"
        foods.append(
            {
                "id": fid,
                "name": row["name"].strip(),
                "kind": "DISH",
                "basis": "COOKED",
                "category": meta["name"],
                "baseUnit": "g",
                "kcal100": float(row["kcal100"]),
                "protein100": float(row["protein100"]),
                "fat100": float(row["fat100"]),
                "carb100": float(row["carb100"]),
                "source": "community_recipe",
                "confidence": "low",
                "verified": False,
                "notes": "按常见做法估算，仅供记录参考",
                "packId": pack_id,
            }
        )
        servings.append(
            {
                "id": f"{meta['prefix']}S{index:03d}",
                "foodId": fid,
                "label": (row.get("serving_label") or "一份").strip(),
                "grams": float(row.get("serving_grams") or 250),
                "isDefault": True,
                "sort": 0,
            }
        )

    payload = {
        "dataVersion": 1,
        "packId": pack_id,
        "foods": foods,
        "servings": servings,
        "aliases": [],
    }
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    file_name = f"pack-{pack_id}-v{version}.json"
    (DATA / file_name).write_bytes(body)

    index_path = DATA / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    packs = [p for p in index.get("packs", []) if p.get("id") != pack_id]
    packs.append(
        {
            "id": pack_id,
            "name": meta["name"],
            "desc": meta["desc"],
            "version": version,
            "file": file_name,
            "sha256": hashlib.sha256(body).hexdigest(),
            "bytes": len(body),
            "foods": len(foods),
        }
    )
    index["packs"] = packs
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{file_name}: {len(foods)} foods, {len(body)} bytes")
    print(f"index.json packs: {[p['id'] for p in packs]}")


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in META:
        raise SystemExit(f"usage: build_pack.py <{'|'.join(META)}> [version]")
    build(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 1)
