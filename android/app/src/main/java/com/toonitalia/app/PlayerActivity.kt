package com.toonitalia.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.regex.Pattern

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var webView: WebView? = null
    private var useWebView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val videoUrl = intent.getStringExtra("video_url") ?: ""
        val title = intent.getStringExtra("title") ?: ""

        if (videoUrl.isNotBlank()) {
            resolveAndPlay(videoUrl)
        } else {
            Toast.makeText(this, "URL non valido", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isDirectStream(url: String): Boolean {
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".mkv") ||
                url.contains(".webm")
    }

    private fun isEmbedHost(url: String): Boolean {
        return url.contains("uqload") || url.contains("chuckle-tube") ||
                url.contains("voe") || url.contains("vidhideplus") ||
                url.contains("ryderjet") || url.contains("luluvdo") ||
                url.contains("streamtape") || url.contains("strcloud") ||
                url.contains("scloud") || url.contains("supervideo") ||
                url.contains("dood") || url.contains("mixdrop") ||
                url.contains("filelions") || url.contains("streamwish") ||
                url.contains("vidmoly") || url.contains("embedsb")
    }

    private fun resolveAndPlay(url: String) {
        // Direct streamable file: play with ExoPlayer.
        if (isDirectStream(url)) {
            showExoPlayer()
            playVideo(url)
            return
        }

        // Embed hosts use their own obfuscated players: load them in a WebView
        // so the site's native player handles extraction/playback reliably.
        if (isEmbedHost(url)) {
            Toast.makeText(this, "Caricamento video...", Toast.LENGTH_SHORT).show()
            showWebView(url)
            return
        }

        // Unknown host: try to extract a direct link, fall back to WebView.
        Toast.makeText(this, "Caricamento video...", Toast.LENGTH_SHORT).show()
        resolveGeneric(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(url: String) {
        useWebView = true
        val wv = WebView(this)
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            pluginState = WebSettings.PluginState.ON
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                return true
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                runOnUiThread {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Impossibile caricare il player. Prova un altro server.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        wv.loadUrl(url)
        webView = wv
        setContentView(wv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showExoPlayer() {
        useWebView = false
        val playerView = PlayerView(this)
        playerView.setBackgroundColor(Color.BLACK)
        setContentView(playerView)
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
    }

    private fun resolveGeneric(embedUrl: String) {
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://toonitalia.xyz/")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        NetworkModule.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Errore di connessione: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val html = try { response.body?.string() ?: "" } finally { response.close() }
                val videoUrl = extractGenericVideoUrl(html)
                runOnUiThread {
                    if (videoUrl != null) {
                        showExoPlayer()
                        playVideo(videoUrl)
                    } else {
                        // Could not extract a direct link: fall back to WebView.
                        showWebView(embedUrl)
                    }
                }
            }
        })
    }

    private fun extractGenericVideoUrl(html: String): String? {
        val patterns = listOf(
            Pattern.compile("file\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4|mkv)[^\"]*)\""),
            Pattern.compile("source\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("sources\\s*:\\s*\\[\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("sources\\s*:\\s*\\[\\s*\\{\\s*file\\s*:\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']"),
            Pattern.compile("[\"'](https?://[^\"']+\\.(mp4|mkv|avi)[^\"']*)[\"']"),
            Pattern.compile("src\\s*:\\s*[\"']?(https?://[^\"'\\s]+\\.(m3u8|mp4))"),
            Pattern.compile("file:[\"']?(https?://[^\"'\\s,]+)"),
            Pattern.compile("video_url\\s*[=:]\\s*[\"']?(https?://[^\"'\\s]+)"),
            Pattern.compile("videoUrl\\s*[=:]\\s*[\"']?(https?://[^\"'\\s]+)"),
            Pattern.compile("[\"'](https?://[^\"'\\s]*(?:s3|storage|stream|cdn|server\\d*)[^\"'\\s]*\\.(m3u8|mp4)[^\"'\\s]*)[\"']")
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                val url = m.group(1)
                if (url != null && url.length > 10 && !url.contains("example.com")) return url
            }
        }

        val packedMatch = Regex("eval\\(function\\(p,a,c,k,e,d\\).+?\\)").find(html)
        if (packedMatch != null) {
            try {
                val unpacked = unpackPackedJs(packedMatch.value)
                if (unpacked != null) {
                    val urlMatch = Regex("[\"'](https?://[^\"']+\\.(m3u8|mp4|mkv)[^\"']*)[\"']").find(unpacked)
                    if (urlMatch != null) return urlMatch.groupValues[1]
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun unpackPackedJs(packed: String): String? {
        return try {
            val base62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val args = Regex("}\\('(.+)',(\\d+),(\\d+),'([^']+)'\\.split\\('\\|'\\)\\)").find(packed)
                ?: return null
            val p = args.groupValues[1]
            val a = args.groupValues[2].toInt()
            val c = args.groupValues[3].toInt()
            val k = args.groupValues[4].split("|")
            val d = mutableMapOf<String, String>()
            var idx = c
            while (idx > 0) {
                idx--
                val key = if (idx < k.size) k[idx] else base62[idx % base62.length].toString()
                d[base62[idx].toString()] = key
            }
            var result = p
            for ((key, value) in d) {
                result = result.replace(Regex("\\b$key\\b"), value)
            }
            result
        } catch (_: Exception) { null }
    }

    private fun playVideo(url: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Errore riproduzione: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        if (useWebView) webView?.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        if (useWebView) webView?.onResume()
    }

    override fun onBackPressed() {
        if (useWebView && webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        webView?.destroy()
        webView = null
    }
}