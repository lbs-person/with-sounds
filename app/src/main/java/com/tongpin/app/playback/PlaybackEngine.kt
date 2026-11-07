package com.tongpin.app.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.tongpin.app.data.Playback
import com.tongpin.app.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class AudioState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val speed: Float = 1f,
    val error: String? = null,
)

/**
 * Owns one ExoPlayer and keeps it inside a server-authoritative timeline.
 *
 * Every device (including the host) plays the same HTTP stream from the relay.
 * The server supplies `startAt` in server epoch milliseconds; the session
 * supplies a clock that has been offset-corrected with WebSocket ping samples.
 */
class PlaybackEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val serverNow: () -> Long,
    private val onEnded: () -> Unit,
) : Player.Listener {

    private val player = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                // Start quickly so the shared startAt is actually reachable.
                .setBufferDurationsMs(1_500, 15_000, 500, 700)
                .build()
        )
        .build()
        .apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        setHandleAudioBecomingNoisy(true)
        setWakeMode(C.WAKE_MODE_NETWORK)
        addListener(this@PlaybackEngine)
    }

    private val _state = MutableStateFlow(AudioState())
    val state = _state.asStateFlow()

    private var remote: Playback = Playback()
    private var track: Track? = null
    private var loadedUrl: String? = null
    private var scheduleJob: Job? = null
    private var endedNotified = false
    private var released = false

    private val ticker = scope.launch {
        while (isActive && !released) {
            publish()
            if (remote.playing && player.isPlaying && player.playbackState != Player.STATE_BUFFERING) {
                correctDrift()
            }
            checkEnd()
            delay(250)
        }
    }

    init {
        publish()
    }

    fun setTrack(newTrack: Track?) {
        if (newTrack == null) {
            track = null
            loadedUrl = null
            scheduleJob?.cancel()
            player.stop()
            player.clearMediaItems()
            publish()
            return
        }
        val url = newTrack.audioUrl
        if (url == loadedUrl) {
            track = newTrack
            return
        }
        track = newTrack
        loadedUrl = url
        scheduleJob?.cancel()
        endedNotified = false
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = false
        publish()
    }

    fun applyRemote(playback: Playback) {
        remote = playback
        scheduleJob?.cancel()
        val itemDuration = track?.durationMs ?: 0L
        val position = playback.positionMs.coerceAtLeast(0)
        if (!playback.playing) {
            player.pause()
            player.setPlaybackSpeed(1f)
            if (abs(player.currentPosition - position) > 180) {
                player.seekTo(position)
            }
            publish()
            return
        }

        endedNotified = false
        val now = serverNow()
        if (playback.startAt > now + 80) {
            player.pause()
            player.setPlaybackSpeed(1f)
            player.seekTo(position.coerceAtMost(itemDuration.takeIf { it > 0 } ?: position))
            scheduleJob = scope.launch {
                val wait = max(0, playback.startAt - serverNow() - 120)
                delay(wait)
                if (released) return@launch
                val target = playback.positionMs + max(0, serverNow() - playback.startAt)
                player.seekTo(target.coerceAtLeast(0))
                player.play()
            }
        } else {
            val target = playback.positionMs + max(0, now - playback.startAt)
            if (abs(player.currentPosition - target) > 180) {
                player.seekTo(target)
            }
            player.play()
        }
        publish()
    }

    private fun correctDrift() {
        val playback = remote
        if (!playback.playing || playback.startAt <= 0) return
        val duration = player.duration.takeIf { it > 0 }
            ?: track?.durationMs?.takeIf { it > 0 }
            ?: return
        val target = (playback.positionMs + (serverNow() - playback.startAt))
            .coerceIn(0, duration)
        val actual = player.currentPosition
        val drift = actual - target
        if (abs(drift) > 900) {
            player.seekTo((target + 60).coerceAtMost(duration))
            player.setPlaybackSpeed(1f)
        } else if (abs(drift) > 140) {
            val speed = (1f - (drift / 9000f)).coerceIn(0.96f, 1.04f)
            if (abs(player.playbackParameters.speed - speed) > 0.004f) {
                player.setPlaybackSpeed(speed)
            }
        } else if (player.playbackParameters.speed != 1f) {
            player.setPlaybackSpeed(1f)
        }
    }

    private fun checkEnd() {
        if (endedNotified) return
        if (player.playbackState == Player.STATE_ENDED) {
            endedNotified = true
            player.pause()
            onEnded()
        }
    }

    fun currentPosition(): Long = player.currentPosition.coerceAtLeast(0)

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun publish() {
        if (released) return
        _state.value = AudioState(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            speed = player.playbackParameters.speed,
            error = null,
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        publish()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        publish()
    }

    override fun onPlayerError(error: PlaybackException) {
        _state.value = _state.value.copy(
            playing = false,
            buffering = false,
            error = "播放失败：${error.errorCodeName}",
        )
    }

    fun release() {
        released = true
        scheduleJob?.cancel()
        ticker.cancel()
        player.removeListener(this)
        player.release()
    }

    private companion object {
        const val DEFAULT_BUFFER = 1L
    }
}
