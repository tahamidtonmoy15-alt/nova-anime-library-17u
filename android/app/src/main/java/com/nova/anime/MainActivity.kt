package com.nova.anime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** The NOVA Anime Universe UI runs in WebView; all selected videos are copied into
 * app-private storage and opened by the native LibVLC player.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    @Volatile private var lastPickedVideoUri: Uri? = null
    private var pendingVideoPicker = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = fileCallback
        fileCallback = null
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            if (pendingVideoPicker) lastPickedVideoUri = uri
            callback?.onReceiveValue(arrayOf(uri))
        } else callback?.onReceiveValue(null)
        pendingVideoPicker = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF080311.toInt()
        window.navigationBarColor = 0xFF080311.toInt()
        web = WebView(this).apply {
            setBackgroundColor(0xFF080311.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    val accepted = params.acceptTypes.orEmpty().joinToString(",").lowercase(Locale.ROOT)
                    pendingVideoPicker = accepted.contains("video/") ||
                        listOf(".mkv", ".avi", ".mp4", ".mov", ".webm", ".m4v").any { accepted.contains(it) }
                    val types = if (pendingVideoPicker) {
                        arrayOf("video/*", "application/octet-stream")
                    } else {
                        arrayOf("text/vtt", "application/x-subrip", "text/plain", "application/octet-stream")
                    }
                    return try {
                        openDocument.launch(types)
                        true
                    } catch (e: Exception) {
                        fileCallback = null
                        callback.onReceiveValue(null)
                        Toast.makeText(this@MainActivity, "ফাইল নির্বাচন চালু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
                    if (host == "tahamidtonmoy15-alt.github.io" || host.endsWith("github.io")) return false
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: Exception) { false }
                }
            }
            addJavascriptInterface(NovaBridge(), "NOVA")
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFF080311.toInt())
            addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        web.loadUrl(NOVA_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun selectedUri(): Uri? {
        val uri = lastPickedVideoUri
        lastPickedVideoUri = null
        return uri
    }

    private fun launchPlayer(path: String?, title: String): Boolean {
        if (path.isNullOrBlank()) return false
        val file = java.io.File(path)
        if (!file.isFile || file.length() <= 0L) return false
        runOnUiThread {
            startActivity(Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, file.absolutePath)
                .putExtra(PlayerActivity.EXTRA_TITLE, title))
        }
        return true
    }

    inner class NovaBridge {
        @android.webkit.JavascriptInterface
        fun registerEpisodeVideo(key: String): Boolean = register("episode:$key")

        @android.webkit.JavascriptInterface
        fun playEpisodeVideo(key: String): Boolean {
            val file = NativeVideoStore.findOrMigrateLegacyEpisode(this@MainActivity, key)
            return launchPlayer(file?.absolutePath, file?.name ?: "Anime episode")
        }

        @android.webkit.JavascriptInterface
        fun forgetEpisodeVideo(key: String) = NativeVideoStore.delete(this@MainActivity, "episode:$key")

        @android.webkit.JavascriptInterface
        fun registerReelVideo(id: String): Boolean = register("reel:$id")

        @android.webkit.JavascriptInterface
        fun playReelVideo(id: String): Boolean {
            val file = NativeVideoStore.find(this@MainActivity, "reel:$id")
            return launchPlayer(file?.absolutePath, file?.name ?: "Anime Reel")
        }

        @android.webkit.JavascriptInterface
        fun forgetReelVideo(id: String) = NativeVideoStore.delete(this@MainActivity, "reel:$id")

        @android.webkit.JavascriptInterface
        fun openLibrary() = runOnUiThread { web.evaluateJavascript("switchView('library')", null) }

        @android.webkit.JavascriptInterface
        fun openReels() = runOnUiThread { web.evaluateJavascript("switchView('reels')", null) }

        @android.webkit.JavascriptInterface
        fun storageBytes(): Long = NativeVideoStore.bytes(this@MainActivity)
    }

    private fun register(key: String): Boolean {
        val uri = selectedUri() ?: return false
        return try {
            NativeVideoStore.importVideo(this, uri, key)
            true
        } catch (e: Exception) {
            android.util.Log.e("NOVA", "Video import failed for $key", e)
            runOnUiThread {
                Toast.makeText(this, "ভিডিও NOVA-তে কপি হয়নি: ${e.message ?: "স্টোরেজ পরীক্ষা করো"}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        web.removeJavascriptInterface("NOVA")
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val NOVA_URL = "https://tahamidtonmoy15-alt.github.io/nova-anime-library-17u/"
    }
}
