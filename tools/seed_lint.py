#!/usr/bin/env python3
"""Validate the seed food dataset (foods / servings / aliases).

Usage:
    python tools/seed_lint.py --dir tools/seed
    python tools/seed_lint.py --dir tools/seed --release   # 发布前严格模式

Exit code 0 = no errors, 1 = errors found.
"""
from __future__ import annotations

import argparse
import csv
import sys
from collections import Counter, defaultdict
from pathlib import Path

CATEGORIES = {
    "主食", "家常菜", "汤羹", "凉菜", "蛋类",
    "快餐连锁", "饮品", "水果", "坚果零食", "早餐", "调味",
}
BASE_UNITS = {"g", "ml"}
CONFIDENCE = {"high", "medium", "low"}
ALIAS_KINDS = {"COMMON", "DIALECT", "PINYIN", "ABBR", "BRAND", "TYPO"}
SOURCES = {"cn_fct6_ref", "usda_ref", "brand_official", "public_dataset", "estimate"}

KEYS = ["id", "name", "category", "brand", "base_unit", "kcal_100", "protein_100",
        "fat_100", "carb_100", "fiber_100", "sodium_100mg", "source", "source_url",
        "confidence", "verified", "notes"]
SERVING_KEYS = ["id", "food_id", "label", "grams", "is_default", "sort"]
ALIAS_KEYS = ["food_id", "alias", "kind"]


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def err(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)


def read_csv(path: Path, keys: list[str], rep: Report) -> list[dict]:
    if not path.exists():
        rep.err(f"missing file: {path.name}")
        return []
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        missing = [k for k in keys if k not in (reader.fieldnames or [])]
        if missing:
            rep.err(f"{path.name}: missing column(s) {missing}")
            return []
        rows = []
        for lineno, row in enumerate(reader, start=2):
            row["__line__"] = lineno
            rows.append({k: (row.get(k) or "").strip() for k in keys} | {"__line__": lineno})
    return rows


def to_float(value: str) -> float | None:
    if value == "":
        return None
    try:
        return float(value)
    except ValueError:
        return None


def check_food(row: dict, rep: Report) -> None:
    rid = row["id"] or f"line{row['__line__']}"
    where = f"foods.csv:{row['__line__']} [{rid}]"

    if not rid.startswith("F") or not rid[1:].isdigit():
        rep.err(f"{where}: id must look like F001")
    if not row["name"]:
        rep.err(f"{where}: name is required")
    if row["category"] not in CATEGORIES:
        rep.err(f"{where}: category '{row['category']}' not in {sorted(CATEGORIES)}")
    if row["base_unit"] not in BASE_UNITS:
        rep.err(f"{where}: base_unit must be g or ml")
    if row["confidence"] not in CONFIDENCE:
        rep.err(f"{where}: confidence must be one of {sorted(CONFIDENCE)}")
    if row["source"] not in SOURCES:
        rep.err(f"{where}: source '{row['source']}' not in {sorted(SOURCES)}")
    if row["verified"] not in {"0", "1"}:
        rep.err(f"{where}: verified must be 0 or 1")

    kc = to_float(row["kcal_100"])
    p = to_float(row["protein_100"])
    f = to_float(row["fat_100"])
    c = to_float(row["carb_100"])
    if None in (kc, p, f, c):
        rep.err(f"{where}: kcal/protein/fat/carb_100 are required and numeric")
        return
    assert kc is not None and p is not None and f is not None and c is not None

    for name, val in (("kcal_100", kc), ("protein_100", p), ("fat_100", f), ("carb_100", c)):
        if val < 0:
            rep.err(f"{where}: {name} must be >= 0")
    if kc > 900:
        rep.err(f"{where}: kcal_100 {kc} exceeds 900 (pure fat is ~900)")
    for name, val in (("protein_100", p), ("fat_100", f), ("carb_100", c)):
        if val > 100:
            rep.err(f"{where}: {name} {val} exceeds 100g per 100g")
    if p + f + c > 105:
        rep.err(f"{where}: protein+fat+carb = {p + f + c:.1f}g exceeds 105g per 100g")

    calculated = 4 * p + 9 * f + 4 * c
    tolerance = max(20.0, 0.20 * kc)
    if abs(calculated - kc) > tolerance:
        rep.err(
            f"{where}: energy mismatch — 4P+9F+4C = {calculated:.0f} kcal vs stated {kc:.0f} "
            f"(tolerance {tolerance:.0f})"
        )

    fiber = to_float(row["fiber_100"])
    if row["fiber_100"] and fiber is None:
        rep.err(f"{where}: fiber_100 is not numeric")
    if fiber is not None and not 0 <= fiber <= 100:
        rep.err(f"{where}: fiber_100 must be within 0..100")

    sodium = to_float(row["sodium_100mg"])
    if row["sodium_100mg"] and sodium is None:
        rep.err(f"{where}: sodium_100mg is not numeric")

    if (row["brand"] or row["source"] == "brand_official") and not row["source_url"]:
        rep.warn(f"{where}: brand item has no source_url (required before release)")
    if row["source"] == "estimate" and row["verified"] == "1":
        rep.warn(f"{where}: estimate marked verified=1 — confirm this is intentional")


