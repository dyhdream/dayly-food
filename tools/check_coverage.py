"""食材库覆盖度检查：目标清单 vs 现有种子数据。

用法:
    python tools/check_coverage.py            # 报告覆盖情况
    python tools/check_coverage.py --strict   # P0 有缺口时返回非 0（发布前用）

只读，不改数据。匹配按名字完全相等，另外把 aliases.csv 也算作覆盖（"包菜" 命中 "卷心菜"）。
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

SEED = Path(__file__).resolve().parent / "seed"


def read_csv(name: str) -> list[dict]:
    with (SEED / name).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="P0 有缺口时返回非 0")
    args = parser.parse_args()

    targets = read_csv("coverage_targets.csv")
    foods = read_csv("foods.csv")
    aliases = read_csv("aliases.csv")

    by_id = {row["id"]: row for row in foods}
    known = {row["name"] for row in foods}
    known |= {row["alias"] for row in aliases}
    # 名字带括号的（辣椒(鲜)、紫菜(干)）允许只写主名就匹配
    known_loose = {name.split("(")[0] for name in known}

    missing: dict[str, list[dict]] = {}
    for target in targets:
        name = target["name"]
        if name in known or name.split("(")[0] in known_loose:
            continue
        missing.setdefault(target["priority"], []).append(target)

    total = len(targets)
    missing_all = sum(len(rows) for rows in missing.values())
    print(f"[覆盖度] 目标 {total} 项，已覆盖 {total - missing_all}，缺 {missing_all} "
          f"({(total - missing_all) / total * 100:.0f}%)")

    for priority in ("P0", "P1", "P2"):
        rows = missing.get(priority, [])
        if not rows:
            print(f"\n{priority}: 无缺口 ✅")
            continue
        print(f"\n{priority}: 缺 {len(rows)} 项")
        by_category: dict[str, list[str]] = {}
        for row in rows:
            by_category.setdefault(row["category"], []).append(row["name"])
        for category, names in sorted(by_category.items()):
            print(f"  {category}: {'、'.join(names)}")

    if args.strict and missing.get("P0"):
        print(f"\n[失败] P0 仍缺 {len(missing['P0'])} 项", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
