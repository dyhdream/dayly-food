# 开发者文档

面向要改代码、跑构建或核对数据的人。普通用户请回 [README](../README.md)。

---

## 技术栈

`Kotlin 2.0` · `Jetpack Compose (Material 3)` · `Room 2.6` · `Hilt` · `ML Kit 中文 OCR` · `minSdk 26` / `target 35`

网络请求刻意没有引入 OkHttp / Retrofit，直接用 `HttpURLConnection`——只发一个 JSON POST，不值得多一个依赖。

## 项目结构

```
app/src/main/java/com/daylyfood/app/
├── data/
│   ├── db/          Room 实体、DAO、迁移（v1→v3）
│   ├── ai/          AiClient（唯一出口）、AiSettings、FoodRecognizer
│   ├── ocr/         ML Kit OCR 封装（LabelReader / PhotoLoader）
│   ├── update/      GitHub Releases 检查 + 镜像下载 + SHA-256 校验
│   └── Backup.kt    JSON 导入导出
├── domain/          纯逻辑：BMR/TDEE、标签解析、菜品组合、识别结果守卫
└── ui/              Compose 界面 + ViewModel
tools/
├── seed/            食物库 CSV（六表契约见 seed/schema.md）
├── derive_dish.py   由配方推导菜品数值
├── seed_lint.py     数据校验（发布前必须 0 error）
└── release.ps1      一条命令发版
```

## 数据模型

三层，避免"成品菜"和"食材"混在一张表各算各的：

```
食材 INGREDIENT（每 100g）
   ↓  recipe_items（用量）+ cooking_methods（烹饪方式与吸油）
菜品 DISH（每 100g，由配方推导，不是手填）
   ↓
日记 diary_entry（份量 + 数值快照 + 单位）
```

- 菜品数值是**推导缓存**，由 `tools/derive_dish.py` 重算，lint 检查与配方是否一致
- 包装 / 连锁单品走 `PACKAGED + AS_SOLD`
- 用户现场建的食物是 `CUSTOM`，不参与启库更新

当前规模：406 食物（207 食材 / 164 包装 / 35 菜品）、425 份量、233 别名、35 配方（127 条用量）、8 种烹饪方式。

## 数据来源

`tools/seed/foods.csv` 的 `source` 字段记录每条出处：

| source | 条数 | 含义 |
|---|---|---|
| `cn_fct6_ref` | 190 | 参考《中国食物成分表》第 6 版数值整理（非原始表分发） |
| `estimate` | 140 | 无权威数据的市售成品，取典型值 |
| `derived` | 35 | 由配方 + 烹饪方式推导 |
| `brand_official` | 24 | 连锁品牌官方公布信息 |
| `public_dataset` | 14 | 公开食物数据集 |
| `usda_ref` | 3 | USDA FoodData Central |

`estimate` 条目一律 `verified=0`，界面标「市售典型值」。

## 本地构建

```bash
# JDK 21 + Android SDK 35
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 92 个单元测试
```

> 项目路径**别含中文**。AGP 会在构建和单元测试 classpath 上报错，用纯 ASCII 路径或目录联接（junction / symlink）绕开。

### 在模拟器里跑

1. release 包只打了 arm 架构，**x86_64 模拟器装不上**。调试时临时去掉 `app/build.gradle.kts` 里的 `abiFilters`，debug 包会带上 x86_64（ML Kit 有对应架构库）
2. 拍照依赖虚拟摄像头；识别标签时用「相册图标」选图更稳

## 测试与发版

- 92 个单元测试（Robolectric）：BMR/TDEE 口径、种子导入与中文搜索、菜品推导、营养标签解析（含中文单位 `克/毫克`、全零标签、配料表噪声）、AI 额度与花费、备份编解码、数据库迁移 SQL
- 数据改动后跑 `python tools/seed_lint.py`，发布前必须 0 error
- 发版：

```powershell
./tools/release.ps1 -Version 0.9.1 -Notes "- 这版改了什么"
```

依次：改版本号 → 校验种子数据 → 构建 release 包并跑单测 → 提交推送 → 发 GitHub Release 上传 APK。

## 签名

当前 release 用 debug 证书签名，可侧载，**不能上架**。正式发布前需换自己的 keystore。

## 用到的开源组件

| 组件 | 版本 | 用途 | 许可证 |
|---|---|---|---|
| Kotlin | 2.0.21 | 语言 | Apache-2.0 |
| Jetpack Compose + Material 3 | BOM 2024.10.01 | 全部界面与动效 | Apache-2.0 |
| AndroidX（Core / Lifecycle / Activity） | 1.13.1 / 2.8.7 / 1.9.3 | 基础组件 | Apache-2.0 |
| Room | 2.6.1 | 本地数据库（含 v1→v3 迁移） | Apache-2.0 |
| Hilt (Dagger) | 2.52 | 依赖注入 | Apache-2.0 |
| AndroidX ExifInterface | 1.3.7 | 照片方向纠正 | Apache-2.0 |
| ML Kit Text Recognition v2（中文） | 16.0.1 | 营养成分表 OCR，离线 | Google ML Kit 服务条款（非开源，免费） |
| kotlinx.coroutines | 随 Kotlin | 异步 | Apache-2.0 |
| JUnit 4 | 4.13.2 | 单元测试 | EPL-1.0 |
| Robolectric | 4.13 | JVM 上跑 Android 单测 | MIT |
| AndroidX Test | 1.6.1 | 测试上下文 | Apache-2.0 |

> 合规提示：APK 内暂未内置第三方许可证清单（Apache-2.0 的 NOTICE 归属要求）。个人侧载无碍，上架前需补开源许可页面。

## 文档

- [实施方案](PLAN.md) —— 数据模型、计算口径、AI 管道、里程碑
- [同类产品调研](RESEARCH.md)
- [fud-ai 架构核对](FUDAI-COMPARISON.md) —— 逐文件对照：借什么、重造什么
- [GitHub 连接修复](GITHUB-CONNECTIVITY.md) —— 本机 hosts 被改写的问题与修复

## 许可证

仓库暂未附加开源许可证（默认保留所有权利）。如需引用或二次分发，请先开 issue 说明用途。
