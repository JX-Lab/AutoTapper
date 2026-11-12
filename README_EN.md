<p align="center">
  <img src="./assets/readme/hero.svg" width="100%" alt="AutoTapper, an Android auto-tapping tool that records points with a floating controller and taps multiple positions in sequence.">
</p>

English | [中文](README.md)

## What is AutoTapper?

AutoTapper is an Android auto-tapping tool. It performs tap gestures through an accessibility service and uses a floating controller to record points, start tapping, and stop tapping in any app.

Unlike single-point auto clickers, AutoTapper can tap multiple positions in sequence for workflows that repeat across several parts of the screen.

## Features

- **Ordered multi-point tapping:** cycles through saved points and highlights the active point.
- **Floating controller:** a draggable side handle with `+`, `DEL`, `GO`, and `STOP` actions.
- **On-screen point markers:** translucent orange markers show saved positions.
- **Flexible timing:** base interval, random extra delay, and finite or infinite rounds.
- **Update checks:** checks the latest GitHub release and displays an update alert.
- **Point management:** view and delete individual points; previous single-point data is migrated automatically.

## Quick start

1. Download and install the APK from [Releases](https://github.com/JX-Lab/AutoTapper/releases/latest).
2. Open the app and grant overlay and accessibility permissions.
3. Select **Open Floating Controller**, tap the screen to add points, and finish point selection.
4. Expand the side controller and press `GO`. Press `STOP` when you are done.

<p align="center">
  <img src="./assets/readme/workflow.svg" width="100%" alt="AutoTapper workflow: select points, configure timing, start, tap in sequence, and check for updates.">
</p>

## How it works

1. **Select points:** each screen tap is saved as one tap position.
2. **Configure:** choose a base interval, optional random extra delay, and the number of rounds. A value of `0` runs until stopped.
3. **Start:** press `GO`; the accessibility service sends a tap gesture to each saved point in order.
4. **Repeat:** the sequence continues and the active point pulses on screen.
5. **Update:** the app checks the latest GitHub release and shows an alert when a newer version is available.

### Tap settings

| Setting | Meaning | Recommendation |
| --- | --- | --- |
| Base interval | Minimum milliseconds between taps | Start with 300–800; values below 100 are raised to 100 |
| Random extra delay | Random wait from 0 to this value after the base interval | Use 0 for a fixed rhythm |
| Rounds | Complete passes through every saved point | 1 taps each point once; 0 runs indefinitely |

## Floating controller

The handle starts at the edge of the screen and can be dragged.

- `+`: add one or more tap points
- `DEL`: remove the last point
- `GO`: start tapping
- `STOP`: stop tapping

Point markers are visible while the controller is expanded. The active marker pulses during tapping, and all markers are hidden when the controller is collapsed.

## Safety notes

- Enabling accessibility does not start tapping. AutoTapper only acts after you press `GO` or **Start Tapping**.
- Use it only in situations where automated input is allowed and understood by everyone affected.
- Test settings on a non-critical screen before using them in a real workflow.

## Known limitations

- Swipe gestures are not supported.
- Complex action sequences are not supported.

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for the complete release history.
