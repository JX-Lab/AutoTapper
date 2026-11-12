# Issue #1：定时开始点击的实现方案

关联 Issue：[是否考虑结合时间秒表工具，到设定时间自动点击？](https://github.com/JX-Lab/AutoTapper/issues/1)

## 建议范围

第一版实现“按设备时间定时开始已保存的点击序列”，不内置京东、淘宝、美团、饿了么等平台接口，也不抓取或模拟第三方服务器时间。原因：平台时间接口并非稳定公开 API，抓取方式容易失效，也可能违反服务条款。AutoTapper 只提供通用、用户主动配置的本地定时能力。

## 用户流程

1. 用户提前保存点击点、间隔和轮数。
2. 打开悬浮控制器并确认无障碍服务已启用。
3. 在主界面开启“定时开始”，选择日期和时间。
4. 应用展示目标时间、剩余时间和“取消定时”。
5. 到达目标时刻后再次校验权限、点位和配置；满足条件才发送开始指令。
6. 通知和悬浮控制器明确显示“等待中 / 点击中 / 已取消 / 启动失败”。

## 分阶段实现

### MVP：控制器存活期间定时

只在 `OverlayService` 前台服务运行期间提供定时。这样有持续可见的通知，行为可预测，也避免为了后台唤醒额外申请精确闹钟权限。

- `AutoTapperConfig` 保存 `scheduledStartEpochMs` 和 `scheduleEnabled`。
- `MainActivity` 增加日期、时间选择器，以及启用和取消按钮。
- `OverlayService` 使用 `Handler` 或协程更新倒计时。
- 触发前重新用 `System.currentTimeMillis()` 校准剩余时间，防止用户修改系统时间后误触发。
- 到点后通过应用内显式广播调用现有 `ACTION_START_CLICKING`。
- 控制器关闭、点位清空或无障碍服务关闭时自动取消并提示原因。

### 第二阶段：进程被回收后仍可触发

确有需求时再加入 `AlarmManager`：

- Android 12 及以上先检查 `canScheduleExactAlarms()`，仅在用户主动启用时解释并请求精确闹钟能力。
- 使用不可变、包内、`exported=false` 的 `PendingIntent` 和 `BroadcastReceiver`。
- Receiver 只负责恢复前台控制器并重新校验状态，不直接执行点击。
- 不绕过锁屏，不自动解锁，不长期持有 WakeLock。

## 时间模型

- 存储绝对目标时间：Unix epoch 毫秒。
- UI 倒计时使用 `targetEpochMs - System.currentTimeMillis()`。
- 安排短延迟任务时记录 `SystemClock.elapsedRealtime()`，但触发前必须再比较墙上时钟。
- 系统时区、日期或时间变化后重新计算；目标已过去则取消，不补执行。
- 最小提前量建议 3 秒，避免用户刚保存就意外触发。

## 建议数据结构

```kotlin
data class ScheduledStart(
    val enabled: Boolean,
    val targetEpochMs: Long
)
```

建议新增配置键：

```text
scheduled_start_enabled
scheduled_start_epoch_ms
```

建议新增广播：

```text
ACTION_SCHEDULE_START
ACTION_SCHEDULE_CANCEL
ACTION_SCHEDULE_STATE_CHANGED
```

所有广播都应限制在应用包内；Android 13 以下建议使用签名级内部权限保护动态 Receiver。

## 触发前检查

按以下顺序失败即停止，不要静默降级：

1. 目标时间仍有效。
2. 悬浮窗权限存在。
3. AutoTapper 无障碍服务已启用。
4. 至少有一个有效点位。
5. 间隔和轮数可以解析。
6. 当前没有正在执行的点击任务。

## 测试清单

- 设定未来 5 秒，可以准确进入点击状态。
- 到点前取消，不会触发。
- 修改系统时间、时区和日期后不会提前或补执行。
- 控制器关闭、进程重建、无障碍关闭、点位删除时状态一致。
- 屏幕旋转和 Activity 重建后仍显示同一目标时间。
- Android 7、12、13、14 至少各验证一台设备或模拟器。
- 英文与简体中文文案、通知和无障碍描述完整。

## 不建议实现

- 不内置第三方电商服务器时间抓取或私有接口。
- 不自动解锁屏幕、绕过验证码、风控、排队或购买限制。
- 不承诺毫秒级网络抢购成功率；Android 调度、设备负载和目标应用状态都会造成偏差。
