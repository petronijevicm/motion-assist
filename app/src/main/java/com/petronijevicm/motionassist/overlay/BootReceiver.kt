/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.petronijevicm.motionassist.data.CueSettings

/** Brings cues back after a reboot or an app update if they were left on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!CueSettings(context).enabled || !Settings.canDrawOverlays(context)) return
        try {
            CueOverlayService.start(context)
        } catch (e: IllegalStateException) {
            // Background start refused; the tile or the app will start it later
        } catch (e: SecurityException) {
            // Same as above on some OEM builds
        }
    }
}
