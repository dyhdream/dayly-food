# 每日食记 · 实施方案

## 1. 目标与范围

做一个**面向国内饮食场景**的安卓饮食记录 App，核心闭环：

```
拍照 / 相册 → 多模态模型识别菜品与份量 → 本地中文食物库匹配
→ 用户确认份量 → 记入当日餐次 → 对标 BMR/TDEE 目标
```

**MVP 范围内**：记录、识图、BMR/TDEE、本地食物库、AI 兜底入库。
**MVP 不做**：账号与云同步、众包社区、运动消耗明细、体重曲线图、AI 饮食建议、iOS。

「每天消耗」的口径 = `BMR × 活动系数（TDEE）` 估算，不做运动条目逐条记录。

## 2. 技术栈

| 项 | 选择 |
|---|---|
| 语言 / SDK | Kotlin 2.x，minSdk 26，compile/target SDK 35 |
| UI | Jetpack Compose + Material 3，单 Activity + Compose Navigation |
| 架构 | 单 module 严格分包，MVVM + UDF |
| DI | Hilt（KSP） |
| 数据库 | Room + FTS4（自建中文 n-gram 索引） |
| 相机 | CameraX 1.4.x |
| 网络 | OkHttp + kotlinx.serialization，统一 OpenAI 兼容协议 |
| Key 存储 | 自封装 AndroidKeystore + AES-GCM（不用已停更的 security-crypto） |
| 图片 | Coil 3；自写压缩（长边 1024 / JPEG q80 / <800KB） |

AI 供应商抽象：直接对标 `fud-ai` 的 `ApiFormat` 结构（`GEMINI` / `OPENAI_COMPATIBLE` / `ANTHROPIC` / `LOCAL`），vision / text 模型分表 + 自定义 BaseUrl。预设表换为国内端点：百炼（北京 `dashscope.aliyuncs.com`）、智谱 GLM、月之暗面 Kimi、火山方舟豆包。详见 [FUDAI-COMPARISON.md](./FUDAI-COMPARISON.md)。

## 3. 数据模型（Room）

| 表 | 关键字段 |
|---|---|
| `food_item` | id, name, nameNorm, brand?, category, **kcal/protein/fat/carb 均按每 100g**, fiber?, sodium?, servingLabel?, servingGrams?, source(SEED/AI_WEB/AI_MODEL/USER), sourceUrl?, confidence, verified, userEdited, seedVersion |
| `food_alias` | foodId, alias, aliasNorm, kind(COMMON/BRAND/DIALECT) |
| `food_fts` | rowid(=foodId), tokens —— `@Fts4` 表，存中文 n-gram 串 |
| `user_profile` | id=1, sex, birthYear, heightCm, weightKg, bodyFatPct?, activityLevel, goalType, targetWeightKg, keyCalibration |
| `diary_entry` | id, epochDay, mealType, foodId?, **nameSnapshot, grams, kcal, protein, fat, carb 快照**, photoPath?, source, confidence |
| `weight_log` | epochDay, weightKg（仅用于目标热量重算，不做图表） |
| `meal_session` | id, epochDay, mealType, createdAt —— 承载 AI 上下文记忆 |
| `pending_food` | id, name, brand?, candidateJson, reason(NOT_IN_DB/LOW_CONFIDENCE/CONFLICT), status |
| `user_correction` | fingerprint(图片 sha256 或文本指纹), correctedFoodId, grams |
| `ai_call_log` | provider, model, latencyMs, tokens?, ok, errorKind（**不存图片与 Key**） |

**种子导入**：不用 `createFromAsset`（要维护 `room_master_table` 的 identity hash，schema 一改就崩）。改为 Room 建表后，首次启动从 `assets/seed/*.csv` 单事务批量插入，用 `seedVersion` 做增量合并，`userEdited=true` 的条目永不覆盖。

## 4. 计算口径

- **BMR**（Mifflin-St Jeor，默认）
  - 男：`10w + 6.25h − 5a + 5`
  - 女：`10w + 6.25h − 5a − 161`
  - 有体脂率时可切 Katch-McArdle：`370 + 21.6 × 去脂体重`
