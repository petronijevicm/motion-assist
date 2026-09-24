#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 petronijevicm
# SPDX-License-Identifier: Apache-2.0
"""
Generates the looping tutorial illustration (an <animated-vector> drawable):
a phone with cues in its side margins, a steering wheel and a brake pedal.
The wheel turns left, then right, then the pedal is pressed, and the cues
shift the way loose objects in a vehicle would.

Usage: make_tutorial_drawable.py <output.xml> <palette>
palette: "app" (Material 3 theme attributes) or "platform" (android: attributes,
for use inside the system Settings app).
"""
import sys

W, H = 360, 200
DURATION = 6000

PALETTES = {
    "app": {
        "outline": "?attr/colorOutline",
        "content": "?attr/colorOutlineVariant",
        "screen": "?attr/colorSurfaceContainerHighest",
        "cue": "?attr/colorPrimary",
        "wheel": "?attr/colorOnSurfaceVariant",
        "pedal": "?attr/colorTertiary",
    },
    "platform": {
        "outline": "?android:attr/textColorSecondary",
        "content": "?android:attr/textColorTertiary",
        "screen": "?android:attr/colorBackgroundFloating",
        "cue": "?android:attr/colorAccent",
        "wheel": "?android:attr/textColorSecondary",
        "pedal": "?android:attr/colorAccent",
    },
}


def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0 1 {x + w},{y + r} V{y + h - r} "
            f"A{r},{r} 0 0 1 {x + w - r},{y + h} H{x + r} A{r},{r} 0 0 1 {x},{y + h - r} "
            f"V{y + r} A{r},{r} 0 0 1 {x + r},{y} Z")


def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


# Scene geometry
PHONE = (132, 12, 96, 176, 14)
SCREEN = (138, 22, 84, 156, 8)
CUE_COLUMNS = (146, 214)
CUE_ROWS = range(-2, 210, 22)
WHEEL = (66, 100)
PEDAL = (294, 100)

# (fraction, value) keyframes for one loop
TIMELINE = {
    "wheel.rotation": [(0, 0), (.07, 0), (.23, -35), (.30, -35), (.40, 0), (.53, 35), (.60, 35), (.70, 0), (1, 0)],
    "cues.translateX": [(0, 0), (.07, 0), (.23, 8), (.30, 8), (.40, 0), (.53, -8), (.60, -8), (.70, 0), (1, 0)],
    "cues.translateY": [(0, 0), (.72, 0), (.78, -12), (.85, -12), (.95, 0), (1, 0)],
    "pedal.scaleY": [(0, 1), (.72, 1), (.78, .78), (.85, .78), (.95, 1), (1, 1)],
}


def vector(p):
    px, py, pw, ph, pr = PHONE
    sx, sy, sw, sh, sr = SCREEN
    wx, wy = WHEEL
    dx, dy = PEDAL
    content = "".join(
        f'\n                <path android:fillColor="{p["content"]}" android:pathData="{rrect(166, y, w, 6, 3)}" />'
        for y, w in [(58, 28), (72, 22), (86, 28), (100, 18), (114, 28), (128, 24), (142, 14)])
    cues = "".join(
        f'\n                    <path android:fillColor="{p["cue"]}" android:pathData="{circle(x + (8 if (i % 2) else 0) * (1 if x < 180 else -1), y, 5)}" />'
        for x in CUE_COLUMNS for i, y in enumerate(CUE_ROWS))
    spokes = "".join(
        f'\n            <path android:fillColor="{p["wheel"]}" android:pathData="{d}" />'
        for d in [
            f"M{wx - 30},{wy - 2.5} H{wx - 6} V{wy + 2.5} H{wx - 30} Z",
            f"M{wx + 6},{wy - 2.5} H{wx + 30} V{wy + 2.5} H{wx + 6} Z",
            f"M{wx - 2.5},{wy + 6} H{wx + 2.5} V{wy + 30} H{wx - 2.5} Z",
        ])
    return f'''<vector
            android:width="{W}dp"
            android:height="{H}dp"
            android:viewportWidth="{W}"
            android:viewportHeight="{H}">

            <!-- Steering wheel -->
            <group android:name="wheel" android:pivotX="{wx}" android:pivotY="{wy}">
                <path android:strokeColor="{p["wheel"]}" android:strokeWidth="6" android:pathData="{circle(wx, wy, 30)}" />
                <path android:fillColor="{p["wheel"]}" android:pathData="{circle(wx, wy, 7)}" />{spokes}
                <path android:fillColor="{p["cue"]}" android:pathData="{rrect(wx - 4, wy - 34, 8, 8, 2)}" />
            </group>

            <!-- Phone -->
            <path android:strokeColor="{p["outline"]}" android:strokeWidth="3" android:pathData="{rrect(px, py, pw, ph, pr)}" />
            <group android:name="screen">
                <clip-path android:pathData="{rrect(sx, sy, sw, sh, sr)}" />
                <path android:fillColor="{p["screen"]}" android:pathData="{rrect(sx, sy, sw, sh, sr)}" />{content}
                <group android:name="cues">{cues}
                </group>
            </group>

            <!-- Brake pedal -->
            <group android:name="pedal" android:pivotX="{dx}" android:pivotY="{dy + 24}">
                <path android:fillColor="{p["pedal"]}" android:pathData="{rrect(dx - 14, dy - 24, 28, 48, 6)}" />
                <path android:fillColor="{p["screen"]}" android:pathData="{rrect(dx - 8, dy - 14, 16, 4, 2)}" />
                <path android:fillColor="{p["screen"]}" android:pathData="{rrect(dx - 8, dy - 4, 16, 4, 2)}" />
                <path android:fillColor="{p["screen"]}" android:pathData="{rrect(dx - 8, dy + 6, 16, 4, 2)}" />
            </group>
        </vector>'''


def target(name, props):
    holders = []
    for prop, frames in props:
        keys = "".join(
            f'\n                        <keyframe android:fraction="{f}" android:value="{v}" android:valueType="floatType"'
            + ('' if i == 0 else ' android:interpolator="@android:interpolator/fast_out_slow_in"') + ' />'
            for i, (f, v) in enumerate(frames))
        holders.append(f'''
                    <propertyValuesHolder android:propertyName="{prop}">{keys}
                    </propertyValuesHolder>''')
    return f'''
    <target android:name="{name}">
        <aapt:attr name="android:animation">
            <objectAnimator
                android:duration="{DURATION}"
                android:repeatCount="infinite">{"".join(holders)}
            </objectAnimator>
        </aapt:attr>
    </target>'''


def main():
    out, palette = sys.argv[1], PALETTES[sys.argv[2]]
    groups = {}
    for key, frames in TIMELINE.items():
        name, prop = key.split(".")
        groups.setdefault(name, []).append((prop, frames))
    targets = "".join(target(n, props) for n, props in groups.items())
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  SPDX-FileCopyrightText: 2026 petronijevicm
  SPDX-License-Identifier: Apache-2.0

  Generated by tools/make_tutorial_drawable.py - edit the script, not this file.
-->
<animated-vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">

    <aapt:attr name="android:drawable">
        {vector(palette)}
    </aapt:attr>
{targets}
</animated-vector>
'''
    with open(out, "w") as f:
        f.write(xml)


if __name__ == "__main__":
    main()
