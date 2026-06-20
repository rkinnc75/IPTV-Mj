package com.iptvapp.ui.player

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.iptvapp.data.local.entities.ChannelEntity
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptvapp.ui.home.ChannelAdapter
import com.iptvapp.data.repository.XtreamRepository
import com.iptvapp.databinding.ActivityPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private val hideHandler = Handler(Looper.getMainLooper())
    private val retryHandler = Handler(Looper.getMainLooper())
    private lateinit var guideAdapter: ChannelAdapter

    private val hideRunnable = Runnable {
        binding.epgOverlay.visibility = View.GONE
        binding.btnBack.visibility = View.GONE
        binding.btnGuide.visibility = View.GONE
    }

    private var streamUrl: String = ""
    private var streamTitle: String = ""
    private var streamId: Int = -1

    // fix: bounded auto-retry so a transient live-stream failure recovers
    // instead of sitting on a black screen forever (the #1 IPTV playback defect).
    private var retryCount = 0
    private val maxRetries = 4

    private var channelJob: Job? = null

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private var resizeModeIndex = 0 // default FIT (ZOOM cropped 4:3 SD channels)

    @Inject
    lateinit var repository: XtreamRepository

    private var channels: List<ChannelEntity> = emptyList()
    private var currentIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Belt-and-braces for the layout's keepScreenOn: never let a long live
        // watch dim/sleep the screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setupFavoritesGuide()
        setupResizeButton()
        setupChannelZones()

        streamUrl = intent.getStringExtra("stream_url") ?: ""
        streamTitle = intent.getStringExtra("stream_title") ?: ""
        streamId = intent.getIntExtra("stream_id", -1)

        binding.tvChannelTitle.text = streamTitle
        binding.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            channels = repository.getAllChannels().first()
            currentIndex = channels.indexOfFirst { it.streamId == streamId }
        }
    }

    private fun setupChannelZones() {
        binding.zonePrevious.setOnClickListener {
            if (binding.guideContainer.visibility == View.VISIBLE) return@setOnClickListener
            if (binding.epgOverlay.visibility == View.VISIBLE) {
                previousChannel()
            } else {
                showOverlay()
            }
        }
        binding.zoneNext.setOnClickListener {
            if (binding.guideContainer.visibility == View.VISIBLE) return@setOnClickListener
            if (binding.epgOverlay.visibility == View.VISIBLE) {
                nextChannel()
            } else {
                showOverlay()
            }
        }
    }

    private fun setupResizeButton() {
        binding.btnResize.setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
            binding.playerView.resizeMode = resizeModes[resizeModeIndex]
            resetHideTimer()
        }
    }

    private fun initPlayer() {
        if (player != null) return
        val loadControl = DefaultLoadControl.Builder()
            // Live IPTV: smaller buffers than the old VOD-tuned 50s/120s, so
            // startup and post-reconnect latency are reasonable.
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // HTTP timeouts so a hung server eventually errors into onPlayerError
        // (→ retry) instead of buffering forever.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer
                binding.playerView.resizeMode = resizeModes[resizeModeIndex]
                binding.playerView.setOnClickListener {
                    if (binding.epgOverlay.visibility == View.VISIBLE) {
                        hideHandler.removeCallbacks(hideRunnable)
                        hideRunnable.run()
                    } else {
                        showOverlay()
                    }
                }

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                retryCount = 0
                                binding.progressBuffering.visibility = View.GONE
                                binding.tvError.visibility = View.GONE
                                showOverlay()
                            }
                            Player.STATE_BUFFERING -> {
                                binding.progressBuffering.visibility = View.VISIBLE
                                binding.epgOverlay.visibility = View.GONE
                                binding.btnBack.visibility = View.GONE
                                binding.btnGuide.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                // A live stream should not legitimately end; the
                                // server likely dropped the connection — retry.
                                retryPlayback()
                            }
                            else -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        retryPlayback(error)
                    }
                })

                if (streamUrl.isNotEmpty()) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                    exoPlayer.playWhenReady = true
                    exoPlayer.prepare()
                }
            }
    }

    /** Bounded auto-retry with linear backoff; surfaces an error after the cap. */
    private fun retryPlayback(error: PlaybackException? = null) {
        val p = player ?: return
        if (retryCount >= maxRetries) {
            binding.progressBuffering.visibility = View.GONE
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Stream unavailable. Press back, or up/down to change channel."
            return
        }
        retryCount++
        binding.progressBuffering.visibility = View.VISIBLE
        retryHandler.removeCallbacksAndMessages(null)
        retryHandler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            p.stop()
            p.clearMediaItems()
            if (streamUrl.isNotEmpty()) {
                p.setMediaItem(MediaItem.fromUri(streamUrl))
                p.playWhenReady = true
                p.prepare()
            }
        }, 1500L * retryCount)
    }

    private fun showOverlay() {
        binding.tvChannelTitle.text = streamTitle
        binding.epgOverlay.visibility = View.VISIBLE
        binding.btnBack.visibility = View.VISIBLE
        binding.btnGuide.visibility = View.VISIBLE
        resetHideTimer()
        if (streamId != -1) {
            lifecycleScope.launch {
                repository.fetchEpg(streamId)
                val epg = repository.getEpgForStream(streamId).first()
                val now = epg.firstOrNull()
                val next = epg.drop(1).firstOrNull()
                binding.tvEpgNow.text = if (now != null) "NOW: " + now.title else ""
                binding.tvEpgNext.text = if (next != null) "NEXT: " + next.title else ""
            }
        }
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    private fun playChannel(channel: ChannelEntity) {
        // Cancel any in-flight switch so rapid channel surfing doesn't race
        // (last click wins, not last-resolved coroutine).
        channelJob?.cancel()
        retryHandler.removeCallbacksAndMessages(null)
        retryCount = 0
        binding.tvError.visibility = View.GONE
        channelJob = lifecycleScope.launch {
            val url = repository.getLiveStreamUrl(channel.streamId)
            streamId = channel.streamId
            streamTitle = channel.name
            streamUrl = url
            binding.tvChannelTitle.text = streamTitle
            val idx = channels.indexOfFirst { it.streamId == channel.streamId }
            if (idx >= 0) currentIndex = idx
            player?.apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                prepare()
            }
        }
    }

    private fun nextChannel() {
        if (channels.isEmpty() || currentIndex < 0) return
        currentIndex++
        if (currentIndex >= channels.size) currentIndex = 0
        playChannel(channels[currentIndex])
    }

    private fun previousChannel() {
        if (channels.isEmpty() || currentIndex < 0) return
        currentIndex--
        if (currentIndex < 0) currentIndex = channels.lastIndex
        playChannel(channels[currentIndex])
    }

    // fix: Android TV D-pad / remote support. The screen has no focusable
    // transport, so map the remote keys directly. When the guide drawer is open,
    // let the RecyclerView consume D-pad keys for its own navigation.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (binding.guideContainer.visibility == View.VISIBLE) {
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                previousChannel(); showOverlay(); return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                nextChannel(); showOverlay(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (binding.epgOverlay.visibility == View.VISIBLE) {
                    toggleFavoritesGuide()
                } else {
                    showOverlay()
                }
                return true
            }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> {
                toggleFavoritesGuide(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        window.decorView.post {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupFavoritesGuide() {
        guideAdapter = ChannelAdapter(
            onChannelClick = { channel ->
                binding.guideContainer.visibility = View.GONE
                playChannel(channel)
            },
            onFavoriteClick = { }
        )
        binding.rvFavoritesGuide.layoutManager = LinearLayoutManager(this)
        binding.rvFavoritesGuide.adapter = guideAdapter
        binding.btnGuide.setOnClickListener { toggleFavoritesGuide() }
        binding.btnCloseGuide.setOnClickListener {
            binding.guideContainer.visibility = View.GONE
        }
    }

    private fun toggleFavoritesGuide() {
        if (binding.guideContainer.visibility == View.VISIBLE) {
            binding.guideContainer.visibility = View.GONE
            return
        }
        hideHandler.removeCallbacks(hideRunnable)
        lifecycleScope.launch {
            val favs = repository.getFavoriteChannels().first()
            guideAdapter.submitList(favs)
            val ids = favs.map { it.streamId }
            if (ids.isNotEmpty()) {
                val epg = repository.getEpgForStreams(ids).first().groupBy { it.streamId }
                val textMap = favs.associate { ch ->
                    val now = epg[ch.streamId].orEmpty().firstOrNull()
                    val next = epg[ch.streamId].orEmpty().drop(1).firstOrNull()
                    val t = when {
                        now != null && next != null -> "NOW: " + now.title + "   NEXT: " + next.title
                        now != null -> "NOW: " + now.title
                        else -> ""
                    }
                    ch.streamId to t
                }
                guideAdapter.submitEpgText(textMap)
            }
            binding.guideContainer.visibility = View.VISIBLE
            binding.rvFavoritesGuide.requestFocus()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // fix: Media3 lifecycle for API 24+. Acquire the codec/socket in onStart and
    // release in onStop, so a backgrounded player doesn't hold the decoder and
    // keep downloading the live stream. (onStop can run without onDestroy.)
    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun releasePlayer() {
        retryHandler.removeCallbacksAndMessages(null)
        channelJob?.cancel()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacksAndMessages(null)
        retryHandler.removeCallbacksAndMessages(null)
        releasePlayer()
    }
}
