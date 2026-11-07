package com.twins.videoplayer.tv

import android.app.Activity
import android.content.Context
import android.media.audiofx.BassBoost
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

class PlayerActivity : Activity() {
    private lateinit var fx: FxSettings
    private lateinit var builder: ShaderBuilder
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: FxRenderer
    private lateinit var menu: MenuView
    private lateinit var audioProc: FxAudioProcessor
    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var uris: List<Uri> = emptyList()
    private var names: List<String> = emptyList()
    private var index = 0
    private var bass: BassBoost? = null
    private var audioSig = -1
    private val handler = Handler(Looper.getMainLooper())
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    private val ticker = object : Runnable {
        override fun run() {
            if (menu.osdActive()) menu.invalidate()
            handler.postDelayed(this, 500)
        }
    }

    private val host = object : MenuView.Host {
        override fun onFxChanged() = applyFx()
        override fun cycleAudio(): String = cycleAudioTrack()
        override fun osd(): MenuView.Osd {
            val p = player
            val title = names.getOrNull(index) ?: ""
            val dur = if (p != null && p.duration > 0) p.duration else 0L
            return MenuView.Osd(title, p?.currentPosition ?: 0L, dur, p?.isPlaying ?: false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fx = FxSettings(this)
        builder = ShaderBuilder(this)
        audioProc = FxAudioProcessor(fx)

        val list = intent.getStringArrayListExtra("uris")
        uris = if (list != null) list.map { Uri.parse(it) } else listOfNotNull(intent.data)
        val nameList = intent.getStringArrayListExtra("names")
        names = if (nameList != null && nameList.size == uris.size) nameList
        else uris.map { Uri.decode(it.lastPathSegment ?: "") }
        if (uris.isEmpty()) {
            finish()
            return
        }
        index = intent.getIntExtra("index", 0).coerceIn(0, uris.size - 1)

        renderer = FxRenderer(
            onSurface = { sf -> runOnUiThread { surface = sf; player?.setVideoSurface(sf) } },
            onMessage = { m -> runOnUiThread { if (::menu.isInitialized) menu.toast(m) } }
        )
        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        glView.preserveEGLContextOnPause = true
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        menu = MenuView(this, fx, host)

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        root.addView(glView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(menu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        initPlayer()
        applyFx()
        load(index)
    }

    private fun initPlayer() {
        val ctx = this@PlayerActivity
        val rf = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(audioProc))
                    .build()
            }
        }
        rf.setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(ctx, rf).build()
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    renderer.videoW = videoSize.width
                    renderer.videoH = videoSize.height
                    renderer.pixelRatio = videoSize.pixelWidthHeightRatio
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) next(true)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupBass(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (fx.v[K.OUT51] == 1 && fx.v[K.AMODE] >= 2) {
                    fx.set(K.OUT51, 0)               // sortie 6 canaux refusée par la TV : retour au casque
                    menu.toast("Sortie 5.1 refusée par la TV : retour stéréo")
                    applyFx()
                } else {
                    menu.toast("Erreur de lecture : " + error.errorCodeName)
                    if (index + 1 < uris.size) next(true)
                }
            }
        })
        surface?.let { p.setVideoSurface(it) }
        player = p
    }

    private fun key(u: Uri): String = "p" + u.toString().hashCode()

    private fun load(i: Int) {
        val p = player ?: return
        saveProgress()
        index = i
        p.setMediaItem(MediaItem.fromUri(uris[i]))
        p.prepare()
        val t = getSharedPreferences("pos", Context.MODE_PRIVATE).getLong(key(uris[i]), 0L)
        if (t > 8000L) p.seekTo(t)
        p.playWhenReady = true
        menu.showOsd()
    }

    private fun saveProgress() {
        val p = player ?: return
        if (uris.isEmpty()) return
        val d = p.duration
        val pos = p.currentPosition
        val v = if (d > 0 && pos > d - 10000L) 0L else pos
        getSharedPreferences("pos", Context.MODE_PRIVATE).edit().putLong(key(uris[index]), v).apply()
    }

    private fun next(auto: Boolean) {
        if (index + 1 < uris.size) load(index + 1) else if (auto) finish() else load(0)
    }

    private fun prev() {
        val p = player
        if (p != null && p.currentPosition > 3000L) p.seekTo(0L) else load((index - 1 + uris.size) % uris.size)
    }

    private fun seekBy(ms: Long) {
        val p = player ?: return
        val max = if (p.duration > 0) p.duration else Long.MAX_VALUE
        p.seekTo((p.currentPosition + ms).coerceIn(0L, max))
        menu.showOsd()
    }

    private fun toggle() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
        menu.showOsd()
    }

    // ---------- effets ----------
    private fun applyFx() {
        renderer.candidates = builder.candidates(fx)
        renderer.dirty = true
        renderer.aspectMode = fx.v[K.ASPECT]
        player?.setPlaybackSpeed(speeds[fx.v[K.SPEED].coerceIn(0, speeds.size - 1)])
        applyResolution()
        applyBass()
        // le nombre de canaux de sortie ne change qu'à la reconfiguration du lecteur audio
        val sig = if (fx.v[K.AMODE] >= 2 && fx.v[K.OUT51] == 1) 1 else 0
        if (audioSig != -1 && sig != audioSig) reloadPlayback()
        audioSig = sig
    }

    private fun reloadPlayback() {
        val p = player ?: return
        if (uris.isEmpty()) return
        val pos = p.currentPosition
        val play = p.playWhenReady
        p.setMediaItem(MediaItem.fromUri(uris[index]))
        p.prepare()
        p.seekTo(pos)
        p.playWhenReady = play
    }

    private fun applyResolution() {
        val w = intArrayOf(0, 1280, 960, 640)[fx.v[K.RES].coerceIn(0, 3)]
        val holder = glView.holder
        if (w == 0) {
            holder.setSizeFromLayout()
        } else {
            val ratio = if (glView.width > 0) glView.height.toFloat() / glView.width else 9f / 16f
            holder.setFixedSize(w, (w * ratio).toInt())
        }
    }

    private fun setupBass(sessionId: Int) {
        try {
            bass?.release()
            bass = BassBoost(0, sessionId)
            applyBass()
        } catch (e: Exception) {
            bass = null
        }
    }

    private fun applyBass() {
        val b = bass ?: return
        try {
            b.setStrength((fx.v[K.BASS] * 100).toShort())
            b.setEnabled(fx.v[K.BASS] > 0)
        } catch (e: Exception) {
            // effet non pris en charge par cette TV
        }
    }

    private fun cycleAudioTrack(): String {
        val p = player ?: return ""
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (groups.size < 2) return "Une seule piste audio"
        val cur = groups.indexOfFirst { it.isSelected }
        val nxt = groups[(cur + 1) % groups.size]
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(nxt.mediaTrackGroup, 0))
            .build()
        val f = nxt.mediaTrackGroup.getFormat(0)
        return "Audio : " + (f.language ?: "?") + " " + (f.label ?: "") + " " + (f.sampleMimeType ?: "")
    }

    // ---------- télécommande ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menu.visible && menu.onKey(keyCode, event)) return true
        val step = 10000L * minOf(1 + event.repeatCount / 4, 12)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> toggle()
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.playWhenReady = true; menu.showOsd() }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.playWhenReady = false; menu.showOsd() }
            KeyEvent.KEYCODE_DPAD_LEFT -> seekBy(-step)
            KeyEvent.KEYCODE_DPAD_RIGHT -> seekBy(step)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-30000L)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(30000L)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU -> menu.show()
            KeyEvent.KEYCODE_DPAD_DOWN -> menu.showOsd()
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> next(false)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> prev()
            KeyEvent.KEYCODE_MEDIA_STOP -> finish()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---------- cycle de vie ----------
    private fun immersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onResume() {
        super.onResume()
        immersive()
        if (::glView.isInitialized) glView.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        if (::glView.isInitialized) glView.onPause()
        player?.playWhenReady = false
        saveProgress()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try {
            bass?.release()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}
