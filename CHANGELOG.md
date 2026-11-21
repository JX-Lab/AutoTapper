# Changelog

## 1.4.0

- Added a versioned action sequence that mixes taps and recorded single-finger gestures while migrating existing points and profiles automatically.
- Added edge-docked recording controls so the full controller does not block targets during capture.
- Added gesture path recording, on-screen trajectory feedback, explicit target-app preview, retry, and keep controls.
- Added gesture editing for per-action delay, repeat count, and duration; the data model already supports multiple strokes for future multi-touch work.
- Refined contributor attribution to distinguish early foundations from the current redesigned interface.

## 1.3.0

- Added System, Light, and Night themes with theme-aware app and overlay icons.
- Added an in-app language selector for System, Simplified Chinese, and English.
- Added edge snapping and right-edge default placement for the floating controller.
- Simplified opening behavior: the main button opens the controller without starting point capture.
- Added separate delete-last and stop/clear-all controls; after stopping, the same button clears all points with one tap.
- The controller now collapses automatically when tapping starts and reuses persistent windows for collapse, expand, and point-picking transitions.
- Removed routine success Toasts during normal controller operations; validation and permission errors still appear.
- Replaced overlay text controls with compact icon buttons and fixed picker-button overflow.
- Permission rows now disappear when access is granted and return only when access is lost.
- Added a concise text-only tutorial from the top-right help button.
- Expanded every numeric click-setting hint with its valid range and the effect of higher or lower values.

## 1.2.0

- Redesigned the app and floating controller around the night-sky orange brand system.
- Added true pause and resume while preserving point, repeat, and round progress.
- Added named profiles for saving, updating, switching, and deleting complete setups.
- Added per-point delay, consecutive tap count, and press duration settings.
- Added unsaved-profile protection and explicit pending states for long-press pause/stop.
- Restricted dynamic control broadcasts to this application.

## 1.1.1

- Redesigned the main screen and floating controller.
- Added controller opacity controls and large-screen layouts.
- Added animated point removal and refreshed visual assets.

## 1.1.0

- Added release update checks and update notifications.
- Added version information and a manual update check.

## 1.0.0

- Added ordered multi-point tapping with finite or infinite rounds.
- Added a draggable floating controller and point list management.
- Added migration from the previous single-point configuration.
