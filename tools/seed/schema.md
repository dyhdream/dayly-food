# 种子食物数据 · 契约与录入规范

> 这是本项目所有营养数据的唯一入口。字段改一次全库重录，所以先定契约、再压样本、最后批量。

## 一、六张表

| 文件 | 作用 |
|---|---|
| `foods.csv` | 食物主表，**营养值统一按每 100g / 100ml**；用 `kind` 区分食材 / 菜品 / 包装品 |
| `servings.csv` | 份量换算表：中式餐具单位 → 克（一种食物可有多个份量） |
| `aliases.csv` | 别名表：常见叫法、错别字、方言、品牌简称（喂给中文检索） |
| `cooking_methods.csv` | 烹饪技法 → 默认油量系数（每 100g 生料） |
| `recipes.csv` | 菜品配方头：生重合计、成品重量、使用的技法 |
| `recipe_items.csv` | 配方明细：食材 + 生重克数 + 用途槽位 |

### kind / basis 是整个模型的地基

| kind | basis | 含义 | 数值来源 |
|---|---|---|---|
| `INGREDIENT` | `RAW` | 基础食材（大米、鸡蛋、食用油），**按生重** | 成分表 |
| `DISH` | `COOKED` | 菜品（番茄炒蛋、红烧肉），**按成品熟重** | **由配方计算，`source=derived`，禁止手填** |
| `PACKAGED` | `AS_SOLD` | 包装品/连锁单品（可乐、肯德基汉堡） | 营养标签 / 品牌官网 |

**铁律：`DISH` 的数值是缓存，不是事实。** 只能由配方算出来，`seed_lint.py` 会核对到 3% 以内；食材数值一改，所有相关菜品必须重算。

## 二、字段契约

### foods.csv

| 列 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | `F001` 递增，**永不复用**（日记快照引用它） |
| `name` | 是 | 规范名，如 `番茄炒蛋`；品牌单品用 `品牌 + 菜名`，如 `肯德基 香辣鸡腿堡` |
| `category` | 是 | 见下方枚举 |
| `brand` | 否 | 仅连锁/包装品填，如 `肯德基` |
| `base_unit` | 是 | `g` 或 `ml`（饮品用 ml） |
| `kcal_100` | 是 | 每 100g/100ml 能量（kcal） |
| `protein_100` `fat_100` `carb_100` | 是 | 每 100g/100ml 三大营养素（g） |
| `fiber_100` | 否 | 膳食纤维（g），未知留空 |
| `sodium_100mg` | 否 | 钠（mg），中式菜品很关键，尽量填 |
| `source` | 是 | 见「来源标注」 |
| `source_url` | 否 | 品牌项**必须**填公示页 URL |
| `confidence` | 是 | `high` / `medium` / `low` |
| `verified` | 是 | `1` 已复核 / `0` 待复核 |
| `notes` | 否 | 口径说明，如「每 100g 由一份 208g 反推」 |

**类目枚举**：`主食` `家常菜` `汤羹` `凉菜` `蛋类` `快餐连锁` `饮品` `水果` `坚果零食` `早餐` `调味`

### servings.csv

| 列 | 说明 |
|---|---|
| `id` | `S001` 递增 |
| `food_id` | 关联 `foods.id` |
| `label` | 中式单位：`一小碗` `一大碗` `一份` `一个` `一根` `一串` `一杯` `一罐` `一盒` `一包` `一笼` `一勺` |
| `grams` | 该份量对应克数（饮品按 1ml≈1g） |
| `is_default` | 每种食物**有且只有一个** `1`，UI 默认选中 |
| `sort` | 展示顺序 |

### aliases.csv

| 列 | 说明 |
|---|---|
| `food_id` | 关联 `foods.id` |
| `alias` | 别名本身 |
| `kind` | `COMMON` 常见叫法 / `DIALECT` 方言 / `PINYIN` 拼音 / `ABBR` 简称 / `BRAND` 品牌名 / `TYPO` 常见错写 |

## 三、来源标注（合规红线）

| `source` | 含义 | 允许用法 |
|---|---|---|
| `cn_fct6_ref` | 参考《中国食物成分表（第 6 版）》**数值** | 只取数值、自建条目与描述。**禁止整表复制或分发原书表格** |
| `usda_ref` | 参考 USDA FoodData Central（公有领域） | 可自由使用，注明来源 |
| `brand_official` | 品牌官网/包装公示值 | 必须填 `source_url`，发布前逐条核对 |
| `public_dataset` | 其他公开数据集 | 需确认 License 允许再分发 |
| `estimate` | 估算 | 仅用于占位，`verified` 必须为 `0` |
| `derived` | 由配方计算得出 | **仅限 `kind=DISH`**，数值不手填，由 `recipes` + `recipe_items` 重算 |

