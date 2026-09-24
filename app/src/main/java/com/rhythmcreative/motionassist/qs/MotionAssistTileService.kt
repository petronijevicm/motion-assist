/*
 * Copyright (C) 2026 rhythmcreative
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rhythmcreative.motionassist.qs

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.rhythmcreative.motionassist.R
import com.rhythmcreative.motionassist.engine.MotionPreferences
import com.rhythmcreative.motionassist.service.MotionAssistService
import com.rhythmcreative.motionassist.ui.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class MotionAssistTileService : TileService() {

    private lateinit var prefs: MotionPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = MotionPreferences(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val newState = !prefs.isEnabled
        prefs.isEnabled = newState
        if (newState) {
            MotionAssistService.start(this)
        } else {
            MotionAssistService.stop(this)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = prefs.isEnabled

        tile.label = getString(R.string.quick_settings_motion_assist_label)
        tile.icon = Icon.createWithResource(
            this,
            if (enabled) R.drawable.ic_qs_motion_assist_on else R.drawable.ic_qs_motion_assist_off
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) {
                if (prefs.isVehicleAuto) {
                    getString(R.string.quick_settings_motion_assist_secondary_label_auto)
                } else {
                    getString(R.string.quick_settings_motion_assist_secondary_label_on)
                }
            } else {
                getString(R.string.quick_settings_motion_assist_secondary_label_off)
            }
        }

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
