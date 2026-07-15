package dev.quietly.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

fun Context.hasUsageStatsPermission(): Boolean {
    val aom = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        aom.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), packageName
        )
    } else {
        @Suppress("DEPRECATION")
        aom.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
