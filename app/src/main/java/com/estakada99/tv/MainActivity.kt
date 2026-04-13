package com.estakada99.tv

import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.KeyEvent
import android.view.View
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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var logoView: ImageView
    private lateinit var backgroundView: SkyGradientView
    private lateinit var statusText: TextView
    private lateinit var playPauseButton: Button
    private lateinit var nowPlayingLabel: TextView
    private lateinit var nowPlayingText: TextView

    private var mediaSession: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val streamUrl = "https://live.e99.live/main"
    private val liveInfoUrl = "https://bo.e99.live/api/live-info"

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

    // Now-playing poller
    private val nowPlayingHandler = Handler(Looper.getMainLooper())
    private val nowPlayingRunnable = object : Runnable {
        override fun run() {
            fetchNowPlaying()
            nowPlayingHandler.postDelayed(this, NOW_PLAYING_POLL_MS)
        }
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
        nowPlayingLabel = findViewById(R.id.nowPlayingLabel)
        nowPlayingText = findViewById(R.id.nowPlayingText)

        logoView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Start hidden
        playPauseButton.visibility = View.INVISIBLE
        playPauseButton.alpha = 0f

        // Enable marquee scrolling for long track names
        nowPlayingText.isSelected = true

        logoView.post {
            bounceHandler.post(bounceRunnable)
        }

        playPauseButton.setOnClickListener { togglePlayback() }

        setupPlayer()
        hideSystemUI()

        // Start polling now-playing info
        nowPlayingHandler.post(nowPlayingRunnable)
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

    // ── Now Playing ──────────────────────────────────────────────

    private fun fetchNowPlaying() {
        Thread {
            try {
                val connection = URL(liveInfoUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/json")

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                connection.disconnect()

                val json = JSONObject(response)
                val result = parseLiveInfo(json)

                runOnUiThread {
                    if (result != null) {
                        nowPlayingLabel.text = result.label
                        nowPlayingText.text = result.text
                        nowPlayingLabel.visibility = View.VISIBLE
                        nowPlayingText.visibility = View.VISIBLE
                    } else {
                        nowPlayingLabel.visibility = View.GONE
                        nowPlayingText.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {
                // Silently fail — will retry on next poll
            }
        }.start()
    }

    private data class NowPlayingInfo(val label: String, val text: String)

    private fun decodeHtmlEntities(text: String): String {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            Html.fromHtml(text).toString()
        }
    }

    private fun parseLiveInfo(json: JSONObject): NowPlayingInfo? {
        val current = json.optJSONObject("current") ?: return null

        // Skip jingles — show the next track instead
        val currentBlob = buildSearchBlob(current)
        if (currentBlob.contains("JINGLE", ignoreCase = true)) {
            val next = json.optJSONObject("next")
            if (next != null) {
                val line = formatSlot(next)
                if (line != null) return NowPlayingInfo("Next up:", decodeHtmlEntities(line))
            }
            return null
        }

        val line = formatSlot(current)
        if (line != null) return NowPlayingInfo("Currently playing:", decodeHtmlEntities(line))

        // Fallback to currentShow name
        val currentShow = json.optJSONObject("currentShow")
        val showName = currentShow?.optString("name", "")?.trim()
        if (!showName.isNullOrEmpty()) {
            return NowPlayingInfo("Currently playing:", decodeHtmlEntities(showName))
        }

        return null
    }

    private fun buildSearchBlob(item: JSONObject): String {
        val bits = mutableListOf<String>()
        item.optString("name", "").let { if (it.isNotEmpty()) bits.add(it) }
        val meta = item.optJSONObject("metadata")
        if (meta != null) {
            meta.optString("track_title", "").let { if (it.isNotEmpty()) bits.add(it) }
            meta.optString("artist_name", "").let { if (it.isNotEmpty()) bits.add(it) }
        }
        return bits.joinToString(" ")
    }

    private fun formatSlot(item: JSONObject): String? {
        if (item.optString("type") == "track") {
            val meta = item.optJSONObject("metadata")
            if (meta != null) {
                val artist = meta.optString("artist_name", "").trim()
                val title = meta.optString("track_title", "").trim()
                if (artist.isNotEmpty() && title.isNotEmpty()) return "$artist \u2013 $title"
                if (title.isNotEmpty()) return title
                if (artist.isNotEmpty()) return artist
            }
        }
        val name = item.optString("name", "").trim()
            .replaceFirst(Regex("^\\s*-\\s+"), "")
            .trim()
        return name.ifEmpty { null }
    }

    // ── System UI ────────────────────────────────────────────────

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
        backgroundView.startAnimation()
        nowPlayingHandler.post(nowPlayingRunnable)
    }

    override fun onPause() {
        super.onPause()
        player.playWhenReady = false
        bounceHandler.removeCallbacks(bounceRunnable)
        hideHandler.removeCallbacks(hideRunnable)
        backgroundView.stopAnimation()
        nowPlayingHandler.removeCallbacks(nowPlayingRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
        player.release()
        bounceHandler.removeCallbacks(bounceRunnable)
        hideHandler.removeCallbacks(hideRunnable)
        nowPlayingHandler.removeCallbacks(nowPlayingRunnable)
    }

    companion object {
        private const val NOW_PLAYING_POLL_MS = 30_000L
    }
}
