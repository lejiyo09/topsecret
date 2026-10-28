package io.github.lejiyo09.independentbrowser

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Every download is shown to the user first (name, size, origin, warnings) and only then
 * handed to the system DownloadManager, which saves into the shared Downloads folder
 * (on ChromeOS this is the "Downloads" folder of the Files app).
 */
class DownloadHandler(private val activity: ComponentActivity) {

    private var pendingRequest: DownloadManager.Request? = null
    private var pendingName: String? = null

    private val storagePermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingRequest
            val name = pendingName
            pendingRequest = null
            pendingName = null
            if (request == null || name == null) return@registerForActivityResult
            if (granted) enqueue(request, name) else toast(activity.getString(R.string.download_permission_denied))
        }

    fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        pageUrl: String?,
    ) {
        if (!DownloadPolicy.isDownloadableUrl(url)) {
            toast(activity.getString(R.string.download_unsupported_scheme))
            return
        }
        val fileName = DownloadPolicy.sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        val host = Uri.parse(url).host.orEmpty()

        val message = buildString {
            appendLine(activity.getString(R.string.download_file, fileName))
            appendLine(
                if (contentLength > 0) {
                    activity.getString(R.string.download_size, Formatter.formatFileSize(activity, contentLength))
                } else {
                    activity.getString(R.string.download_size_unknown)
                }
            )
            appendLine(activity.getString(R.string.download_source, host))
            appendLine(activity.getString(R.string.download_destination))
            if (DownloadPolicy.isInsecure(url)) {
                appendLine()
                appendLine(activity.getString(R.string.download_insecure_warning))
            }
            if (DownloadPolicy.isPotentiallyDangerous(fileName)) {
                appendLine()
                appendLine(activity.getString(R.string.download_dangerous_warning, DownloadPolicy.extensionOf(fileName)))
            }
        }.trimEnd()

        AlertDialog.Builder(activity)
            .setTitle(R.string.download_title)
            .setMessage(message)
            .setPositiveButton(R.string.download_confirm) { _, _ ->
                start(url, userAgent, mimeType, fileName, host, pageUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun start(url: String, userAgent: String?, mimeType: String?, fileName: String, host: String, pageUrl: String?) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            // Send this app's own cookies so downloads behind a login work; never Chrome's.
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            // Don't leak an https referrer to an http download.
            if (pageUrl != null && UrlInput.isWebUrl(pageUrl) && !(UrlInput.isHttps(pageUrl) && !UrlInput.isHttps(url))) {
                addRequestHeader("Referer", pageUrl)
            }
            setTitle(fileName)
            setDescription(host)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingRequest = request
            pendingName = fileName
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueue(request, fileName)
        }
    }

    private fun enqueue(request: DownloadManager.Request, fileName: String) {
        try {
            activity.getSystemService<DownloadManager>()!!.enqueue(request)
            toast(activity.getString(R.string.download_started, fileName))
        } catch (e: RuntimeException) {
            toast(activity.getString(R.string.download_failed))
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}
