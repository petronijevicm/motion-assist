# Motion Assist

Vehicle motion cues for Android: moving dots at the edges of the screen that follow the
vehicle's motion, to ease motion sickness while you read or watch in a car, bus or train.

Works as a standalone app on Android 10 and later, and as a built-in feature for LineageOS 24.0
through the patches in `aosp-patches/`.

No network access, no Google Play services, no analytics. The app declares no `INTERNET`
permission; everything is computed on the device from its motion sensors.

## Features

- **Cues that follow the vehicle, not the phone.** Acceleration shifts the dots the opposite way,
  like loose objects in the vehicle, and they spring back when it ends. Turning scrolls them
  sideways, bumps briefly change their size. Tilting or turning the phone in your hand (for
  example, lifting it to look at it) is detected and ignored.
- **Horizon line** (optional): an artificial horizon, level with the ground, in the side bands.
- **Start in a moving vehicle**: show cues only while vehicle motion is detected (a heuristic
  based on sustained acceleration; walking is ignored).
- **Appearance**: circle, squircle, pentagon or diamond; wallpaper accent, salmon, amber, mint,
  sky or high contrast; opacity and size.
- **Motion tuning**: how far cues travel and how quickly they react.
- **Cue area**: side bands of adjustable width, up to the whole screen.
- **Shuffle**: scattered layout with shape and colour changing every few seconds.
- **Smooth animation** at the display's full refresh rate, capped at 30 fps in Battery Saver.
- **Quick Settings tile**, notification with a Stop action, restore after reboot.
- **Touches pass through** on Android 12 and later: the overlay stays under the system's
  maximum obscuring opacity, so apps underneath stay usable.
- **Battery**: sensors stop while the screen is off. In auto-start mode, detection runs at a
  low, hardware-batched rate until motion is detected.
- In-app **guide** animation and **live preview** that reacts to your phone's motion.
- Translated into English, German, Spanish, French, Italian, Japanese, Portuguese, Russian and
  Simplified Chinese.

## Install

1. Download the APK from [Releases](../../releases) and install it.
2. Open Motion Assist and switch on **Motion cues**. Allow **Display over other apps** when asked.
   If that switch is greyed out (Android 13 and later, for apps installed outside an app store):
   open **App info**, tap **⋮**, choose **Allow restricted settings**, then try again.
3. Optional: tap **Add tile** to put the Quick Settings tile in your panel.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### Release APKs

CI (`.github/workflows/android.yml`) builds, tests and lints every push and pull request.
When a GitHub release is published, it builds a signed release APK and attaches it to that
release. Signing uses four repository secrets:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the keystore file, base64 encoded |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

A key can be created with the JDK's `keytool`:

```bash
keytool -genkeypair -v -keystore release.keystore -alias motionassist \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.keystore   # value for SIGNING_KEYSTORE_BASE64
```

Keep the keystore safe: updates must be signed with the same key.

To sign locally, set `SIGNING_KEYSTORE_FILE` (path) and the three other variables above in the
environment and run `./gradlew assembleRelease`.

## LineageOS integration

`aosp-patches/` contains two patches for **LineageOS 24.0** (`lineage-24.0` branches). They
build on the platform motion cues overlay that ships in `frameworks/base`.

```bash
# From the root of the source tree
git -C frameworks/base am /path/to/aosp-patches/frameworks_base_motion_assist.patch
git -C packages/apps/Settings am /path/to/aosp-patches/packages_apps_settings_motion_assist.patch
```

- **frameworks/base (SystemUI)**: `MotionAssistController` runs the same sensor fusion as the
  app and animates the platform `MotionCuesUi` overlay; built-in shapes with contrast outlines,
  a high-contrast mode that follows the status bar tint, scattered layout and high refresh rate
  drawing; a Quick Settings tile. On a secure lock screen, Quick Settings tiles (except the
  flashlight) act only after unlocking.
- **packages/apps/Settings**: Accessibility > Motion cues, with the guide animation, main
  switch, start-in-vehicle, tile shortcut and a Customize page.

Settings are stored in `Settings.Secure`:

| Key | Values (default) |
|---|---|
| `motion_assist_enabled` | 0/1 (0) |
| `motion_assist_vehicle_auto` | 0/1 (0) |
| `motion_assist_color` | 0 wallpaper, 1 salmon, 2 amber, 3 mint, 4 sky, 5 high contrast (0) |
| `motion_assist_shape` | 0 circle, 1 squircle, 2 pentagon, 3 diamond (0) |
| `motion_assist_opacity` | 10..100 (60) |
| `motion_assist_size` | 50..200 (100) |
| `motion_assist_movement` | 25..200 (100) |
| `motion_assist_responsiveness` | 0..100 (70) |
| `motion_assist_mode` | 0 side bands, 1 full screen (0) |
| `motion_assist_randomize` | 0/1 (0) |
| `motion_assist_smooth_animation` | 0/1 (1) |

`aosp-source/` holds readable copies of the files the patches add. The sensor fusion
(`MotionFilter`, `MotionEstimator`, `MotionVector`) and cue physics (`CueMotion`) are the app's
own files with only the package changed; keep them in sync when editing.

The patches have been checked to apply cleanly to `lineage-24.0` and their new sources were
type-checked, but they have not been built into a ROM or run on a device yet.

## How it works

`MotionFilter` works from the gravity direction and the screen axes, so compass heading never
matters. Linear acceleration is projected onto the horizontal plane and split into sideways and
forward/backward components relative to the screen, then smoothed and slowly de-biased. The
gyroscope provides the turn rate about world vertical; fast pitch or roll of the phone itself
pauses the acceleration input for half a second, which is what keeps hand movement from moving
the cues. `CueMotion` turns that into a displacement with a critically damped spring plus an
unbounded sideways scroll, and `CueFieldView` draws the field.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
