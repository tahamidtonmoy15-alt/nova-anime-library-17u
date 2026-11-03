package com.nova.anime

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/** Full-screen NOVA player. LibVLC handles broad container/codec support; software decoding is retried automatically. */
class PlayerActivity : AppCompatActivity() {
    private var libVlc: LibVLC? = null
    private var vlc: MediaPlayer? = null
    private var exo: ExoPlayer? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var exoView: PlayerView
    private lateinit var status: TextView
    private lateinit var controls: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var playBtn: Button
    private var mediaUri: Uri = Uri.EMPTY
    private var title = "NOVA Player"
    private var usingExo = false
    private var softwareAttempted = false
    private var switchingEngine = false
    private var userSeeking = false
    private var speedIdx = 2
    private var ratioIdx = 0
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val ratios = arrayOf("Fit", "16:9", "4:3", "Fill")
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); ui.postDelayed(this, 500) }
    }
    private val hideControls = Runnable { if (::controls.isInitialized) controls.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        mediaUri = when {
            !path.isNullOrBlank() -> Uri.fromFile(File(path))
            intent.data != null -> intent.data!!
            else -> Uri.EMPTY
        }
        title = intent.getStringExtra(EXTRA_TITLE) ?: (mediaUri.lastPathSegment ?: "NOVA Player")
        if (mediaUri == Uri.EMPTY || (mediaUri.scheme == "file" && !File(mediaUri.path ?: "").isFile)) {
            Toast.makeText(this, "ভিডিও ফাইলটি পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            finish(); return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        buildUi()
        videoLayout.post { startVlc(softwareOnly = false) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun makeButton(label: String, onClick: (Button) -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0x332A1B3F); minWidth = 0; minimumWidth = 0
        setPadding(dp(7), 0, dp(7), 0)
        setOnClickListener { onClick(this); showControls() }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        videoLayout = VLCVideoLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        exoView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            visibility = View.GONE
            setShutterBackgroundColor(0xFF000000.toInt())
        }
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))
        root.addView(exoView, FrameLayout.LayoutParams(-1, -1))

        status = TextView(this).apply {
            text = title; setTextColor(-1); textSize = 14f
            setPadding(dp(14), dp(24), dp(14), dp(10)); setBackgroundColor(0x66000000)
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xB3000000.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(14))
        }
        val seekRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        seek = SeekBar(this).apply { max = 1000 }
        timeText = TextView(this).apply { text = "00:00 / 00:00"; setTextColor(-1); textSize = 12f; setPadding(dp(8), 0, dp(4), 0) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { val d = duration(); if (d > 0) timeText.text = "${fmt(d * progress / 1000)} / ${fmt(d)}" }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                val d = duration(); if (d > 0) seekTo(d * (bar?.progress ?: 0) / 1000)
                userSeeking = false
            }
        })
        seekRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        seekRow.addView(timeText)

        val actionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        playBtn = makeButton("Ⅱ") { togglePlay() }
        actionRow.addView(makeButton("−10s") { seekTo(position() - 10_000) })
        actionRow.addView(playBtn)
        actionRow.addView(makeButton("+10s") { seekTo(position() + 10_000) })
        actionRow.addView(makeButton("1×") { cycleSpeed(it) })
        actionRow.addView(makeButton("Audio") { pickTrack(audio = true) })
        actionRow.addView(makeButton("Sub") { pickTrack(audio = false) })
        actionRow.addView(makeButton("Fit") { cycleRatio(it) })
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actionRow) }
        controls.addView(seekRow)
        controls.addView(scroll, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        videoLayout.setOnClickListener { toggleControlsVisibility() }
        exoView.setOnClickListener { toggleControlsVisibility() }
        status.setOnClickListener { toggleControlsVisibility() }
        setContentView(root)
        showControls()
    }

    private fun toggleControlsVisibility() {
        if (controls.visibility == View.VISIBLE) {
            controls.visibility = View.GONE
            status.visibility = View.GONE
            ui.removeCallbacks(hideControls)
        } else {
            status.visibility = View.VISIBLE
            showControls()
        }
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        status.visibility = View.VISIBLE
        ui.removeCallbacks(hideControls)
        ui.postDelayed(hideControls, 4500)
    }

    private fun startVlc(softwareOnly: Boolean) {
        if (isFinishing || isDestroyed || usingExo) return
        try {
            releaseVlc()
            val options = arrayListOf("--audio-time-stretch", "--file-caching=2000", "--network-caching=2000", "--no-video-title-show")
            if (softwareOnly) options.add("--avcodec-hw=none")
            val engine = LibVLC(this, options)
            libVlc = engine
            val player = MediaPlayer(engine)
            vlc = player
            player.setEventListener { event ->
                runOnUiThread {
                    if (switchingEngine || isFinishing) return@runOnUiThread
                    when (event.type) {
                        MediaPlayer.Event.Opening -> status.text = "$title · Opening…"
                        MediaPlayer.Event.Buffering -> status.text = "$title · Buffering…"
                        MediaPlayer.Event.Playing -> status.text = title
                        MediaPlayer.Event.Paused -> status.text = "$title · Paused"
                        MediaPlayer.Event.Stopped -> if (!isFinishing) status.text = "$title · Stopped"
                        MediaPlayer.Event.EndReached -> status.text = "$title · Episode finished"
                        MediaPlayer.Event.EncounteredError -> {
                            if (!softwareAttempted) {
                                softwareAttempted = true
                                retryWithSoftwareDecoder()
                            } else startExoFallback()
                        }
                    }
                }
            }
            player.attachViews(videoLayout, null, false, false)
            val media = Media(engine, mediaUri)
            media.setHWDecoderEnabled(!softwareOnly, false)
            media.addOption(":file-caching=2000")
            player.media = media
            media.release()
            player.setVolume(100)
            player.play()
            status.text = if (softwareOnly) "$title · Software decoding" else "$title · Opening…"
            ui.removeCallbacks(tick); ui.post(tick)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "LibVLC start failed (software=$softwareOnly)", e)
            if (!softwareAttempted) { softwareAttempted = true; retryWithSoftwareDecoder() }
            else startExoFallback()
        }
    }

    private fun retryWithSoftwareDecoder() {
        if (switchingEngine || isFinishing) return
        switchingEngine = true
        status.text = "$title · Retrying with software decoder…"
        ui.postDelayed({
            switchingEngine = false
            if (!isFinishing) startVlc(softwareOnly = true)
        }, 250)
    }

    private fun startExoFallback() {
        if (usingExo || isFinishing) return
        usingExo = true
        releaseVlc()
        videoLayout.visibility = View.GONE
        exoView.visibility = View.VISIBLE
        status.text = "$title · Compatibility player"
        try {
            exo = ExoPlayer.Builder(this).build().also { player ->
                exoView.player = player
                player.setMediaItem(MediaItem.fromUri(mediaUri))
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        status.text = "এই ফাইলটি NOVA-র কোনো ডিকোডার চালাতে পারেনি · ${error.errorCodeName}"
                        Toast.makeText(this@PlayerActivity, "ভিডিওর কোডেক অসমর্থিত অথবা ফাইলটি ক্ষতিগ্রস্ত। অন্য কপি বা MP4 (H.264/AAC) ব্যবহার করো।", Toast.LENGTH_LONG).show()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) status.text = title
                    }
                })
                player.prepare()
                player.playWhenReady = true
            }
            ui.removeCallbacks(tick); ui.post(tick)
        } catch (e: Throwable) {
            status.text = "Player start failed: ${e.message}"
            Toast.makeText(this, "Player চালু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun releaseVlc() {
        val player = vlc
        vlc = null
        try { player?.stop() } catch (_: Throwable) { }
        try { player?.detachViews() } catch (_: Throwable) { }
        try { player?.release() } catch (_: Throwable) { }
        val engine = libVlc
        libVlc = null
        try { engine?.release() } catch (_: Throwable) { }
    }

    private fun duration(): Long = if (usingExo) (exo?.duration ?: 0L).coerceAtLeast(0L) else (vlc?.length ?: 0L).coerceAtLeast(0L)
    private fun position(): Long = if (usingExo) (exo?.currentPosition ?: 0L).coerceAtLeast(0L) else (vlc?.time ?: 0L).coerceAtLeast(0L)
    private fun isPlaying(): Boolean = if (usingExo) (exo?.isPlaying ?: false) else (vlc?.isPlaying ?: false)

    private fun seekTo(ms: Long) {
        val d = duration()
        val target = ms.coerceAtLeast(0L).let { if (d > 0L) it.coerceAtMost(d) else it }
        if (usingExo) exo?.seekTo(target) else vlc?.time = target
    }

    private fun togglePlay() {
        if (usingExo) exo?.let { if (it.isPlaying) it.pause() else it.play() }
        else vlc?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun cycleSpeed(button: Button) {
        speedIdx = (speedIdx + 1) % speeds.size
        val speed = speeds[speedIdx]
        if (usingExo) exo?.setPlaybackSpeed(speed) else vlc?.rate = speed
        button.text = "$speed×"
    }

    private fun cycleRatio(button: Button) {
        ratioIdx = (ratioIdx + 1) % ratios.size
        button.text = ratios[ratioIdx]
        if (usingExo) {
            exoView.resizeMode = when (ratioIdx) {
                3 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        } else vlc?.let { player ->
            when (ratioIdx) {
                0 -> { player.setAspectRatio(null); player.setScale(0f) }
                1 -> { player.setAspectRatio("16:9"); player.setScale(0f) }
                2 -> { player.setAspectRatio("4:3"); player.setScale(0f) }
                3 -> { player.setAspectRatio(null); player.setScale(1f) }
            }
        }
    }

    private fun pickTrack(audio: Boolean) {
        if (usingExo) {
            Toast.makeText(this, "Compatibility player-এ আলাদা ট্র্যাক নির্বাচন সীমিত", Toast.LENGTH_SHORT).show()
            return
        }
        val player = vlc ?: return
        val tracks = (if (audio) player.audioTracks else player.spuTracks)?.toList().orEmpty()
        if (tracks.isEmpty()) {
            Toast.makeText(this, if (audio) "এই ভিডিওতে আলাদা Audio track নেই" else "এই ভিডিওতে Subtitle track নেই", Toast.LENGTH_SHORT).show()
            return
        }
        val names = tracks.map { it.name?.takeIf(String::isNotBlank) ?: "Track ${it.id}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (audio) "Audio track" else "Subtitle track")
            .setItems(names) { _, index ->
                if (audio) player.setAudioTrack(tracks[index].id) else player.setSpuTrack(tracks[index].id)
            }
            .show()
    }

    private fun refresh() {
        if (!::playBtn.isInitialized) return
        playBtn.text = if (isPlaying()) "Ⅱ" else "▶"
        val d = duration()
        if (!userSeeking) {
            timeText.text = "${fmt(position())} / ${fmt(d)}"
            if (d > 0L) seek.progress = (position() * 1000L / d).coerceIn(0L, 1000L).toInt()
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        return if (s >= 3600L) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onPause() {
        if (usingExo) exo?.pause() else vlc?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        try { exoView.player = null } catch (_: Exception) { }
        try { exo?.release() } catch (_: Throwable) { }
        exo = null
        releaseVlc()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        private const val TAG = "NOVA.Player"
    }
}
