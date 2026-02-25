package com.estakada99.tv

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var logoView: ImageView
    private lateinit var backgroundView: ImageView
    private lateinit var statusText: TextView
    private lateinit var playPauseButton: TextView

    private val streamUrl = "https://live.e99.live/main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setContentView(R.layout.activity_main)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logoView = findViewById(R.id.logoView)
        backgroundView = findViewById(R.id.backgroundView)
        statusText = findViewById(R.id.statusText)
        playPauseButton = findViewById(R.id.playPauseButton)

        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        backgroundView.startAnimation(pulseAnim)

        setupPlayer()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()

        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        player.setMediaItem(mediaItem)

        player.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        statusText.text = "Buffering..."
                        statusText.visibility = View.VISIBLE
                        playPauseButton.text = "⏳"
                    }
                    Player.STATE_READY -> {
                        statusText.visibility = View.GONE
                        updatePlayPauseButton()
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        statusText.text = "Press OK to connect"
                        statusText.visibility = View.VISIBLE
                        playPauseButton.text = "▶"
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton()
            }

            override fun onPlayerError(error: PlaybackException) {
                statusText.text = "Stream unavailable. Press OK to retry."
                statusText.visibility = View.VISIBLE
                playPauseButton.text = "▶"
            }
        })

        player.prepare()
        player.playWhenReady = true
    }

    private fun updatePlayPauseButton() {
        playPauseButton.text = if (player.isPlaying) "⏸" else "▶"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_IDLE ||
                        player.playbackState == Player.STATE_ENDED) {
                        player.prepare()
                    }
                    player.play()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        player.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}