- **TDEE** = BMR × 活动系数（1.2 / 1.375 / 1.55 / 1.725 / 1.9）
- **目标热量**：减脂 −15%~20%，增肌 +10%~15%，维持 0；硬下限 男 ≥1500、女 ≥1200 kcal
- **宏量**：蛋白 1.6 g/kg（减脂）/ 1.4（维持），脂肪 25% kcal，余量给碳水；4/9/4 换算
- **重算时机**：体重变化 ≥1kg 或每周一；历史日记的快照不受影响
- 全部为纯函数，无 Android 依赖，便于单测

## 5. 中文食物搜索（关键难点）

SQLite 的 `unicode61` 分词器把整句中文当一个 token，`MATCH "米饭"` 搜不到「黄焖鸡米饭」，而 Android 8–10 上 FTS5 支持不稳定。方案：**FTS4 + 自建 n-gram 索引串**。

- 建索引：`名称 + 品牌 + 全部别名` 归一化后切 unigram + bigram，空格连接
  `黄焖鸡米饭` → `黄 焖 鸡 米 饭 黄焖 焖鸡 鸡米 米饭 …`
- 查询：同样切分，多词 `MATCH "米饭 AND 鸡"`，再算字符覆盖率
- 排序：名称精确 100 / 别名精确 95 / 品牌+名称 90 / 名称前缀 80 / FTS 命中率×60 / verified 加权，取 Top-5
- FTS 无命中 → `nameNorm LIKE '%q%'` 子串兜底
- 拼音首字母（`hmsj`）列为二期

## 6. 种子库与数据治理

- **规模**：MVP 400–600 条，覆盖主食（米饭/面/馒头/包子/粥/饺/粉）、家常菜、快餐连锁单品（肯德基/麦当劳/华莱士/塔斯汀/蜜雪/瑞幸/星巴克）、饮料（含杯型规格）、水果、坚果零食、油酱。
- **每条必填**：中文名、别名数组、类别、每 100g 能量与 P/F/C、钠、常见份量克数（碗/份/杯/个/串）、来源标注。
- **授权口径**：
  - 营养成分是**事实数据**，不受著作权保护；但《中国食物成分表（第 6 版）》整表汇编受汇编作品保护 → **只参考数值、自建条目，不整表复制**。
  - 品牌单品取品牌官网/包装公示值，标注 `sourceUrl`。
  - MVP **不引入 OpenFoodFacts**（ODbL 的 share-alike 对内置数据库有传染性）。
  - 不抓取薄荷/食物库等竞品私有数据。
- **数值守卫**（种子与 AI 入库共用）：`0 ≤ kcal100g ≤ 900`；`0 ≤ P/F/C ≤ 100`；`P+F+C ≤ 105`；`|4P+9F+4C − kcal| ≤ max(20, 20%)`；不满足即拒收并记录原因。

## 7. AI 子系统

### 7.1 身份与提示词边界（对着开源品的痛点）

System Prompt 固化四段：

1. **身份**：中文营养分析员，只做「图片/文字 → 菜品与营养估算」，不闲聊、不写长解释、不做医疗建议。
2. **输出契约**：只允许 JSON；不输出 markdown / 代码块 / 多余字段。
3. **中式烹饪先验**：家常炒菜用油 8–15g/份；红烧/干锅/糖醋加糖加油；油炸吸油 10–15%；勾芡 +5g 碳水/100g；汤面按「面 + 汤 + 浇头」分项；米饭默认小碗 150g / 饭店大碗 250g；奶茶按杯型与糖度档。
4. **反幻觉**：只描述可见内容；不可见配料必须写进 `uncertainty_notes`；份量必须给 `estimated_grams` 与 `range_grams`；kcal 取 5 的倍数，不假装精确。

温度 0.2。

### 7.2 结构化契约

```json
{
  "dishes": [{
    "name_zh": "黄焖鸡米饭",
    "brand": null,
    "confidence": 0.82,
    "portion": { "description": "一份", "estimated_grams": 550, "range_grams": [400, 700] },
    "nutrition": { "kcal": 780, "protein_g": 38, "fat_g": 26, "carbs_g": 96, "sodium_mg": 1800 },
    "nutrition_basis": "per_serving_estimated",
    "evidence": "快餐一份含米饭约200g、鸡腿肉约150g",
    "uncertainty_notes": ["用油量不可见"],
    "needs_confirmation": ["份量"]
  }],
  "overall_confidence": 0.8,
  "reject_reason": null
}
```

