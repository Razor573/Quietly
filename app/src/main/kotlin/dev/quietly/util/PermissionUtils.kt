package dev.quietly.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Returns true if the user has granted PACKAGE_USAGE_STATS access.
 * This permission cannot be requested at runtime — the user must go to
 * Settings > Apps > Special app access > Usage access and toggle it on.
 */
fun Context.hasUsagePermission(): Boolean {
    val appOps  = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
