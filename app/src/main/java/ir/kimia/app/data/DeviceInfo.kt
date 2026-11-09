package ir.kimia.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/** What the phone can actually do. Read once at startup. */
data class DeviceInfo(
    val totalRamMb: Long,
    val freeStorageMb: Long,
    val cores: Int,
    val androidSdk: Int,
    val model: String,
) {
    companion object {
        fun read(context: Context): DeviceInfo {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val stat = StatFs(context.filesDir.path)
            return DeviceInfo(
                totalRamMb = mi.totalMem / (1024 * 1024),
                freeStorageMb = stat.availableBytes / (1024 * 1024),
                cores = Runtime.getRuntime().availableProcessors(),
                androidSdk = Build.VERSION.SDK_INT,
                model = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }
}
