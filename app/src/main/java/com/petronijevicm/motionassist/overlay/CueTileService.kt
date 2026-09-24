/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.overlay

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.petronijevicm.motionassist.R
import com.petronijevicm.motionassist.data.CueSettings
import com.petronijevicm.motionassist.ui.MainActivity

/** Quick Settings tile: tap to toggle cues. Long-press opens the app (see manifest). */
class CueTileService : TileService() {

    private val settings by lazy { CueSettings(this) }

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val turnOn = !settings.enabled
        if (turnOn && !Settings.canDrawOverlays(this)) {
            // Needs the overlay permission first; the app explains and asks for it
            openApp()
            return
        }
        settings.enabled = turnOn
        if (turnOn) CueOverlayService.start(this) else CueOverlayService.stop(this)
        render()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = settings.enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, if (on) R.drawable.ic_tile else R.drawable.ic_tile_off)
        tile.subtitle = getString(
            when {
                !on -> R.string.tile_state_off
                settings.autoStart -> R.string.tile_state_auto
                else -> R.string.tile_state_on
            }
        )
        tile.updateTile()
    }
}
