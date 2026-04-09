# AutoTapper

AutoTapper 是一个最小可用的 Android 自动连点器，基于无障碍服务执行点击手势，配合悬浮窗完成屏幕坐标取点。

## 当前能力

- 支持悬浮窗取点，直接记录屏幕坐标
- 支持基础点击间隔与随机附加延迟
- 支持固定次数点击，也支持填 `0` 后无限循环
- 启用无障碍服务后不会自动开始点击，必须手动点击“开始点击”
- 适配 Android 7.0 及以上

## 使用方式

1. 安装 APK 并打开应用。
2. 点击“授予悬浮窗权限”。
3. 点击“开启无障碍服务”，在系统设置里启用 AutoTapper。
4. 回到应用后点击“选择点击坐标”，再轻触一次目标位置。
5. 设置基础间隔、随机附加延迟、重复次数。
6. 点击“开始点击”执行连点，点击“停止点击”随时停止。

## 本地构建

前提：

- Android Studio Hedgehog 或更新版本
- JDK 17
- 已安装 Android SDK Platform 34 与 Build-Tools

命令：

```bash
./gradlew assembleDebug
```

生成文件位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions 自动打包

仓库已包含 `.github/workflows/android.yml`，推送到 `main` 分支后会自动构建 Debug APK，也可以在 GitHub 的 `Actions` 页面手动运行。

构建完成后，在对应 workflow 的 `Artifacts` 中下载 `AutoTapper-debug-apk` 即可。

同时，workflow 也会自动刷新一个固定的 GitHub Release：

- Tag: `debug-latest`
- Asset: `AutoTapper-debug.apk`

## 注意事项

- 本项目依赖 Android 无障碍能力，请仅在你明确知情和允许的场景下使用。
- 不建议用于违反平台协议、刷量或规避风控的行为。