解析容错：剥 markdown 包裹 → 取首个 `{` 到末个 `}` → 反序列化；失败重试 1 次；再失败降级到手动。

### 7.3 三阶段管道 + 记忆

- **Stage A 识别**：压缩图 → 模型 → 候选菜品 + 份量区间
- **Stage B 匹配**：过 `FoodMatcher`；命中则**用本地库数值覆盖 AI 数值**（AI 值仅在差异 >25% 时作旁注）
- **Stage C 联网兜底**（未命中且用户开启）：优先供应商自带联网（DashScope `enable_search`、Kimi `$web_search`、GLM web_search）；可选用户自备搜索 API Key
- **记忆**：
  - `meal_session` 内最近 3 次识别 + 用户修正，注入 system prompt 的 `<context>`
  - `user_correction` 指纹命中直接复用（省一次调用）
  - `keyCalibration`：用户把「一碗」改成 180g 后，同类食物默认份量随之更新

### 7.4 入库治理

Stage C 结果必须过数值守卫 + 用户确认，才以 `source=AI_WEB, verified=false` 写入，并进 `pending_food` 可追溯。

## 8. UI 与交互

屏幕：引导（4 步）/ 首页（环形进度 + 三条宏量 + 餐次列表）/ 拍照预览 / 识别中（分段进度）/ 结果确认 / 本地搜索 / 手动快速录入 / 设置。

关键决策：

- **份量必须用户确认**，不做全自动。份量选择器用**中式餐具单位**：碗/盘/份/个/串/杯/易拉罐 → 克（饮品 1ml≈1g），配滑块微调。
- 置信度分级：≥0.8 一键确认；0.5–0.8 高亮待确认字段；<0.5 强制手动搜索。
- 一图多菜 → 拆多条，可勾选。
- 未命中 → 「库里没有，联网查到 3 个候选」→ 选/确认 → 入库并记录。
- 全部拒绝 → 「名称 + 热量」极速记录，保证永不卡死。
- Key 引导：图文步骤 + 一键复制 BaseUrl + 测试连接按钮。

## 9. 边界与失败模式

| 场景 | 处理 |
|---|---|
| 无 Key / Key 失效 | 转纯本地模式，显式横幅，核心记录不受影响 |
| 无网络 | 跳过 Stage C，仅用本地库 |
| 模型返回非 JSON / 截断 | 容错提取 → 重试 1 次 → 降级手动 |
| 429 / 5xx / 超时 | 指数退避 2 次，仍失败给可重试错误卡 |
| 大图 / OOM | 采样解码 + 长边 1024 压缩 |
| HEIC/HEIF | ImageDecoder（API 28+）+ BitmapFactory 回退 + EXIF 旋转 |
| 权限被拒 | 引导系统设置，不反复弹窗 |
| 跨天 / 补记 | 本地时区 00:00 分界，日历选日期 |
| 并发多次识别 | 串行队列，防乱序覆盖 |
| 用户改过种子条目 | `userEdited=true` 永不覆盖 |
| AI 数值与库冲突 | 库优先，冲突入 `pending_food` |

## 10. 测试与验收

- **单测**：BMR/TDEE/目标热量 20 组（含下限钳制）、宏量分配、`CnTokenizer`、`FoodMatcher` 排序、`CandidateValidator`（12 组脏数据）、JSON 容错、单位换算
- **数据库**：Room migration、种子导入幂等、FTS 同步、30 条真实中文查询命中率断言
- **网络**：MockWebServer 模拟 4 家预设 + 超时/429/5xx/非 JSON/空 dishes
- **UI**：拍照→识别→改份量→入库→汇总；无 Key 路径；搜索→记录

验收标准：

1. 冷启动到首页 ≤1.5s（中端机，500 条种子）
2. 本地搜索 P95 ≤80ms（5000 条）
3. 识图端到端 P50 ≤10s，失败必有明确降级提示
4. 20 顿典型中式餐食首次命中率 ≥70%
5. BMR/TDEE 20 组用例全部一致
6. 无 Key / 无网络下核心记录 100% 可用
7. 30 条中文模糊查询（含错别字、别名、品牌简称）命中率 ≥90%

## 11. 里程碑

