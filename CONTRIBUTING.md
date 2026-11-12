# 为 AutoTapper 贡献代码 / Contributing to AutoTapper

感谢你愿意帮助改进 AutoTapper。提交代码前，请先阅读下面的约定；这能让讨论、审查和合并更顺畅。

Thank you for helping improve AutoTapper. Please follow the workflow below so changes are easy to review and merge.

## 开始之前 / Before you start

- Bug、功能建议和实现讨论请先搜索 [Issues](https://github.com/JX-Lab/AutoTapper/issues)，避免重复。
- 大型功能、权限变化、后台行为或 UI 重构，建议先开 issue 对齐范围。
- 请勿提交用于绕过平台限制、风控、验证码或未经许可自动操作第三方服务的功能。
- Search existing [Issues](https://github.com/JX-Lab/AutoTapper/issues) before opening a new one.
- Discuss large features, permission changes, background behavior, or major UI work in an issue first.
- Do not submit features intended to bypass platform rules, risk controls, CAPTCHAs, or authorization boundaries.

## 开发环境 / Development setup

- JDK 17
- Android SDK 34
- Android 7.0 / API 24 或更高版本的设备或模拟器
- 使用仓库自带的 Gradle Wrapper，不依赖全局 Gradle

```bash
git clone https://github.com/JX-Lab/AutoTapper.git
cd AutoTapper
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

## 分支与提交 / Branches and commits

1. Fork 仓库并从最新 `main` 建立短生命周期分支。
2. 推荐分支名：`feat/scheduled-start`、`fix/overlay-state`、`docs/contributing`。
3. 一个 PR 尽量只解决一个主题；不要混入无关格式化或重构。
4. 提交信息建议使用 `feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`ci:` 前缀。

1. Fork the repository and branch from the latest `main`.
2. Prefer short-lived names such as `feat/scheduled-start` or `fix/overlay-state`.
3. Keep each pull request focused on one topic.
4. Prefer commit prefixes such as `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, and `ci:`.

## 代码要求 / Code requirements

- 保持最低 Android 版本为 API 24，除非 PR 明确说明升级原因。
- UI 文案必须同时维护 `values/strings.xml`（英文默认）和 `values-b+zh+Hans/strings.xml`（简体中文）。
- 新增广播、Service、权限或网络请求时，说明安全边界和生命周期行为。
- 不要在仓库中提交签名文件、密码、令牌、私有代理地址或个人环境配置。
- 图标和插图优先使用可审查的矢量源文件，并说明第三方素材授权。
- Preserve API 24 compatibility unless the PR explains why it must change.
- Update both English and Simplified Chinese string resources for user-facing text.
- Explain security and lifecycle behavior when adding broadcasts, services, permissions, or network access.
- Never commit signing files, passwords, tokens, private proxy URLs, or personal environment settings.

## 提交前检查 / Before opening a PR

```bash
./gradlew assembleDebug
```

- [ ] Debug APK 可以构建。
- [ ] 在至少一台设备或模拟器上验证主要流程。
- [ ] 新旧配置迁移不会丢失用户点位。
- [ ] 英文和简体中文界面均无缺失文案。
- [ ] README、CHANGELOG 或使用说明已按需更新。
- [ ] PR 描述包含变更原因、验证步骤和必要截图。

- [ ] The debug APK builds successfully.
- [ ] The primary flow was tested on at least one device or emulator.
- [ ] Existing saved points survive configuration migrations.
- [ ] English and Simplified Chinese resources are complete.
- [ ] Documentation and changelog entries are updated when needed.
- [ ] The PR explains the motivation, validation steps, and includes screenshots for UI changes.

## Pull Request 审查流程

PR 会先通过 GitHub Actions 构建。维护者将重点检查：功能正确性、无障碍服务边界、前台服务生命周期、权限最小化、配置迁移和双语资源完整性。CI 和审查问题解决后再合并到 `main`。

Pull requests are built by GitHub Actions. Maintainers review correctness, accessibility-service boundaries, foreground-service lifecycle, minimum permissions, configuration migration, and localization completeness before merging.
