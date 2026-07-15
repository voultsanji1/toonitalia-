package com.toonitalia.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.regex.Pattern

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val playerView = PlayerView(this)
        setContentView(playerView)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoUrl = intent.getStringExtra("video_url") ?: ""
        val title = intent.getStringExtra("title") ?: ""

        if (videoUrl.isNotBlank()) {
            resolveAndPlay(videoUrl)
        } else {
            Toast.makeText(this, "URL non valido", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun resolveAndPlay(url: String) {
        if (url.contains("m3u8") || url.contains(".mp4") || url.contains(".mkv")) {
            playVideo(url)
            return
        }

        Toast.makeText(this, "Caricamento video...", Toast.LENGTH_SHORT).show()

        if (url.contains("uqload")) {
            resolveUqload(url)
        } else if (url.contains("chuckle-tube") || url.contains("voe")) {
            resolveVoe(url)
        } else if (url.contains("vidhideplus") || url.contains("ryderjet")) {
            resolveVidhide(url)
        } else if (url.contains("luluvdo")) {
            resolveLulu(url)
        } else if (url.contains("streamtape")) {
            resolveStreamtape(url)
        } else {
            resolveGeneric(url)
        }
    }

    private fun resolveUqload(embedUrl: String) {
        val fileCode = extractFileCode(embedUrl)
        if (fileCode == null) {
            runOnUiThread {
                Toast.makeText(this, "Impossibile estrarre il codice video", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        val body = FormBody.Builder()
            .add("op", "embed")
            .add("file_code", fileCode)
            .add("auto", "1")
            .add("referer", "https://toonitalia.xyz/")
            .build()

        val request = Request.Builder()
            .url(embedUrl)
            .post(body)
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
                val videoUrl = extractFromSources(html)
                runOnUiThread {
                    if (videoUrl != null) {
                        playVideoWithReferer(videoUrl, embedUrl)
                    } else {
                        Toast.makeText(this@PlayerActivity, "Video non disponibile. Prova con un altro player.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun resolveVoe(embedUrl: String) {
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
                val videoUrl = extractVoeVideoUrl(html)
                runOnUiThread {
                    if (videoUrl != null) {
                        playVideo(videoUrl)
                    } else {
                        Toast.makeText(this@PlayerActivity, "Video VOE non disponibile. Prova con un altro player.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun resolveVidhide(embedUrl: String) {
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
                val videoUrl = extractVidhideVideoUrl(html)
                runOnUiThread {
                    if (videoUrl != null) {
                        playVideo(videoUrl)
                    } else {
                        Toast.makeText(this@PlayerActivity, "Video non disponibile. Prova con un altro player.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun resolveLulu(embedUrl: String) {
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
                        playVideo(videoUrl)
                    } else {
                        Toast.makeText(this@PlayerActivity, "Video non disponibile. Prova con un altro player.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun resolveStreamtape(embedUrl: String) {
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
                val videoUrl = extractStreamtapeVideoUrl(html)
                runOnUiThread {
                    if (videoUrl != null) {
                        playVideo(videoUrl)
                    } else {
                        Toast.makeText(this@PlayerActivity, "Video non disponibile. Prova con un altro player.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
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
                        playVideo(videoUrl)
                    } else {
                        Toast.makeText(this@PlayerActivity, "Video non disponibile. Prova con un altro player.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun extractFileCode(url: String): String? {
        val patterns = listOf(
            Pattern.compile("/embed-([a-zA-Z0-9]+)\\."),
            Pattern.compile("/e/([a-zA-Z0-9]+)"),
            Pattern.compile("/file/([a-zA-Z0-9]+)"),
            Pattern.compile("file_code=([a-zA-Z0-9]+)")
        )
        for (p in patterns) {
            val m = p.matcher(url)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractFromSources(html: String): String? {
        val patterns = listOf(
            Pattern.compile("sources\\s*:\\s*\\[\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("sources\\s*:\\s*\\[\\s*\\{\\s*file\\s*:\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("\\{\\s*file\\s*:\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("file\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("source\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']"),
            Pattern.compile("[\"'](https?://[^\"']+\\.mp4[^\"']*)[\"']"),
            Pattern.compile("file:[\"']?(https?://[^\"'\\s,]+)")
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                val url = m.group(1)
                if (url != null && url.length > 10) return url
            }
        }
        return null
    }

    private fun extractVoeVideoUrl(html: String): String? {
        val patterns = listOf(
            Pattern.compile("file\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("\\{\\s*file\\s*:\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("source\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("sources\\s*:\\s*\\[\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("sources\\s*:\\s*\\[\\s*\\{\\s*file\\s*:\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']"),
            Pattern.compile("[\"'](https?://[^\"']+\\.mp4[^\"']*)[\"']"),
            Pattern.compile("var\\s+source\\s*=\\s*'(https?://[^']+)'")
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                val url = m.group(1)
                if (url != null && url.length > 10 && !url.contains("test-videos.co.uk")) return url
            }
        }
        return null
    }

    private fun extractVidhideVideoUrl(html: String): String? {
        val patterns = listOf(
            Pattern.compile("file\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("source\\s*:\\s*\"(https?://[^\"]+\\.(m3u8|mp4)[^\"]*)\""),
            Pattern.compile("video_url\\s*[=:]\s*[\"']?(https?://[^\"'\\s]+)"),
            Pattern.compile("videoUrl\\s*[=:]\s*[\"']?(https?://[^\"'\\s]+)"),
            Pattern.compile("sources\\s*:\\s*\\[\\s*\"(https?://[^\"]+)\""),
            Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']"),
            Pattern.compile("[\"'](https?://[^\"']+\\.mp4[^\"']*)[\"']")
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                val url = m.group(1)
                if (url != null && url.length > 10) return url
            }
        }
        return null
    }

    private fun extractStreamtapeVideoUrl(html: String): String? {
        val patterns = listOf(
            Pattern.compile("file\\s*:\\s*\"(https?://[^\"]+\\.mp4[^\"]*)\""),
            Pattern.compile("[\"'](https?://[^\"']+streamtape[^\"']+\\.mp4[^\"']*)[\"']"),
            Pattern.compile("[\"'](https?://[^\"']+\\.mp4[^\"']*)[\"']"),
            Pattern.compile("src\\s*:\\s*[\"']?(https?://[^\"'\\s]+\\.(m3u8|mp4))")
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                val url = m.group(1)
                if (url != null && url.length > 10) return url
            }
        }
        return null
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
            Pattern.compile("file:[\"']?(https?://[^\"'\\s,]+)")
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                val url = m.group(1)
                if (url != null && url.length > 10) return url
            }
        }
        return null
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

    private fun playVideoWithReferer(url: String, referer: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .build()
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
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
