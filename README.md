<p align="center">
  <img src="./assets/readme/hero.svg" width="100%" alt="AutoTapper：Android 自动连点工具，在任意应用里通过悬浮控制器取点，自动按顺序轮流点击多个位置。">
</p>

<p align="center">
  <a href="README_EN.md">English</a> · 中文
</p>

<p align="center">
  <a href="https://github.com/JX-Lab/AutoTapper/actions/workflows/android.yml"><img src="https://github.com/JX-Lab/AutoTapper/actions/workflows/android.yml/badge.svg" alt="Android build"></a>
  <a href="https://github.com/JX-Lab/AutoTapper/releases/latest"><img src="https://img.shields.io/github/v/release/JX-Lab/AutoTapper?display_name=tag&include_prereleases" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0+">
  <a href="https://github.com/JX-Lab/AutoTapper/pulls"><img src="https://img.shields.io/badge/PRs-welcome-FF7A1A" alt="PRs welcome"></a>
</p>

## AutoTapper 是什么

AutoTapper 是一个轻量的 Android 自动点击工具。它通过系统**无障碍服务**执行点击手势，并通过可拖动的**悬浮控制器**在任意应用中完成取点、开始和停止。

它支持多个点位按顺序循环点击，适合重复测试、演示、设备操作辅助等明确允许自动输入的场景。应用不会在启用无障碍服务后自行点击，只有用户主动按下 `GO` 或“开始点击”才会执行。

## 核心功能

- **多点轮流点击**：按保存顺序循环点击，支持固定轮数与无限轮数。
- **可拖动悬浮控制器**：在任意应用中通过 `+`、`DEL`、`GO`、`STOP` 完成操作。
- **点位可视化**：半透明圆点展示每个位置，当前点击点会闪烁高亮。
- **点击节奏控制**：配置基础间隔和随机附加延迟，最低间隔为 100 ms。
- **点位管理与迁移**：主界面逐条查看、删除点位，并自动迁移旧版单点配置。
- **大屏适配与双语界面**：支持普通手机、`w600dp` 大屏布局以及中英文系统语言。
- **更新提醒**：检查本仓库最新 Release，并允许忽略指定版本。

## 兼容性与下载

