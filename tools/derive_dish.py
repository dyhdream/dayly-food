#!/usr/bin/env python3
"""Recompute every DISH row from its recipe.

DISH values are a cache, never a fact: they must be reproducible from
recipes.csv + recipe_items.csv. This script is the only thing allowed to write them.

Usage:
    python tools/derive_dish.py --dir tools/seed           # rewrite DISH rows
    python tools/derive_dish.py --dir tools/seed --check   # exit 1 if anything drifted
"""
from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path

NUTRIENTS = ["kcal_100", "protein_100", "fat_100", "carb_100", "fiber_100", "sodium_100mg"]


def read_csv(path: Path) -> tuple[list[str], list[dict]]:
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        rows = [{k: (v or "").strip() for k, v in row.items()} for row in reader]
        return list(reader.fieldnames or []), rows


def write_csv(path: Path, fields: list[str], rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fields, lineterminator="\r\n")
        writer.writeheader()
        writer.writerows(rows)


def fmt(value: float) -> str:
    text = f"{value:.2f}"
    text = text.rstrip("0").rstrip(".")
    return text if text else "0"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="tools/seed")
    ap.add_argument("--check", action="store_true", help="report drift without writing")
    args = ap.parse_args()

    base = Path(args.dir)
    food_fields, foods = read_csv(base / "foods.csv")
    _, recipes = read_csv(base / "recipes.csv")
    _, items = read_csv(base / "recipe_items.csv")

    by_id = {f["id"]: f for f in foods}
    grouped: dict[str, list[dict]] = defaultdict(list)
    for item in items:
        grouped[item["recipe_id"]].append(item)

    drifted = 0
    touched = 0
    for recipe in recipes:
        dish = by_id.get(recipe["food_id"])
        if dish is None:
            print(f"skip {recipe['id']}: food {recipe['food_id']} missing")
            continue
        cooked = float(recipe["cooked_total_g"])
        totals = dict.fromkeys(NUTRIENTS, 0.0)
        for item in grouped.get(recipe["id"], []):
            source = by_id.get(item["ref_id"])
            if source is None:
                continue
            factor = float(item["grams"]) / 100.0
            for nutrient in NUTRIENTS:
                raw = source.get(nutrient) or "0"
                totals[nutrient] += float(raw) * factor

        for nutrient in NUTRIENTS:
            expected = fmt(totals[nutrient] / cooked * 100.0)
            if dish[nutrient] != expected:
                if args.check:
                    print(f"DRIFT {dish['id']} {dish['name']} {nutrient}: {dish[nutrient]} -> {expected}")
                dish[nutrient] = expected
                drifted += 1
        touched += 1
        print(
            f"{recipe['id']} {dish['id']:<5} {dish['name']:<10} "
            f"kcal={dish['kcal_100']}/100g  P={dish['protein_100']} F={dish['fat_100']} C={dish['carb_100']}"
        )

    print(f"\nrecipes={touched} fields_changed={drifted}")
    if args.check:
        return 1 if drifted else 0
    write_csv(base / "foods.csv", food_fields, foods)
    print("foods.csv written")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
