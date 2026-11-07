package com.tongpin.app.data

import android.content.Context
import android.os.SystemClock
import com.tongpin.app.playback.AudioState
import com.tongpin.app.playback.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

sealed interface SessionEvent {
    data class Toast(val message: String) : SessionEvent
    data class Ended(val reason: String?) : SessionEvent
    data class HostLeft(val name: String) : SessionEvent
    data class Reaction(val emoji: String, val name: String) : SessionEvent
}

class RoomSession(
    context: Context,
    private val api: RelayApi,
    private val repository: MusicRepository,
    private val serverUrl: String,
    private val code: String,
    private val nickname: String,
    private val isHost: Boolean,
    private val hostToken: String?,
    initialTrack: Track?,
    initialRoom: JSONObject? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(
        SessionUi(
            code = code,
            serverUrl = serverUrl,
            isHost = isHost,
            hostName = nickname,
            track = initialTrack,
            connection = LinkState.Connecting,
            upload = if (isHost) UploadState(0f) else null,
        )
    )
    val ui = _ui.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 24)
    val events = _events.asSharedFlow()

    private val _audio = MutableStateFlow(AudioState())
    val audio = _audio.asStateFlow()

    private val engine = PlaybackEngine(
        context = context,
        scope = scope,
        serverNow = { serverNow() },
        onEnded = { onTrackEnded() },
    )

    private val incoming = Channel<JSONObject>(Channel.UNLIMITED)
    private val pendingTimes = mutableMapOf<Long, Long>()
    private val pendingTimeLock = Any()
    private val clockOffset = AtomicLong(0L)

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var timeJob: Job? = null
    private var reconnectAttempt = 0
    private var leaving = false
    private var clockApplied = false
    private var bestRtt = Long.MAX_VALUE
    private var timeSamples = 0

    init {
        scope.launch {
            for (message in incoming) {
                runCatching { handleEvent(message) }
                    .onFailure { _events.tryEmit(SessionEvent.Toast("房间消息处理失败：${it.message}")) }
            }
        }
        scope.launch {
            engine.state.collect { _audio.value = it }
        }
        initialRoom?.let { applyRoomSnapshot(it, fromWelcome = false) }
        initialTrack?.let {
            _ui.update { current -> current.copy(track = it) }
            engine.setTrack(it)
        }
        connect()
    }

    private fun connect() {
        if (leaving) return
        _ui.update {
            it.copy(
                connection = if (reconnectAttempt > 0) LinkState.Reconnecting else LinkState.Connecting,
                error = null,
            )
        }
        val ws = api.openSocket(
            baseUrl = serverUrl,
            code = code,
            name = nickname,
            role = if (isHost) "host" else "guest",
            hostToken = hostToken,
            listener = listener,
        )
        socket = ws
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch {
                if (webSocket !== socket) return@launch
                reconnectAttempt = 0
                _ui.update { it.copy(connection = LinkState.Connected) }
                startClockSync()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            incoming.trySend(json)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { socketLost(webSocket, null) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scope.launch { socketLost(webSocket, response) }
        }
    }

    private suspend fun socketLost(webSocket: WebSocket, response: Response?) {
        if (leaving || webSocket !== socket) return
        socket = null
        timeJob?.cancel()
        val httpCode = response?.code
        if (httpCode == 404) {
            _events.emit(SessionEvent.Ended("房间不存在或已经结束"))
            _ui.update { it.copy(connection = LinkState.Closed, error = "房间不存在或已经结束") }
            leaving = true
            engine.pause()
            disposeSoon()
            return
        }
        if (httpCode == 403) {
            _events.emit(SessionEvent.Ended("房主身份已失效，请重新创建房间"))
            _ui.update { it.copy(connection = LinkState.Closed, error = "房主身份已失效") }
            leaving = true
            disposeSoon()
            return
        }
        _ui.update { it.copy(connection = LinkState.Reconnecting) }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (leaving) return
        if (reconnectJob?.isActive == true) return
        reconnectAttempt++
        val delayMs = min(10_000L, 800L * (1L shl min(reconnectAttempt - 1, 4)))
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!leaving) connect()
        }
    }

    private fun startClockSync() {
        timeJob?.cancel()
        clockApplied = false
        timeSamples = 0
        bestRtt = Long.MAX_VALUE
        synchronized(pendingTimeLock) { pendingTimes.clear() }
        timeJob = scope.launch {
            repeat(6) { index ->
                if (!isActive || leaving) return@launch
                val t0 = System.currentTimeMillis()
                synchronized(pendingTimeLock) { pendingTimes[t0] = SystemClock.elapsedRealtime() }
                sendRaw(JSONObject().put("type", "time").put("t0", t0))
                delay(if (index == 0) 80 else 160)
            }
        }
    }

    private suspend fun handleEvent(json: JSONObject) {
        when (json.optString("type")) {
            "welcome" -> {
                val selfId = json.optString("selfId").takeIf { it.isNotBlank() }
                val room = json.optJSONObject("room")
                _ui.update {
                    it.copy(
                        selfId = selfId ?: it.selfId,
                        connection = LinkState.Connected,
                        error = null,
                    )
                }
                if (room != null) applyRoomSnapshot(room, fromWelcome = true)
                json.optJSONArray("chats")?.let { chats ->
                    val parsed = parseChats(chats)
                    _ui.update { current ->
                        current.copy(chats = mergeChats(current.chats, parsed))
                    }
                }
                _ui.update { it.copy(upload = null) }
                // The initial playback may have arrived before the clock was
                // corrected; re-apply the schedule for the first good sample.
                if (clockApplied) engine.applyRemote(_ui.value.playback)
            }

            "members" -> {
                val members = Member.listFromJson(json.optJSONArray("members"))
                _ui.update {
                    it.copy(
                        members = members,
                        hostId = json.stringOrNull("hostId") ?: it.hostId,
                        hostName = json.optString("hostName", it.hostName),
                    )
                }
            }

            "track" -> {
                val track = json.optJSONObject("track")?.let { Track.fromJson(it, serverUrl) }
                _ui.update { it.copy(track = track) }
                engine.setTrack(track)
            }

            "playback" -> {
                val playback = Playback(
                    playing = json.optBoolean("playing"),
                    positionMs = json.optLong("positionMs").coerceAtLeast(0),
                    startAt = json.optLong("startAt"),
                )
                _ui.update { it.copy(playback = playback) }
                engine.applyRemote(playback)
            }

            "host" -> {
                _ui.update {
                    it.copy(
                        hostId = json.stringOrNull("hostId"),
                        hostName = json.optString("hostName", it.hostName),
                    )
                }
            }

            "host_left" -> {
                val playback = Playback.fromJson(json.optJSONObject("playback"))
                _ui.update {
                    it.copy(
                        hostId = null,
                        playback = playback,
                    )
                }
                engine.applyRemote(playback)
                _events.emit(SessionEvent.HostLeft(json.optString("name").ifBlank { "房主" }))
            }

            "chat" -> {
                val message = json.optJSONObject("message")?.let { ChatMessage.fromJson(it) }
                if (message != null && _ui.value.chats.none { it.id == message.id }) {
                    _ui.update { it.copy(chats = (it.chats + message).takeLast(100)) }
                }
            }

            "reaction" -> {
                _events.emit(
                    SessionEvent.Reaction(
                        emoji = json.optString("emoji", "❤️"),
                        name = json.optString("name", "有人"),
                    )
                )
            }

            "ended" -> {
                leaving = true
                engine.pause()
                _events.emit(SessionEvent.Ended(json.optString("reason").ifBlank { "房间已经结束" }))
                _ui.update { it.copy(connection = LinkState.Closed) }
                disposeSoon()
            }

            "time" -> {
                handleTime(json)
            }

            else -> Unit
        }
    }

    private fun handleTime(json: JSONObject) {
        val t0 = json.optLong("t0")
        if (t0 <= 0) return
        val sentAt = synchronized(pendingTimeLock) { pendingTimes.remove(t0) } ?: return
        val receivedAt = SystemClock.elapsedRealtime()
        val rtt = (receivedAt - sentAt).coerceAtLeast(0)
        val t1 = json.optLong("t1")
        if (t1 <= 0) return
        val offset = t1 - (t0 + rtt / 2)
        timeSamples++
        val isBetter = bestRtt == Long.MAX_VALUE || rtt <= bestRtt + 8
        if (isBetter) {
            bestRtt = min(bestRtt, rtt)
            clockOffset.set(offset)
            _ui.update { it.copy(syncRttMs = rtt) }
        }
        if (!clockApplied && timeSamples >= 1) {
            clockApplied = true
            engine.applyRemote(_ui.value.playback)
        }
    }

    private fun applyRoomSnapshot(room: JSONObject, fromWelcome: Boolean) {
        val track = room.optJSONObject("track")?.let { Track.fromJson(it, serverUrl) }
        val playback = Playback.fromJson(room.optJSONObject("playback"))
        val members = Member.listFromJson(room.optJSONArray("members"))
        val hostId = room.stringOrNull("hostId")
        val hostName = room.optString("hostName")
        val selfId = room.stringOrNull("selfId")
        _ui.update {
            it.copy(
                hostId = hostId ?: it.hostId,
                hostName = hostName.ifBlank { it.hostName },
                members = if (members.isNotEmpty()) members else it.members,
                track = track,
                playback = playback,
                selfId = selfId ?: it.selfId,
            )
        }
        engine.setTrack(track)
        if (fromWelcome) engine.applyRemote(playback)
    }

    private fun parseChats(array: JSONArray): List<ChatMessage> = buildList {
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { add(ChatMessage.fromJson(it)) }
        }
    }

    private fun mergeChats(existing: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        if (incoming.isEmpty()) return existing
        val ids = existing.mapTo(HashSet()) { it.id }
        return (existing + incoming.filter { it.id !in ids }).takeLast(100)
    }

    private fun sendRaw(json: JSONObject): Boolean {
        val ws = socket ?: return false
        return ws.send(json.toString())
    }

    private fun sendHostEvent(json: JSONObject) {
        if (!isHost) return
        if (!sendRaw(json)) {
            _events.tryEmit(SessionEvent.Toast("还没有连接上房间"))
        }
    }

    fun togglePlay() {
        if (!isHost) return
        if (_ui.value.playback.playing) pause() else play()
    }

    fun play() {
        if (!isHost) return
        val track = _ui.value.track ?: return
        val position = engine.currentPosition()
        val start = if (track.durationMs > 0 && position >= track.durationMs - 800) 0L else position
        sendHostEvent(JSONObject().put("type", "play").put("positionMs", start))
    }

    fun pause() {
        if (!isHost) return
        sendHostEvent(
            JSONObject()
                .put("type", "pause")
                .put("positionMs", engine.currentPosition())
        )
    }

    fun seekTo(positionMs: Long) {
        if (!isHost) return
        sendHostEvent(
            JSONObject()
                .put("type", "seek")
                .put("positionMs", positionMs.coerceAtLeast(0))
                .put("playing", _ui.value.playback.playing)
        )
    }

    fun skipBy(deltaMs: Long) {
        if (!isHost) return
        val duration = _ui.value.track?.durationMs ?: 0
        val target = (engine.currentPosition() + deltaMs).coerceIn(
            0,
            if (duration > 0) duration else Long.MAX_VALUE,
        )
        seekTo(target)
    }

    fun sendChat(text: String) {
        val trimmed = text.trim().take(300)
        if (trimmed.isEmpty()) return
        sendRaw(JSONObject().put("type", "chat").put("text", trimmed))
    }

    fun sendReaction(emoji: String) {
        sendRaw(JSONObject().put("type", "reaction").put("emoji", emoji))
    }

    fun setVolume(volume: Float) {
        engine.setVolume(volume)
    }

    private fun disposeSoon() {
        scope.launch {
            delay(250)
            engine.release()
            scope.cancel()
        }
    }

    fun changeTrack(song: Song) {
        if (!isHost || _ui.value.upload != null) return
        val token = hostToken ?: return
        _ui.update { it.copy(upload = UploadState(0f, "正在上传《${song.title}》")) }
        scope.launch {
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    api.uploadTrack(serverUrl, code, token, song) { progress ->
                        _ui.update { state ->
                            state.copy(upload = UploadState(progress, "正在上传《${song.title}》"))
                        }
                    }
                }
                _ui.update { it.copy(track = uploaded) }
                engine.setTrack(uploaded)
                engine.pause()
                _ui.update { it.copy(upload = null) }
                val withCover = withContext(Dispatchers.IO) {
                    api.uploadCover(serverUrl, code, token, song, repository)
                }
                if (withCover != null) {
                    _ui.update { it.copy(track = withCover) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(upload = null) }
                _events.emit(SessionEvent.Toast(e.message ?: "更换歌曲失败"))
            }
        }
    }

    private fun onTrackEnded() {
        if (!isHost) return
        val duration = _ui.value.track?.durationMs ?: engine.currentPosition()
        if (duration > 0) {
            sendHostEvent(
                JSONObject()
                    .put("type", "pause")
                    .put("positionMs", duration)
            )
        }
    }

    fun leave(endRoom: Boolean = false) {
        if (leaving) return
        leaving = true
        reconnectJob?.cancel()
        timeJob?.cancel()
        if (endRoom && isHost) {
            sendRaw(JSONObject().put("type", "end"))
        }
        scope.launch {
            delay(if (endRoom) 180 else 40)
            socket?.close(1000, "bye")
            socket = null
            engine.release()
            _ui.update { it.copy(connection = LinkState.Closed, upload = null) }
            _events.tryEmit(SessionEvent.Ended(null))
            scope.cancel()
        }
    }

    fun serverNow(): Long = System.currentTimeMillis() + clockOffset.get()

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
