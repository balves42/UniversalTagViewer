package dev.wander.android.opentagviewer.util.validate

import android.webkit.URLUtil
import lombok.AccessLevel
import lombok.NoArgsConstructor
import java.net.URL

@NoArgsConstructor(access = AccessLevel.PRIVATE)
object FMDUrlValidatorUtil {
    @JvmStatic
    fun isValidFMDUrl(urlInput: String?): Boolean {
        if (!URLUtil.isHttpsUrl(urlInput) && !URLUtil.isHttpUrl(urlInput)) {
            return false
        }
        try {
            val url = URL(urlInput).toURI()

            // no path allowed -> this must be BASE URL
            return url.path == null || url.path.isEmpty()
        } catch (e: Exception) {
            return false
        }
    }
}
