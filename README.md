# Padmap

Play touch-only Android games with a gamepad. Padmap turns gamepad buttons and sticks into touches on the
screen, using a layout you place over the game with an on-screen editor. Layouts are plain JSON files, one per
game, that you can edit by hand and share. No root needed: a helper started once over adb does the input
injection, the same way scrcpy and Shizuku work.

## Setup

1. Install `padmap.apk` and open Padmap. Allow "display over other apps" (the daemon also grants it when it
   starts).
2. Turn on USB debugging and, from a computer with adb, run the command Padmap shows:
   `adb shell sh /sdcard/Android/data/app.padmap/files/start.sh`. Rooted devices can run the same file with
   `su -c sh`. The daemon keeps running after you unplug, until the device restarts.
3. Connect a gamepad and open your game. Padmap follows the focused app and loads its layout.

## Editing a layout

Press **Select + Start** on the gamepad, or tap the floating ✥ button, to open the editor over the game. The
button hides while the gamepad is mapping an app with a layout, so mapped touches can't land on it.

- **+ Tap**, **+ Stick**, **+ Aim** add a control in the middle of the screen, already selected (yellow). Drag
  it onto the game's on-screen button.
- Bind the selected control from the pad. A tap: press its button. A joystick: move its stick. An aim: press its
  button, then move the stick that aims it (the label shows `LB+?` until you do).
- Tap a control to select it again and rebind, move or resize it.
- **−** / **+** resize the selected control: a tap's size, how far a joystick drags, an aim's reach.
- A selected aim has a checkbox, **Aim with LS while RS is centred** (off by default): the other stick aims the
  skill while its own stick is untouched.
- **Close** (or Select + Start) saves any changes and returns to the game.

For example, an aim skill on LB aimed with the right stick: **+ Aim**, drag it onto the skill, press **LB**, push
the **right stick**, **Close**.

While the editor is open the pad doesn't touch the game.

## Controls

| Type | Behaviour |
| --- | --- |
| `tap` | Touches its spot while the button is held |
| `joystick` | When the stick leaves its deadzone: touches down at the centre, then drags in the stick's direction, up to `radius` |
| `aim` | MOBA-style skill: touches down on the skill while the button is held, drags by the stick up to `radius`, lifts (casts) on release. Without the stick it casts in place; with `"fallback": true` the other stick aims while `stick` is centred |

## Layout files

`/sdcard/Padmap/<app package>.json`, read again whenever it changes:

```json
{
  "version": 1,
  "controls": [
    { "type": "joystick", "stick": "LS", "x": 0.16, "y": 0.75, "radius": 0.12 },
    { "type": "tap", "button": "A", "x": 0.88, "y": 0.8, "radius": 0.05 },
    { "type": "aim", "button": "RB", "stick": "RS", "fallback": false, "x": 0.75, "y": 0.7, "radius": 0.15 }
  ]
}
```

`x` and `y` are fractions of the screen's width and height in the game's orientation; `radius` is a fraction of
the screen height. Buttons: `A B X Y LB RB LT RT SELECT START HOME L3 R3 UP DOWN LEFT RIGHT`, or `KEY_<hex code>`
for others. Sticks: `LS`, `RS`.

## Vibration

While Padmap is mapping the focused app, the app's vibrations rumble the gamepad (if it has motors Android can
drive). On a device with a vibrator, Padmap mirrors the vibrator's on/off state, so the phone vibrates too and the
pad's rumble has no strength levels. On Android without a vibrator, e.g. in a container, Padmap registers a
stand-in vibrator service that apps vibrate through instead, which forwards each vibration's length. The
`padmap` log says which applies (`vibration: ...`) and how many motors the pad has (`rumble: ...`).

## Shared layouts

[`layouts/`](layouts/) holds layouts for particular games: copy one to `/sdcard/Padmap/` and adjust it in the
editor.

| Game | Controls | Status |
| --- | --- | --- |
| Pokémon UNITE (`jp.pokemon.pokemonunite`) | LS moves, A attacks, RB / RT / LT are Move 1 / Move 2 / Unite Move aimed with RS, X battle item, Y recall | Positions approximate, not yet checked in a match |

## Limits

- Touching the screen while the gamepad is mid-gesture ends the gamepad's touches (Android doesn't merge two
  touch devices); they resume with the next input.
- The pad is hidden from Android only while the focused app has a layout or the editor is open.
- One gamepad at a time: the first device with gamepad buttons and two stick axes.
- Without a vibrator, an app that looked up the vibrator before the daemon started keeps a vibrator-less handle
  until it restarts. Mirroring needs the daemon's user to hold `ACCESS_VIBRATOR_STATE`; the log says if it doesn't.

## How it works

The APK holds both halves. The daemon (`app.padmap.daemon.Main`) runs from the APK via `app_process` as adb's
shell user or root, which may inject input: it reads the pad from evdev (`/dev/input`, grabbed while mapping),
maps it through the focused app's layout and injects multi-touch `MotionEvent`s. Each touch lands at its
anchor, holds for a few frames and then ramps to its target, so games that sample touches per frame see where a
drag started. The app (`app.padmap.ui`) is the overlay: the bubble, the editor and setup screen. They talk over
a loopback socket: the daemon hands the app a fresh token through `am start-foreground-service` (the service
requires `INJECT_EVENTS`, which only shell and root hold), opens each connection by proving it knows the token,
and drops clients that don't present it.

## Building

`./build.sh` (JDK 17, Android SDK with build-tools 36 and platform 36 under `ANDROID_HOME`, default
`~/android/sdk`) runs the tests and writes `build/padmap.apk`, signed with `~/.android/debug.keystore`.
`hidden/` holds compile-only stubs of hidden framework classes (the vibrator service interface); the device's own
classes are used at runtime.
