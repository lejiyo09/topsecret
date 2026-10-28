package io.github.lejiyo09.independentbrowser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.net.URISyntaxException
import java.util.Locale

/**
 * Links with non-web schemes (mailto:, tel:, intent:, market:, ...) leave the browser only
 * after the user clicked them and confirmed. Local-file style schemes are never opened.
 */
object ExternalLinks {
    private val BLOCKED_SCHEMES = setOf("file", "content", "chrome", "chrome-extension", "android-app")

    fun handle(activity: Activity, uri: Uri, hasGesture: Boolean, loadFallback: (String) -> Unit) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return
        if (scheme in BLOCKED_SCHEMES) {
            if (hasGesture) Toast.makeText(activity, R.string.external_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        var fallbackUrl: String? = null
        val intent = if (scheme == "intent") {
            val parsed = try {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } catch (e: URISyntaxException) {
                return
            }
            fallbackUrl = parsed.getStringExtra("browser_fallback_url")?.takeIf { UrlInput.isWebUrl(it) }
            // Web pages may only reach exported, browsable activities - never a specific component.
            parsed.component = null
            parsed.selector = null
            if (parsed.action == null) parsed.action = Intent.ACTION_VIEW
            parsed
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = 0 // drops any URI-permission grant flags a page tried to add

        if (!hasGesture) {
            // No automatic app launches from redirects/scripts; fall back to the web page if one was given.
            fallbackUrl?.let(loadFallback)
            return
        }

        val target = if (scheme == "intent") intent.`package` ?: intent.dataString ?: scheme else uri.toString()
        AlertDialog.Builder(activity)
            .setTitle(R.string.external_title)
            .setMessage(activity.getString(R.string.external_message, target.take(300)))
            .setPositiveButton(R.string.external_open) { _, _ ->
                try {
                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val fallback = fallbackUrl
                    if (fallback != null) {
                        loadFallback(fallback)
                    } else {
                        Toast.makeText(activity, R.string.external_no_app, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(activity, R.string.external_blocked, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> fallbackUrl?.let(loadFallback) }
            .show()
    }
}