**绝对禁止**：抓取薄荷健康、食物库等竞品私有数据；直接分发 `china-food-composition-data` 的 JSON/截图（无 License + 汇编著作权风险）。

## 四、校验规则（`tools/seed_lint.py` 自动执行）

1. `id` 唯一、命名符合前缀；`servings.food_id` / `aliases.food_id` 必须存在
2. `category` / `base_unit` / `kind` / `confidence` 必须在枚举内
3. 每 100g：`0 ≤ kcal ≤ 900`；`0 ≤ P/F/C ≤ 100`；`P+F+C ≤ 105`
4. 能量一致性：`|4P + 9F + 4C − kcal| ≤ max(20, 20% × kcal)`
5. 每种食物至少 1 个份量，且 `is_default=1` 恰好 1 个；`0 < grams ≤ 3000`
6. 品牌项（`brand` 非空）必须 `source_url` 非空
7. `brand_official` 来源必须 `verified=1` 才允许进入发布种子包
8. 别名不得等于规范名、同一食物下不得重复
9. `kind` 与 `basis` 必须配对（`INGREDIENT/RAW`、`DISH/COOKED`、`PACKAGED/AS_SOLD`）
10. `kind=DISH` 必须有且只有一个配方；`source` 必须是 `derived`；反之亦然
11. 配方校验：`Σ items.grams == raw_total_g`（±1g）；`cooked_total_g` 在 `0.5x ~ 12x` 生重之间
12. 配方算出的每 100g 四项数值，必须与 `foods.csv` 该菜品一致（±2 或 3%）
13. 配方项不允许引用 `DISH`（不支持嵌套），`ref_kind` 只能是 `FOOD`

## 五、扩展流程

```
1. 在 Excel/文本里按列录入 → 存 UTF-8 CSV
2. python tools/seed_lint.py --dir tools/seed     # 必须 0 error
3. 抽 10% 人工复核营养值（品牌项 100% 复核）
4. 提升 seed_version → App 首启增量导入（只插新条目，userEdited 的永不覆盖）
```

**当前阶段**：`verified=0` 的条目可以入库参与开发与测试，但**发布版种子包只打包 `verified=1`**。

## 六、样本集说明

当前 `foods.csv` 的 31 条是**样本集**，目的不是覆盖，而是压 schema：

- 覆盖全部 11 个类目
- 覆盖 12 种中式份量单位（碗/份/个/根/笼/罐/盒/包/杯/串/勺/大杯）
- 刻意包含三类难例：**品牌单品**（只有「每个」没有每 100g）、**汤面一体**（兰州牛肉面，汤与面不可分）、**一菜多份量**（红烧肉：一份/一块）

样本通过后，再按类目分批扩到 400–600 条。

## 七、配方模型（v2）

```
食材(INGREDIENT, 生重) ──┐
                         ├─► recipe_items ─► recipes ─► 菜品(DISH, 熟重) ─► diary_entry
技法(cooking_methods) ───┘
```

**计算规则**（`seed_lint.py` 已实现）：

1. 总热量 = Σ(食材每 100g 值 × 生重 ÷ 100)
2. 每 100g 成品 = 总热量 ÷ `cooked_total_g` × 100
3. `Σ recipe_items.grams` 必须等于 `recipes.raw_total_g`（±1g）
4. 算出的 4 个数值必须与 `foods.csv` 中该菜品一致（±2 或 3%）

**三个刻意保留的限制**：

- **不支持嵌套**：配方项只能是 `INGREDIENT`。盖饭/套餐让用户记两条，避免循环检测与递归计算。
- **技法只提供默认值**：`oil_g_per_100g_raw` 用于 UI 预填；配方里仍显式写油克数（同一道菜人人口味不同，不能被系数锁死）。
- **成品重量必须填**：收汁、吸水、油炸失水都会改变密度，没有它算不出每 100g。

**生熟重示例**（最容易出错的地方）：

| 菜品 | 生料 | 成品 | 说明 |
|---|---|---|---|
| 米饭 | 大米 100g | 250g | 吸水 2.5 倍 |
| 白粥 | 大米 50g | 500g | 吸水 10 倍（上限放到 12 倍） |
| 红烧肉 | 345g | 280g | 收汁失重 19% |

## 八、运行时自建食物（不进 CSV）

用户在 App 里手动添加的食物存在本地库，**不写回种子 CSV**：

| 字段 | 取值 |
|---|---|
| kind | CUSTOM |
| asis | AS_SOLD |
| category | 自定义 |
| source | local |
| confidence | low，erified=false |

数值按「这一份多少克、多少 kcal」录入，落库时换算成每 100g（×100/克数），并自动生成一条默认份量。
搜索排序把 CUSTOM 排在最前，用户自建的东西永远先出现。因为复用现有列，**不需要数据库迁移**。
