package dev.wander.android.opentagviewer.util

class Utils {

    fun parseTimeToEpochMs(time: String?): Long {
        val t = time?.trim().orEmpty()
        if (t.isEmpty()) return 0L

        val asLong = t.toLongOrNull()
        if (asLong != null) {
            return if (asLong in 1L..9_999_999_999L) asLong * 1000L else asLong
        }

        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC") // ou TimeZone.getDefault()
            val date = sdf.parse(t)
            date?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}


