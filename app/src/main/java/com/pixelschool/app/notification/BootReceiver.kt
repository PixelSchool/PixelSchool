package com.pixelschool.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pixelschool.app.data.local.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val settingsStore = SettingsStore(context)
            if (settingsStore.notificationsEnabled.value) {
                MarksSyncWorker.schedule(context)
            }
        }
    }
}
