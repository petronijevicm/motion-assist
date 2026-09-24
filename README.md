<h1 align="center">Motion Assist 🚗💨</h1>

<div align="center">

<p><i> Vehicle Motion Cues for Android &amp; AOSP / LineageOS </i></p>

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Material 3](https://img.shields.io/badge/Material_You-005AC1?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![GitHub license](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge)](LICENSE)

</div>

[![Typing SVG](https://readme-typing-svg.herokuapp.com?font=Fira+Code&size=23&pause=1000&color=F7F7F7&vCenter=true&width=435&height=30&lines=ABOUT)](https://git.io/typing-svg)

**Motion Assist** brings official vehicle motion cues (anti-motion sickness visual anchors) to Android. By projecting synchronized visual cues that move in real-time response to physical vehicle acceleration, braking, and cornering, Motion Assist resolves vestibular-ocular sensory conflict while reading or using your phone in cars, buses, and trains.

This repository contains both:
1. **The Standalone Android App**: A modern **Material You 3** companion app with full customization, real-time interactive previews, system overlay rendering, and a Quick Settings tile.
2. **The Official AOSP / LineageOS System Framework Patches**: Native deep OS integration into `frameworks/base` (`SystemUI` 6-DOF sensor fusion) and `packages/apps/Settings` (native Accessibility menu & Quick Settings auto-add).

______________________________________________________________________

[![Typing SVG](https://readme-typing-svg.herokuapp.com?font=Fira+Code&size=23&pause=1000&color=F7F7F7&vCenter=true&width=435&height=30&lines=FEATURES)](https://git.io/typing-svg)

- **6-DOF Sensor Fusion Engine**: Fuses Linear Acceleration, Rotation Vector, and Gyroscope sensor streams into true world-space physical inertia vectors.
- **Quick Settings Tile**: Toggle Motion Assist instantly from anywhere with one tap (`ic_qs_motion_assist`). Long-press to open settings or customization.
- **Vehicle Auto-Detection**: Intelligently monitors sustained vehicle movement patterns and activates visual cues only when moving, turning off automatically when stationary.
- **Customizable Shapes**: Support for **Circle**, **Squircle**, **Pentagon**, and **Diamond** shapes.
- **Material You Dynamic Colors & Adaptive Contrast**:
  - Dynamically extracts primary accent colors from your active wallpaper.
  - Curated color palettes: *Salmon Pink*, *Amber Yellow*, *Mint Green*, *Soft Blue*.
  - **Adaptive Mode**: Automatically calculates background luminance and applies high-contrast dual strokes to guarantee clear visibility over light or dark apps.
- **Smooth 120 Hz Animation**: Uncapped high-refresh rate rendering with intelligent power-saving throttling when Battery Saver is engaged.
- **Shape & Color Randomization**: Option to cycle visual styles periodically for dynamic stimulus.
- **Interactive Live Preview**: Test cue dynamics by tilting the device or dragging directly on the preview card.

______________________________________________________________________

[![Typing SVG](https://readme-typing-svg.herokuapp.com?font=Fira+Code&size=23&pause=1000&color=F7F7F7&vCenter=true&width=435&height=30&lines=INSTALL)](https://git.io/typing-svg)

### Option 1: Standalone Android App (Any Android Device 10+)
1. Download the latest `MotionAssist.apk` from the [Releases](https://github.com/rhythmcreative/motion-assist/releases) section.
2. Install the APK on your device.
3. Open **Motion Assist**, grant the "Display over other apps" (System Alert Window) permission.
4. Tap **Add tile to Quick Settings** or pull down your notification shade twice, tap the Edit pencil, and drag **Motion assist** into your active tiles.

### Option 2: LineageOS / AOSP ROM Compilation
Apply the native patches included under `aosp-patches/`:
```bash
# In your AOSP / LineageOS source tree root:
git -C frameworks/base apply aosp-patches/frameworks_base_motion_assist.patch
git -C packages/apps/Settings apply aosp-patches/packages_apps_settings_motion_assist.patch
```

______________________________________________________________________

[![Typing SVG](https://readme-typing-svg.herokuapp.com?font=Fira+Code&size=23&pause=1000&color=F7F7F7&vCenter=true&width=435&height=30&lines=TESTS)](https://git.io/typing-svg)

To run the unit tests and verify physical engine calculations:
```bash
./gradlew testDebugUnitTest
```

All physics formulas, zero vector initializations, deadband thresholds, and contrast alpha functions are fully covered and verified.

______________________________________________________________________

<div align="center">

<p>Made with ❤️ from rhythmcreative.</p>

</div>
