# 同类开源产品与数据源调研

> 数据采集方式：GitHub Search API（经镜像访问），字段为 stars / language / license / pushed_at。**未逐个通读源码**，结论按 README 摘要与仓库元数据给出，标注为「待验证」的需落地前自行核对。

## 一、最值得看的三个

### 1. `apoorvdarshan/fud-ai` — 架构最接近本项目

- 490★ · Kotlin · MIT · 最近推送 2026-10
- 定位：free & open-source **AI** calorie & workout tracker，iOS + Android，**privacy-first、local-first、自带 Key（BYOK）**
- 为什么值得读：技术选型（Kotlin/Compose + 本地优先 + BYOK）和目标场景几乎与我们重合，是目前唯一「同路线」的公开实现。重点看它的 AI 调用层怎么组织、图片怎么传、结构化输出怎么解析、离线怎么降级。
- 待验证：国内食物数据覆盖度、是否支持中文菜名与中式份量。

### 2. `Sanotsu/china-food-composition-data` — 国内食物数据的现成线索

- 372★ · Python · **无 License** · 最近推送 2026-09
- 内容：《中国食物成分表标准版（第 6 版）》「能量和食物一般营养成分」部分的表格截图，以及转换成特定格式的 JSON
- 为什么重要：这是「国内食物库」最直接的公开数据来源线索，能省掉大量手工录入
- **风险**：无 License + 原书汇编著作权。营养成分是事实数据不受保护，但**整表汇编与截图**存在侵权风险。合规做法：仅作数值参考、自建条目与描述、不直接分发其 JSON/截图；若确要引入需先做授权评估。

### 3. `simonoppowa/OpenNutriTracker` — 通用类里最成熟的

- 2736★ · Dart（Flutter）· GPL-3.0 · 活跃
- 数据来自 OpenFoodFacts + FoodData Central，隐私优先、本地存储
- 参考价值：记录/目标/图表的产品结构、条码扫描流程、国际化组织方式
- 局限：Flutter 技术栈不可直接复用；OFF 对国内常见菜品与连锁单品覆盖很差，正是我们要补的缺口

## 二、其他相关项目

| 仓库 | 星 | 栈 | 许可 | 备注 |
|---|---|---|---|---|
| `brandonp2412/FitBook` | 176 | Dart | MIT | 离线优先 + 条码 + 宏量，产品完整度不错 |
| `SilverCrocus/OpenMacro` | 5 | Rust | MIT | Android 自适应热量/宏量目标 + AI 拍照记录，思路值得看但很新 |
| `H1an1/health-coach` | 64 | Python | MIT | AI 健康教练 skill，含餐食照片分析，可看提示词组织 |
| `MaharshSuryawala/Food-Image-Recognition` | 135 | Jupyter | MIT | 食物图像识别 + 营养输出，算法侧参考 |
| `jonathanyin12/Food.AI` | 52 | Java | 无 | Android 实时目标检测做热量，老项目但链路完整 |
| `andywu0913/Chinese-Cafeteria-Food-Recognition` | 34 | Python | 无 | 中文食堂菜品检测 + 价格/热量，中式场景 |
| `designproart/awesome-ai-food-scanner` | 25 | — | 无 | AI 食物扫描类产品的架构综述合集 |
| `thansury/CalTrack` | 0 | Kotlin | GPL-3.0 | 中文热量记录 + 本地缓存，小而全 |
| `zhaowenbin135/food-tracker-miniapp` | 1 | HTML | 无 | **通义千问 VL + 百度 API 双引擎**识别，小程序 |
| `ha-zhuzhu/SmartDiet` | 8 | C | 无 | 联网电子秤 + 小程序拍照识别菜品，食堂/外卖单人餐场景 |
| `xinyingstudio/nutrition-advisor` | 0 | Python | MIT | 1152 道高校食堂菜品营养数据集 |
| `leimuwife/Food_calories` | 3 | Java | MIT | 小程序 + SpringBoot3 + MySQL 完整全栈 |

## 三、结论

**值得花时间读源码的只有两个**：

1. `fud-ai` —— 同路线（Kotlin + local-first + BYOK），直接看它的 AI 层与数据层设计，能避免重复踩坑。
2. `OpenNutriTracker` —— 只为看产品结构与交互，技术栈不同，不做移植。

**数据源只有 `china-food-composition-data` 一条线索**，且必须先解决授权问题。这也印证了本项目的核心判断：**国内食物数据没有现成的、可直接商用的开源库，自建种子库是绕不开的成本**，也是护城河所在。

**其余项目不必投入**：多数是课程/毕设级（0–50★），或场景不匹配（食堂识别、纯算法、小程序）。

## 四、与我方方案的差异化落点

调研后更确认了三个差异化方向：

1. **中式份量体系**：碗/盘/份/串/杯 + 个人校准记忆（竞品普遍只有「克」）
2. **提示词工程**：角色 + 输出契约 + 中式烹饪先验 + 反幻觉约束（竞品多为裸 API 调用）
3. **库优先原则**：AI 数值只作旁注，冲突进待复核队列（竞品多为 AI 直接写库，幻觉会污染历史）

## 附：android/skills 官方技能包

`third_party/android-skills`（来自 `github.com/android/skills`，33 个 skill、428 文件 / 4.2MB）已拉取本地，与本项目直接相关的：

| skill | 用途 |
|---|---|
| `camera/camerax` | 拍照/预览、低光、测试、折叠屏、XR |
| `device-ai/ml-kit-genai-prompt-api` | **结构化输出、system instructions、prompt design/optimization** —— 与本项目 AI 管道直接对应 |
| `device-ai/litert/*` | 端侧模型（二期可能的离线识别） |
| `jetpack-compose/jetpack-compose-performance` | 列表与状态性能 |
| `jetpack-compose/theming/styles` | Material 3 主题 |
| `navigation/navigation-3` | 类型安全导航 |
| `testing/testing-setup` | Hilt 测试、截图测试 |
| `build-system/agp/agp-9-upgrade` | AGP 9 / KSP 迁移 |
| `performance/anr-audit-and-fix` | ANR 排查 |
| `system/glance-canonical-layouts` | 桌面小组件（二期） |

该仓库为 Android 官方维护，标记为只读参考，已在 `.gitignore` 中排除，不纳入本项目提交。
