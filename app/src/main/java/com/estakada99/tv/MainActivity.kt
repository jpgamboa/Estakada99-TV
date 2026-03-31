package com.estakada99.tv

import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var logoView: ImageView
    private lateinit var backgroundView: ImageView
    private lateinit var statusText: TextView
    private lateinit var playPauseButton: Button

    private var mediaSession: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val streamUrl = "https://live.e99.live/main"

    // DVD bounce
    private val bounceHandler = Handler(Looper.getMainLooper())
    private var logoX = 100f
    private var logoY = 100f
    private var dx = 2f
    private var dy = 1.5f

    private val bounceRunnable = object : Runnable {
        override fun run() {
            val parent = logoView.parent as View
            val maxX = parent.width - logoView.width.toFloat()
            val maxY = parent.height - logoView.height.toFloat()

            if (maxX <= 0 || maxY <= 0) {
                bounceHandler.postDelayed(this, 16)
                return
            }

            logoX += dx
            logoY += dy

            if (logoX <= 0f) { logoX = 0f; dx = Math.abs(dx) }
            if (logoX >= maxX) { logoX = maxX; dx = -Math.abs(dx) }
            if (logoY <= 0f) { logoY = 0f; dy = Math.abs(dy) }
            if (logoY >= maxY) { logoY = maxY; dy = -Math.abs(dy) }

            // Use translationX/Y for smoother rendering
            logoView.translationX = logoX
            logoView.translationY = logoY

            bounceHandler.postDelayed(this, 16)
        }
    }

    // Button auto-hide
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        playPauseButton.animate()
            .alpha(0f)
            .setDuration(500)
            .withEndAction { playPauseButton.visibility = View.INVISIBLE }
            .start()
    }

    private fun showButton() {
        hideHandler.removeCallbacks(hideRunnable)
        playPauseButton.visibility = View.VISIBLE
        playPauseButton.animate().alpha(1f).setDuration(300).start()
        hideHandler.postDelayed(hideRunnable, 15000)
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                player.playWhenReady = true
                player.volume = 1f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player.volume = 0.3f
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AndroidAudioAttributes.Builder()
                        .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                        .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        logoView = findViewById(R.id.logoView)
        backgroundView = findViewById(R.id.backgroundView)
        statusText = findViewById(R.id.statusText)
        playPauseButton = findViewById(R.id.playPauseButton)

        logoView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Start hidden
        playPauseButton.visibility = View.INVISIBLE
        playPauseButton.alpha = 0f

        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        backgroundView.startAnimation(pulseAnim)

        logoView.post {
            bounceHandler.post(bounceRunnable)
        }

        playPauseButton.setOnClickListener { togglePlayback() }

        setupPlayer()
        hideSystemUI()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()

        mediaSession = MediaSession.Builder(this, player).build()
        requestAudioFocus()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Estakada 99")
                    .setArtist("Live Radio")
                    .build()
            )
            .build()
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

    private fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE ||
                player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.play()
        }
    }

    private fun updatePlayPauseButton() {
        playPauseButton.text = if (player.isPlaying) "⏸" else "▶"
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (playPauseButton.visibility == View.VISIBLE) {
                    togglePlayback()
                } else {
                    showButton()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayback()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> {
                showButton()
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        player.playWhenReady = true
        bounceHandler.post(bounceRunnable)
    }

    override fun onPause() {
        super.onPause()
        player.playWhenReady = false
        bounceHandler.removeCallbacks(bounceRunnable)
        hideHandler.removeCallbacks(hideRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
        player.release()
        bounceHandler.removeCallbacks(bounceRunnable)
        hideHandler.removeCallbacks(hideRunnable)
    }
}
