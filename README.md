# AutoTapper 自动连点器

AutoTapper 是一个安卓平台上的自动点击工具，基于无障碍服务（Accessibility Service）实现，支持自定义点击位置与间隔，并具备简洁中文界面和随机延迟功能，旨在模拟“人类点击行为”。

---

## 功能特性

- 支持点击屏幕记录坐标，设置点击位置
- 支持单点循环点击
- 支持“随机延迟”模拟自然点击节奏，防止系统检测
- 无需 root，基于 Accessibility Service 实现
- 支持后台悬浮窗控制
- 中文界面，操作简洁
- 适配 Android 7+

---

## 使用步骤

1. 安装 APK（见下方打包方法）
2. 进入系统设置 → 无障碍服务 → 开启 AutoTapper
3. 打开应用，允许悬浮窗权限
4. 点击“设置坐标”并点击屏幕目标点
5. 设置间隔时间与是否随机延迟
6. 开始点击！

---

## 如何构建（使用 GitHub Actions 自动打包）

本项目使用 GitHub Actions 自动构建 APK。

### 自动打包步骤：

1. 上传完所有项目文件，并确保你有以下内容：
   - `.github/workflows/android.yml`
   - 完整的 `app/` 目录结构（包括 Kotlin 源码、布局 XML 等）

2. 进入你的仓库主页 → 点击上方菜单栏的 **Actions**

3. 找到自动触发的 `Build Android APK` 流程（或手动点击运行）

4. 等待构建完成后，在页面底部找到：
   **Artifacts > AutoTapper-APK**

5. 点击下载即可获得 `app-debug.apk`

---

## 开发者

- 项目整理：君诩（JX-Lab）
- 辅助生成：ChatGPT（OpenAI）

---

## License

本项目为学习与技术交流使用，不建议用于刷量或违反使用协议的行为。
