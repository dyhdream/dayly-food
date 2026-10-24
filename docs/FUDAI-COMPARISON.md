# fud-ai 架构核对：能借什么、必须重造什么

> 核对方式：GitHub API 拉取仓库树与关键源文件（`apoorvdarshan/fud-ai` @ 2026-10-21，490★ / Kotlin / MIT）。
> 结论来自源码，不是 README 转述。

## 0. 先纠正一个误判

**fud-ai 已有完整中文界面**：

| 文件 | 大小 |
|---|---|
| `res/values/strings.xml`（英文） | 112,534 B |
| `res/values-zh-rCN/strings.xml`（简中） | 110,153 B |

覆盖率约 98%，共 19 种语言（ar/az/cs/de/es/fr/hi/it/ja/ko/nl/pl/pt-BR/ro/ru/tr/uk/zh-CN…）。

所以「中文用户环境」不能作为差异化叙事——**它做完了**。真正缺的是下面三层，而且是结构性的，不是补数据能解决的。

## 1. 它的数据形状：一切归一化为「克 + 配料」

```kotlin
// models/IngredientPortion.kt
data class IngredientPortion(
    val grams: Double, val calories: Double, val protein: Double,
    val carbs: Double, val fat: Double
) { fun resized(newGrams: Double): IngredientPortion? { … } }
```

```kotlin
// models/FoodSource.kt
enum class FoodSource { SNAP_FOOD, NUTRITION_LABEL, BARCODE, TEXT_INPUT, MANUAL }
```

关键事实：

- 所有营养值最终归一到 **grams**，只支持按克重线性缩放
- 数据来源只有五种：**拍照、营养标签、条码、文字、手动** —— 没有「条目库检索命中」这一种，也没有「联网查询后入库」
- 全仓库 **没有 dish / cuisine 概念**（仅 `RecipeServings.kt` 做菜谱配料缩放）
- 它的食物库是**包装商品库**（条码导向）：`BarcodeLabelMemory.kt`、`IngredientLabelParser.kt`、`FoodProductMetadata.kt`

**对照国内现实**：家常菜、连锁单品、外卖菜品**没有条码**，也没有营养标签，只有「菜名 + 份量」；份量单位是「一碗/一份/半份/大份/一个/一串」。用「克 + 配料」的模型装中式餐饮，要么丢份量语义，要么逼用户自己称重——这正是国内竞品体验差的根源。

## 2. 它的 AI 层：编排做得好，但没有「库优先」

- `services/ai/FoodAnalysisService.kt`（57 KB）、`FoodAnalysis.kt`（31 KB）、`GeminiClient.kt`
- AI 结果以 `SNAP_FOOD` 直接落成 `FoodEntry`

**缺的是治理层**：没有「先查本地库 → 命中则用库值 → AI 值仅作旁注 → 冲突进待复核」的链路。也就是说它的 AI 估算**会直接进历史记录**，模型幻觉会污染数据。这正是我方 PLAN 的核心差异化之一（库优先原则）。

## 3. 它的模型预设：全是国际端点，国内不可直连

```kotlin
QWEN -> "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"   // 新加坡国际站
```

17 个供应商里，唯一沾国内的 Qwen 走的是 **intl 新加坡端点**，中国大陆网络下不可用。没有：百炼（北京 `dashscope.aliyuncs.com`）、智谱 GLM、月之暗面 Kimi、火山方舟豆包。

## 4. 五星借鉴清单（值得直接对标实现）

| 文件 | 借鉴点 |
|---|---|
| `models/AIProvider.kt` | **`ApiFormat` 抽象**（GEMINI / OPENAI_COMPATIBLE / ANTHROPIC / LOCAL）+ vision/text 模型分表 + `supportsCustomModelName` + 请求超时策略（30–600s）+ 每 provider 的 Key 提示文案。这是本项目「供应商抽象」的现成范本，**只把预设表换成国内端点**即可 |
| `data/KeyStore.kt` | BYOK 的加密存储组织方式 |
| `data/PersistedJsonGuard.kt` | 持久化 JSON 的防御式校验（防脏数据/版本漂移） |
| `services/ai/FoodAnalysisService.kt` | 多 provider 请求编排、JSON 解析容错、错误分类的结构（**提示词与契约要重写**） |
| `models/IngredientPortion.kt` | 「未取整基线 + 按量缩放」思路 → 可推广为**按份量单位缩放**（碗/份/串/杯） |

## 5. 明确不借鉴（MVP 范围外）

`exercise/`、`FastingRepository`、`WeeklyChallenge*`、`backup/`（Google Drive 云备份）、`HealthNutritionImport`、`widget/`、`ios/`。

它是个 9000+ 文件、含运动/断食/周挑战/健康数据导入的成熟商业级应用。**照着它的范围做，MVP 永远发不出去**。

## 6. 国内化缺口（可直接转成任务清单）

| 层 | 缺口 | 对应设计 |
|---|---|---|
| 数据模型 | 无菜品/份量单位概念 | 新增 `dish`（菜名/类别/品牌）+ 份量单位表（碗/盘/份/串/杯/个 → 克），`diary_entry` 存份量单位 + 克数双轨 |
| 食物库 | 无中式菜品库、无连锁单品、无外卖菜名 | 自建 400–600 条种子（`docs/PLAN.md` §6），按「每份 + 每 100g」双基准存储 |
| 检索 | 无中文菜名检索 | Room FTS4 + unigram/bigram 索引；别名/错别字/品牌简称/方言叫法 |
| 份量 | 只有克 | 中式餐具单位选择器 + `keyCalibration` 个人校准记忆 |
| 模型接入 | 只有国际端点 | 国内预设：百炼(北京)/智谱/Kimi/豆包 + 自定义 OpenAI 兼容 |
| 提示词 | 无中式烹饪先验 | 用油 8–15g/份、红烧加糖油、油炸吸油 10–15%、勾芡 +5g 碳水/100g、汤面分项、米饭小碗 150g/大碗 250g、奶茶杯型糖度 |
| 数据治理 | AI 值直接入库 | 库优先 + 数值守卫 + `pending_food` 待复核 + `verified=false` |

## 7. 对 `docs/PLAN.md` 的两处修正

1. **删除「中文界面」作为差异点**（竞品已 98% 完成）。差异化收敛为：中式份量体系、库优先治理、中式烹饪先验提示词、国内模型直连。
2. **AI 供应商层直接对标 `AIProvider` 的 `ApiFormat` 结构**，不再从零设计；预设表替换为国内四家 + 自定义。

## 8. 结论

fud-ai 值得读的只有 **AI 供应商抽象层**和**几个防御性工具类**，其余（运动/断食/云备份/国际化）都不属于我们的 MVP。

它的真实短板不是「没有中文」，而是：

> **数据模型是「包装商品 + 克」，而中国饮食是「菜品 + 份量单位」。**
> 这不是加数据能补的，必须换模型。

这也是本项目唯一值得投入的地方。