| 项目 | 要求 |
| --- | --- |
| 最低系统 | Android 7.0（API 24） |
| 目标 SDK | Android 14（API 34） |
| 构建 JDK | JDK 17 |
| 最新构建 | [GitHub Releases](https://github.com/JX-Lab/AutoTapper/releases/latest) |

> 当前 Release 自动化主要提供 Debug APK，适合测试与体验。正式发布签名流程完善前，不应把它视为应用商店级稳定发行版。

## 为什么需要这些权限

| 权限 | 用途 | 何时使用 |
| --- | --- | --- |
| 悬浮窗 | 显示控制器、取点层与点位标记 | 用户打开悬浮控制器后 |
| 无障碍服务 | 在用户保存的坐标执行点击手势 | 用户主动开始点击后 |
| 前台服务 | 让悬浮控制器在切换应用后继续存在 | 控制器运行期间 |
| 网络 | 查询本仓库的 GitHub Release | 启动静默检查或手动检查时 |
| 通知 | 显示控制器状态与更新提醒 | 系统允许通知时 |

AutoTapper 不读取界面文本，不上传点击点位；点位和参数保存在设备本地。请在授予无障碍权限前确认安装包来源可信。

## 快速开始

1. 从 [Releases](https://github.com/JX-Lab/AutoTapper/releases/latest) 下载并安装 APK。
2. 打开应用，依次授予悬浮窗权限并启用 AutoTapper 无障碍服务。
3. 配置基础间隔、随机延迟和点击轮数。
4. 点击“打开悬浮控制器取点”，在目标位置依次轻触，完成后退出取点模式。
5. 展开侧边控制器并点击 `GO`；需要停止时点击 `STOP`。

<p align="center">
  <img src="./assets/readme/workflow.svg" width="100%" alt="AutoTapper 工作流程：取点、配置、开始、循环点击、更新提醒。">
</p>

## 点击逻辑

假设保存了 A、B、C 三个点：

```text
A → 等待 → B → 等待 → C → 完成 1 轮 → A → …
```

| 配置项 | 说明 | 示例 |
| --- | --- | --- |
| 基础间隔 | 两次点击之间至少等待的毫秒数 | `400` |
| 随机附加延迟 | 每次额外随机等待 `0..N` 毫秒 | `80` |
| 点击轮数 | 每个点位各点击一次算一轮；`0` 表示无限 | `10` |

当基础间隔为 `400`、随机延迟为 `80` 时，每次点击后的实际等待时间为 400–480 ms。

## 悬浮控制器

- `+`：进入取点模式并连续添加点位。
- `DEL`：删除最后一个点位。
- `GO`：从第一个点开始执行。
- `STOP`：立即停止后续点击。
- 拖动悬浮球：调整控制器停靠位置。
- 收起控制器：隐藏点位标记，避免遮挡界面。

## 从源码构建

准备 JDK 17 和 Android SDK 34，然后使用仓库自带的 Gradle Wrapper：

```bash
git clone https://github.com/JX-Lab/AutoTapper.git
cd AutoTapper
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

## 项目结构

```text
app/src/main/
├── java/com/example/autotapper/
│   ├── AutoClickService.kt   # 无障碍点击执行器
│   ├── OverlayService.kt     # 悬浮控制器与取点界面
│   ├── MainActivity.kt       # 配置与点位管理
│   ├── AutoTapperConfig.kt   # 本地配置和数据迁移
│   └── UpdateChecker.kt      # GitHub Release 更新检查
└── res/                      # 布局、矢量图标、主题与双语文案
```

## 贡献者

感谢下面的贡献者直接推动了 AutoTapper 的功能和体验升级。贡献归属依据原始 PR 与提交历史记录：

| 贡献者 | 主要贡献 |
| --- | --- |
| [@YangZiyueZY](https://github.com/YangZiyueZY) | 在 [PR #5](https://github.com/JX-Lab/AutoTapper/pull/5) 中提供多点顺序点击、可拖动悬浮控制器、点位列表与迁移、更新检查和发布自动化；同时完成主界面与悬浮窗重构、透明度调节、大屏适配，以及 README 头图和流程图。 |
| [@Rose088211](https://github.com/Rose088211) | 在 [PR #4](https://github.com/JX-Lab/AutoTapper/pull/4) 中提供初始英文界面资源与英文 README，为项目后续的中英双语支持打下基础。 |

如果你的名字或贡献说明需要修正，请提交 Issue 或 PR，我们会按提交历史及时更新。

## 贡献

Bug 报告、文档改进、翻译和功能 PR 都很欢迎。开始前请阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)。简要流程：

1. 搜索或创建 Issue，说明问题、复现步骤或功能边界。
2. 从最新 `main` 创建单一主题分支。
3. 同时维护英文默认资源和简体中文资源。
4. 运行 `./gradlew assembleDebug`，并在设备或模拟器验证主要流程。
5. 提交 PR，写清楚变更原因、测试步骤；UI 改动请附截图。

当前正在讨论的方向可在 [Issues](https://github.com/JX-Lab/AutoTapper/issues) 查看，包括 [#1 定时开始点击](https://github.com/JX-Lab/AutoTapper/issues/1)。

Issue #1 的可行实现边界、时间模型和测试清单见 [定时开始点击设计方案](./docs/issue-1-scheduled-start.md)。

## 安全与使用边界

- 仅在你拥有设备和操作授权、并且目标服务允许自动输入的场景使用。
- 不要用于规避验证码、风控、排队或购买限制，也不要影响其他用户或第三方服务。
- 第一次使用请先在非关键界面验证点位、轮数和停止方式。
- 如果发现权限滥用、意外后台行为或安全问题，请通过 Issue 联系维护者并避免公开敏感细节。

## 已知限制

- 不支持滑动、长按或复杂动作编排。
- 点击坐标不会自动适配目标应用布局变化或屏幕旋转。
- 定时启动功能仍在讨论中，尚未包含电商平台服务器时间同步。

## 更新日志与授权

- 完整更新记录见 [CHANGELOG.md](./CHANGELOG.md)。
- 本仓库当前未包含明确的开源许可证；在许可证确定前，默认保留所有权利。复用或分发代码前请先联系维护者。
