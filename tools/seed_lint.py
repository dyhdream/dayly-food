#!/usr/bin/env python3
"""Validate the seed dataset: foods / servings / aliases / recipes / recipe_items / cooking_methods.

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
KINDS = {"INGREDIENT", "DISH", "PACKAGED"}
BASES = {"RAW", "COOKED", "AS_SOLD"}
CONFIDENCE = {"high", "medium", "low"}
ALIAS_KINDS = {"COMMON", "DIALECT", "PINYIN", "ABBR", "BRAND", "TYPO"}
SOURCES = {"cn_fct6_ref", "usda_ref", "brand_official", "public_dataset", "estimate", "derived"}

KEYS = ["id", "name", "kind", "basis", "category", "brand", "base_unit", "kcal_100",
        "protein_100", "fat_100", "carb_100", "fiber_100", "sodium_100mg",
        "source", "source_url", "confidence", "verified", "notes"]
SERVING_KEYS = ["id", "food_id", "label", "grams", "is_default", "sort"]
ALIAS_KEYS = ["food_id", "alias", "kind"]
RECIPE_KEYS = ["id", "food_id", "cooking_method_id", "raw_total_g", "cooked_total_g", "notes"]
ITEM_KEYS = ["id", "recipe_id", "ref_kind", "ref_id", "grams", "slot"]
METHOD_KEYS = ["id", "name", "oil_g_per_100g_raw", "notes"]

# DISH values are derived; allow small drift for rounding in the CSV.
DISH_TOLERANCE_ABS = 2.0
DISH_TOLERANCE_REL = 0.03


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
    if row["kind"] not in KINDS:
        rep.err(f"{where}: kind must be one of {sorted(KINDS)}")
    if row["basis"] not in BASES:
        rep.err(f"{where}: basis must be one of {sorted(BASES)}")
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

    # kind drives basis: ingredients are raw, packaged goods are as sold.
    if row["kind"] == "INGREDIENT" and row["basis"] != "RAW":
        rep.err(f"{where}: INGREDIENT must have basis=RAW")
    if row["kind"] == "DISH" and row["basis"] != "COOKED":
        rep.err(f"{where}: DISH must have basis=COOKED")
    if row["kind"] == "PACKAGED" and row["basis"] != "AS_SOLD":
        rep.err(f"{where}: PACKAGED must have basis=AS_SOLD")
    if row["kind"] == "DISH" and row["source"] != "derived":
        rep.err(f"{where}: DISH values are computed, so source must be 'derived'")
    if row["kind"] != "DISH" and row["source"] == "derived":
        rep.err(f"{where}: only DISH may use source=derived")

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


def check_recipes(base: Path, foods: dict[str, dict], rep: Report) -> None:
    """DISH rows carry cached values; verify they can be reproduced from the recipe."""
    methods = read_csv(base / "cooking_methods.csv", METHOD_KEYS, rep)
    recipes = read_csv(base / "recipes.csv", RECIPE_KEYS, rep)
    items = read_csv(base / "recipe_items.csv", ITEM_KEYS, rep)

    method_ids = {m["id"] for m in methods}
    for m in methods:
        if to_float(m["oil_g_per_100g_raw"]) is None:
            rep.err(f"cooking_methods.csv:{m['__line__']} [{m['id']}]: oil coefficient must be numeric")

    by_recipe: dict[str, list[dict]] = defaultdict(list)
    for it in items:
        by_recipe[it["recipe_id"]].append(it)

    dish_ids = {fid for fid, row in foods.items() if row["kind"] == "DISH"}
    covered: set[str] = set()

    for r in recipes:
        where = f"recipes.csv:{r['__line__']} [{r['id']}]"
        food = foods.get(r["food_id"])
        if food is None:
            rep.err(f"{where}: food_id '{r['food_id']}' not found in foods.csv")
            continue
        if food["kind"] != "DISH":
            rep.err(f"{where}: food_id {r['food_id']} must have kind=DISH")
        if r["food_id"] in covered:
            rep.err(f"{where}: dish {r['food_id']} already has a recipe")
        covered.add(r["food_id"])
        if r["cooking_method_id"] not in method_ids:
            rep.err(f"{where}: cooking_method_id '{r['cooking_method_id']}' not found")

        raw_total = to_float(r["raw_total_g"])
        cooked_total = to_float(r["cooked_total_g"])
        if raw_total is None or raw_total <= 0:
            rep.err(f"{where}: raw_total_g must be positive")
            continue
        if cooked_total is None or cooked_total <= 0:
            rep.err(f"{where}: cooked_total_g must be positive")
            continue
        # Congee legitimately takes ~10x its rice weight in water; braised dishes lose mass.
        if cooked_total > raw_total * 12:
            rep.err(f"{where}: cooked_total_g {cooked_total} > 12x raw {raw_total} (water uptake sanity)")
        if cooked_total < raw_total * 0.5:
            rep.err(f"{where}: cooked_total_g {cooked_total} < 0.5x raw {raw_total} (mass loss sanity)")

        its = by_recipe.get(r["id"], [])
        if not its:
            rep.err(f"{where}: recipe has no items")
            continue

        summed = 0.0
        totals = {"kcal": 0.0, "protein": 0.0, "fat": 0.0, "carb": 0.0}
        for it in its:
            iwhere = f"recipe_items.csv:{it['__line__']} [{it['id']}]"
            if it["ref_kind"] != "FOOD":
                rep.err(f"{iwhere}: ref_kind must be FOOD (nesting is intentionally unsupported)")
                continue
            ref = foods.get(it["ref_id"])
            if ref is None:
                rep.err(f"{iwhere}: ref_id '{it['ref_id']}' not found in foods.csv")
                continue
            if ref["kind"] == "DISH":
                rep.err(f"{iwhere}: dish '{ref['name']}' cannot be used as an ingredient")
                continue
            grams = to_float(it["grams"])
            if grams is None or grams <= 0:
                rep.err(f"{iwhere}: grams must be positive")
                continue
            summed += grams
            factor = grams / 100.0
            totals["kcal"] += float(ref["kcal_100"]) * factor
            totals["protein"] += float(ref["protein_100"]) * factor
            totals["fat"] += float(ref["fat_100"]) * factor
            totals["carb"] += float(ref["carb_100"]) * factor
            if it["slot"] not in {"MAIN", "SEASONING", "OIL", "SALT"}:
                rep.err(f"{iwhere}: slot must be MAIN/SEASONING/OIL/SALT")

        if abs(summed - raw_total) > 1.0:
            rep.err(f"{where}: items sum to {summed:.1f}g but raw_total_g is {raw_total:.1f}g")

        # Derived per-100g must match what foods.csv claims.
        expected = {
            "kcal_100": totals["kcal"] / cooked_total * 100.0,
            "protein_100": totals["protein"] / cooked_total * 100.0,
            "fat_100": totals["fat"] / cooked_total * 100.0,
            "carb_100": totals["carb"] / cooked_total * 100.0,
        }
        for col, want in expected.items():
            have = float(food[col])
            tol = max(DISH_TOLERANCE_ABS, DISH_TOLERANCE_REL * abs(want))
            if abs(have - want) > tol:
                rep.err(
                    f"{where}: {food['id']}.{col} is {have:.2f} but recipe computes {want:.2f} "
                    f"(tolerance {tol:.2f})"
                )

    for dish in dish_ids - covered:
        rep.err(f"food {dish} ({foods[dish]['name']}): DISH has no recipe")

    print(f"\n[配方] recipes={len(recipes)}  items={len(items)}  methods={len(methods)}")
    if recipes:
        print("  " + "  ".join(f"{r['id']}→{r['food_id']}" for r in recipes))


def print_stats(foods: list[dict], servings: list[dict], aliases: list[dict]) -> None:
    print("=" * 62)
    print(f"foods={len(foods)}  servings={len(servings)}  aliases={len(aliases)}")

    kinds = Counter(f["kind"] for f in foods)
    print("\n[条目类型] " + "  ".join(f"{k}={v}" for k, v in kinds.most_common()))

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
    if brands:
        print(f"[品牌单品] {len(brands)} 条，全部待按官网复核：")
        for f in brands:
            print(f"  - {f['id']} {f['name']}  verified={f['verified']}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="tools/seed", help="directory holding the CSVs")
    ap.add_argument("--release", action="store_true",
                    help="strict mode: brand_official rows must be verified=1 with a source_url")
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
        if row["id"] and row["id"] in ids:
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
    check_recipes(base, food_by_id, rep)

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
