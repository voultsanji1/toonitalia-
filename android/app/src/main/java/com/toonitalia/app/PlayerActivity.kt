package com.toonitalia.app

import android.content.Intent
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
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.regex.Pattern

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        playerView = PlayerView(this)
        setContentView(playerView)

        player = ExoPlayer.Builder(this).build()
        playerView?.player = player

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

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
            .build()

        NetworkModule.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val html = try {
                    response.body?.string() ?: ""
                } finally {
                    response.close()
                }

                val patterns = listOf(
                    Pattern.compile("file:\\s*\"(https?://[^\"]+\\.m3u8[^\"]*)\""),
                    Pattern.compile("file:\\s*\"(https?://[^\"]+\\.(mp4|mkv|avi)[^\"]*)\""),
                    Pattern.compile("src:\\s*\"(https?://[^\"]+\\.(mp4|m3u8)[^\"]*)\""),
                    Pattern.compile("(https?://[^\"]+\\.m3u8[^\"]*)"),
                    Pattern.compile("(https?://[^\"]+\\.(mp4|mkv)[^\"]*)"),
                    Pattern.compile("source.*?\"(https?://[^\"]+)\""),
                    Pattern.compile("file.*?=.*?\"(https?://[^\"]+)\""),
                )

                var videoUrl: String? = null
                for (pattern in patterns) {
                    val matcher = pattern.matcher(html)
                    if (matcher.find()) {
                        videoUrl = matcher.group(1)
                        break
                    }
                }

                runOnUiThread {
                    if (videoUrl != null) {
                        playVideo(videoUrl)
                    } else {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Impossibile trovare il video. Prova con un altro player.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
    }

    private fun playVideo(url: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
        
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                runOnUiThread {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Errore riproduzione: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

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