| 阶段 | 内容 | 产出 |
|---|---|---|
| M0 | 工程骨架（Gradle/Compose/Hilt/Room 跑通） | 可运行空壳 |
| M1 | 数据模型 + 种子 CSV + 导入器 + 中文搜索 + BMR/TDEE + 记录 CRUD + 首页汇总 | **纯本地可用版** |
| M2 | Key 保管 + 供应商预设 + LlmClient + 提示词与契约 + 拍照压缩 + 确认 UI + 库优先覆盖 | **AI 识图闭环** |
| M3 | 联网兜底 + 数值守卫 + pending 队列 + 上下文记忆 + 修正学习 | **数据可增长闭环** |
| M4 | 降级与错误 UI、性能、数据导出、隐私文案 | 可自用发布 |

## 12. 安装包体量预算（硬约束：APK ≤ 100 MB）

实测（31 条样本建真实 SQLite，含主键与三个索引、VACUUM 后）：**每条食物约 1 KB**，已含份量与别名。

| 库规模 | 未压缩 SQLite | 打包进 APK 后（assets 压缩） |
|---|---|---|
| 500 条 | 0.5 MB | ~0.2–0.3 MB |
| 1,000 条 | 1.0 MB | ~0.4–0.6 MB |
| 10,000 条 | 10 MB | ~4–6 MB |
| 50,000 条 | 50 MB | ~20–30 MB |

**结论：食物库内置打包，不做任何「下载数据库」机制。**

1. **DB 直接进 APK。** 目标 400–600 条只占 0.6 MB，扩到 1 万条也才 10 MB。下载 / 校验 / 版本回滚那一整套代码不值得写。
2. **食物配图不打包。** 这是唯一会炸预算的东西：600 张 × 50 KB ≈ 30 MB。要么不做配图，要么运行时按需下载缓存。
3. **超过 2 万条再考虑「内置基础包 + 可选扩展包」。**

体量大头不在数据，而在：Compose/AndroidX 基线（~8–15 MB）、CameraX、可选端侧模型（LiteRT 类 20–50 MB）、图片资源。

> shortcut: 不设自动体积门禁，靠本表人工核对；接入端侧模型或配图时改成 CI 检查 APK 大小。

## 13. AI 依赖红线与成本策略

### 红线（违反即视为缺陷）

1. **断网 / 无 Key / Key 失效 / 额度耗尽**时，记录、份量、计算、汇总、搜索、历史查看全部照常可用。AI 入口禁用或隐藏，**不得弹阻断式错误**。
2. **首屏与冷启动零网络请求**：不 ping 供应商、不校验 Key。启动路径里没有网络代码。
   **唯一例外：更新检查**——首帧之后延迟 3 秒、异步、静默，失败只写在设置页，不阻塞、不弹窗、不进关键路径。
3. **AI 结果只作候选**，用户确认才入库；库优先原则保证 AI 永远写不坏历史。
4. **每个 AI 入口都有非 AI 等价路径**：拍照 → 手动搜索 / 最近吃过 / 复制昨日同餐。
5. **AI 失败不跳空白**：跳到同一个确认页并带上已有上下文（如用户输入的文字描述），不吞输入。
6. 单次识别**重试上限 1 次**，失败即降级，不连环重试烧钱。

### 成本策略

用户选**档位**，不选模型名（价格常变，写死模型名会腐化）：

| 档位 | 默认取向 | 用途 |
|---|---|---|
| **省钱（默认）** | 视觉模型免费/极低价档 | 单菜识图足够 |
| 平衡 | 低价多模态档 | 多菜、复杂摆盘 |
| 更准 | 旗舰多模态档 | 用户主动选择 |

- 档位 → 模型的映射放**本地配置表**，改档位不动代码逻辑。
- 图片先压缩（长边 ≤1024、目标 <500 KB）再上传：token 成本与像素强相关。
- 上传前算 `sha256`，命中 `user_correction` 直接复用，**不调 API**。
- 只在用户**主动触发**时调用，无后台批量识别。
- 设置页显示：当前档位、模型名、最近一次耗时与估算费用、本月累计调用次数。

### 验收（必须进测试）

- `no_provider` 用例：未配置任何供应商时，跑通「搜索 → 选份量 → 记录 → 查看当日汇总」全流程，0 崩溃、0 阻塞。
- `offline` 用例：断网状态重复上一流程，结果一致（不得因网络不可用而禁用记录按钮）。
