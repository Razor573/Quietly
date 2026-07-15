package dev.quietly.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

fun Context.hasUsageStatsPermission(): Boolean {
    val aom = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val mode = aom.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
