# AutoTapper

English | [中文](README.md)

AutoTapper is an Android auto-tapping tool.

It performs tap gestures via accessibility services, using a floating controller to select, start, and stop taps in any application.

## What it's suitable for

- Continuous tapping at a fixed location

- Quickly start or stop auto-tapping in any application

- Temporarily readjust tap locations

- Basic auto-tapping with simple configuration

## Currently supported

- Single-point continuous tapping

- Floating controller permanently displayed on the side

- `+` Add or reselect tap point

- `DEL` Delete the current tap point

- `GO` Start tapping

- `STOP` Stop tapping

- Semi-transparent orange tap marker

- Basic interval

- Randomly added delay

- Fixed number of taps / Unlimited number of taps

## Currently not supported

- Multi-point alternating tapping

- Swipe gestures

- Complex motion programming

## First-time use

1. Install and open AutoTapper.

2. Click "Grant floating window permission".

3. Click “Enable Accessibility Service” and enable AutoTapper in system settings.

4. Return to the app and configure click settings.

5. Click “Open Floating Controller”.

If there is no click point yet, the app will directly enter click mode.

## Basic Usage Flow

1. Open the floating controller.

2. Click `+`.

3. Tap once at the target location to record the click point.

4. A semi-transparent orange dot will appear on the screen, indicating the current click location.

5. Enter the app you actually need to interact with.

6. Expand the side floating controller.

7. Click `GO` to start automatic clicking.

8. To stop, click `STOP`.

## Floating Controller Explanation

The floating controller is stored on the side of the screen by default.

- `+`: Add or reselect a click point

- `DEL`: Delete the current click point

- `GO`: Start automatic clicking

- `STOP`: Stop automatic clicking

When the controller is expanded:

- If a click point has been set, a semi-transparent orange dot will be displayed.

- This dot will blink while clicking is in progress.

When the controller is collapsed:

- The dot marker will automatically hide.

- It will not continuously obscure screen content.

## Click Configuration Instructions

### Basic Interval

Indicates the minimum waiting time between two clicks, in milliseconds.

- `1000` milliseconds = `1` second

- It is recommended to try values between `300` and `800` first.

- If less than `100` is entered, it will be processed as `100` milliseconds.

### Random Additional Delay

Indicates an additional random delay beyond the basic interval.

For example:

- Base interval = `400`

- Random additional delay = `80`

Then the actual waiting time after each click will vary between `400` and `480` milliseconds.

If you enter `0`, it means a fixed clicking rhythm with no random variation.

### Number of Clicks

- Enter `1`: Click only once

- Enter `10`: Click 10 times

- Enter `0`: Click continuously until manually stopped

## Usage Suggestions

- When using it for the first time, test the parameters on a less important interface first.

- If the point is off, delete it directly with `DEL`, then use `+` to re-select the point.

- If the clicking is too fast, increase the base interval first.

- To make the rhythm less rigid, you can appropriately increase the random additional delay.

## Usage Reminders

- AutoTapper will not automatically click just because you have enabled accessibility services.

- It will only actually execute when you manually click `GO` or "Start Clicking" on the main interface.

- Please only use it in scenarios where you are clearly aware of and allow it.

- It is not recommended to use it for behaviors that violate platform rules, circumvent risk control, or affect the services of others.