def check_servings(rows: list[dict], food_ids: set[str], rep: Report) -> None:
    per_food: dict[str, list[dict]] = defaultdict(list)
    seen_ids: set[str] = set()
    for row in rows:
        where = f"servings.csv:{row['__line__']} [{row['id']}]"
        if row["id"] in seen_ids:
            rep.err(f"{where}: duplicate serving id")
        seen_ids.add(row["id"])
        if row["food_id"] not in food_ids:
            rep.err(f"{where}: food_id '{row['food_id']}' not found in foods.csv")
            continue
        grams = to_float(row["grams"])
        if grams is None or grams <= 0:
            rep.err(f"{where}: grams must be a positive number")
            continue
        if grams > 3000:
            rep.err(f"{where}: grams {grams} exceeds 3000 (sanity limit)")
        if row["is_default"] not in {"0", "1"}:
            rep.err(f"{where}: is_default must be 0 or 1")
        if not row["label"]:
            rep.err(f"{where}: label is required")
        per_food[row["food_id"]].append(row)

    for food_id, items in per_food.items():
        defaults = [i for i in items if i["is_default"] == "1"]
        if len(defaults) != 1:
            rep.err(f"servings for {food_id}: expected exactly 1 default, found {len(defaults)}")
    for food_id in food_ids:
        if food_id not in per_food:
            rep.err(f"food {food_id}: has no serving row")


def check_aliases(rows: list[dict], foods: dict[str, dict], rep: Report) -> None:
    seen: set[tuple[str, str]] = set()
    for row in rows:
        where = f"aliases.csv:{row['__line__']}"
        food = foods.get(row["food_id"])
        if food is None:
            rep.err(f"{where}: food_id '{row['food_id']}' not found in foods.csv")
            continue
        alias = row["alias"]
        if not alias:
            rep.err(f"{where}: alias is required")
            continue
        if row["kind"] not in ALIAS_KINDS:
            rep.err(f"{where}: kind '{row['kind']}' not in {sorted(ALIAS_KINDS)}")
        if alias == food["name"]:
            rep.err(f"{where}: alias equals canonical name '{alias}'")
        key = (row["food_id"], alias)
        if key in seen:
            rep.err(f"{where}: duplicate alias '{alias}' for {row['food_id']}")
        seen.add(key)


def print_stats(foods: list[dict], servings: list[dict], aliases: list[dict]) -> None:
    print("=" * 62)
    print(f"foods={len(foods)}  servings={len(servings)}  aliases={len(aliases)}")

    cats = Counter(f["category"] for f in foods)
    print("\n[类目覆盖]")
    for cat in sorted(CATEGORIES):
        print(f"  {cat:<6} {cats.get(cat, 0):>3}")

    labels = Counter(s["label"] for s in servings)
    print(f"\n[份量单位] 共 {len(labels)} 种")
    print("  " + "  ".join(f"{k}×{v}" for k, v in labels.most_common()))

    verified = sum(1 for f in foods if f["verified"] == "1")
    print(f"\n[复核状态] verified=1: {verified}/{len(foods)}  "
          f"verified=0: {len(foods) - verified}")

    src = Counter(f["source"] for f in foods)
    print("[来源分布] " + "  ".join(f"{k}={v}" for k, v in src.most_common()))

    brands = [f for f in foods if f["brand"]]
    print(f"[品牌单品] {len(brands)} 条，全部待按官网复核：")
    for f in brands:
        print(f"  - {f['id']} {f['name']}  verified={f['verified']}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="tools/seed", help="directory holding the three CSVs")
    ap.add_argument("--release", action="store_true",
                    help="strict mode: brand_official rows must be verified=1")
    args = ap.parse_args()

    base = Path(args.dir)
    rep = Report()

    foods = read_csv(base / "foods.csv", KEYS, rep)
    servings = read_csv(base / "servings.csv", SERVING_KEYS, rep)
    aliases = read_csv(base / "aliases.csv", ALIAS_KEYS, rep)

    ids: set[str] = set()
    food_by_id: dict[str, dict] = {}
    names: Counter = Counter()
    for row in foods:
        if row["id"] in ids:
            rep.err(f"foods.csv:{row['__line__']}: duplicate id {row['id']}")
        ids.add(row["id"])
        food_by_id[row["id"]] = row
        names[row["name"]] += 1
        check_food(row, rep)
    for name, n in names.items():
        if n > 1:
            rep.err(f"foods.csv: duplicate food name '{name}' ({n} times)")

    if args.release:
        for row in foods:
            where = f"foods.csv:{row['__line__']} [{row['id']}]"
            if row["source"] == "brand_official" and row["verified"] != "1":
                rep.err(f"{where}: brand_official must be verified=1 before release")
            if (row["brand"] or row["source"] == "brand_official") and not row["source_url"]:
                rep.err(f"{where}: brand item requires source_url before release")

    check_servings(servings, ids, rep)
    check_aliases(aliases, food_by_id, rep)

    print_stats(foods, servings, aliases)

    print("\n" + "=" * 62)
    for w in rep.warnings:
        print(f"WARN  {w}")
    for e in rep.errors:
        print(f"ERROR {e}")

    if rep.errors:
        print(f"\nFAILED: {len(rep.errors)} error(s), {len(rep.warnings)} warning(s)")
        return 1
    print(f"\nOK: 0 errors, {len(rep.warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
