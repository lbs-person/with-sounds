package com.tongpin.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tongpin.app.data.CreatedRoom
import com.tongpin.app.data.LinkState
import com.tongpin.app.data.MusicRepository
import com.tongpin.app.data.Prefs
import com.tongpin.app.data.RelayApi
import com.tongpin.app.data.ReactionEvent
import com.tongpin.app.data.RoomSession
import com.tongpin.app.data.SessionEvent
import com.tongpin.app.data.SessionUi
import com.tongpin.app.data.Song
import com.tongpin.app.playback.AudioState
import com.tongpin.app.playback.RoomPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { Lobby, Join, Room }

data class BusyState(
    val title: String,
    val detail: String? = null,
    val progress: Float? = null,
)

data class AppUi(
    val screen: AppScreen = AppScreen.Lobby,
    val songs: List<Song> = emptyList(),
    val libraryLoading: Boolean = false,
    val libraryLoaded: Boolean = false,
    val libraryError: String? = null,
    val hasAudioPermission: Boolean = false,
    val nickname: String = "",
    val serverUrl: String = "",
    val busy: BusyState? = null,
    val testingServer: Boolean = false,
    val session: SessionUi? = null,
    val audio: AudioState = AudioState(),
    val prefillCode: String? = null,
    val prefillServer: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val repository = MusicRepository(application)
    private val api = RelayApi(application)

    private val _ui = MutableStateFlow(
        AppUi(
            nickname = prefs.nickname,
            serverUrl = prefs.serverUrl,
        )
    )
    val ui = _ui.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val toasts = _toasts.asSharedFlow()

    private val _reactions = MutableSharedFlow<ReactionEvent>(extraBufferCapacity = 24)
    val reactions = _reactions.asSharedFlow()

    private var currentSession: RoomSession? = null
    private var sessionJob: Job? = null
    private var libraryJob: Job? = null

    fun onAudioPermission(granted: Boolean) {
        _ui.update { it.copy(hasAudioPermission = granted) }
        if (granted && !_ui.value.libraryLoaded) {
            loadLibrary()
        }
    }

    fun loadLibrary(force: Boolean = false) {
        if (libraryJob?.isActive == true) return
        if (!_ui.value.hasAudioPermission) return
        libraryJob = viewModelScope.launch {
            _ui.update { it.copy(libraryLoading = true, libraryError = null) }
            val result = runCatching { withContext(Dispatchers.IO) { repository.loadSongs() } }
            result.onSuccess { songs ->
                _ui.update {
                    it.copy(
                        songs = songs,
                        libraryLoading = false,
                        libraryLoaded = true,
                        libraryError = null,
                    )
                }
            }.onFailure { error ->
                _ui.update {
                    it.copy(
                        libraryLoading = false,
                        libraryLoaded = true,
                        libraryError = error.message ?: "音乐库读取失败",
                    )
                }
            }
        }
    }

    fun saveSettings(nickname: String, serverUrl: String) {
        val cleanName = nickname.trim().take(20).ifBlank { prefs.nickname }
        val cleanServer = serverUrl.trim().trimEnd('/')
        prefs.nickname = cleanName
        prefs.serverUrl = cleanServer
        _ui.update { it.copy(nickname = cleanName, serverUrl = cleanServer) }
    }

    fun testServer(serverUrl: String) {
        if (_ui.value.testingServer) return
        viewModelScope.launch {
            _ui.update { it.copy(testingServer = true) }
            try {
                val base = RelayApi.normalizeServer(serverUrl)
                val info = withContext(Dispatchers.IO) { api.health(base) }
                val name = info.optString("name", "同频电台")
                val rooms = info.optInt("rooms", 0)
                _toasts.emit("已连接 · $name · $rooms 个房间在线")
            } catch (e: Exception) {
                _toasts.emit(e.message ?: "连接失败，请检查地址和服务器")
            } finally {
                _ui.update { it.copy(testingServer = false) }
            }
        }
    }

    fun openJoin(prefillCode: String? = null, prefillServer: String? = null) {
        _ui.update {
            it.copy(
                screen = AppScreen.Join,
                prefillCode = prefillCode,
                prefillServer = prefillServer,
            )
        }
    }

    fun backToLobby() {
        if (_ui.value.screen == AppScreen.Join) {
            _ui.update { it.copy(screen = AppScreen.Lobby) }
        }
    }

    fun createRoom(song: Song, nickname: String, serverUrl: String) {
        if (_ui.value.busy != null) return
        saveSettings(nickname, serverUrl)
        val cleanName = _ui.value.nickname
        val cleanServer = _ui.value.serverUrl
        if (cleanServer.isBlank()) {
            _toasts.tryEmit("先填写房间服务器地址")
            return
        }
        _ui.update {
            it.copy(busy = BusyState("正在创建房间…", "准备把《${song.title}》分享给房间"))
        }
        viewModelScope.launch {
            var created: CreatedRoom? = null
            try {
                created = withContext(Dispatchers.IO) {
                    api.createRoom(RelayApi.normalizeServer(cleanServer), cleanName)
                }
                _ui.update {
                    it.copy(busy = BusyState("正在上传音乐…", "《${song.title}》", 0f))
                }
                val track = withContext(Dispatchers.IO) {
                    api.uploadTrack(
                        baseUrl = cleanServer,
                        code = created.code,
                        hostToken = created.hostToken,
                        song = song,
                    ) { progress ->
                        _ui.update { current ->
                            current.copy(
                                busy = current.busy?.copy(progress = progress)
                            )
                        }
                    }
                }
                _ui.update {
                    it.copy(busy = BusyState("正在准备封面…", "马上就好"))
                }
                val withCover = withContext(Dispatchers.IO) {
                    api.uploadCover(cleanServer, created.code, created.hostToken, song, repository)
                }
                val session = RoomSession(
                    context = getApplication(),
                    api = api,
                    repository = repository,
                    serverUrl = RelayApi.normalizeServer(cleanServer),
                    code = created.code,
                    nickname = cleanName,
                    isHost = true,
                    hostToken = created.hostToken,
                    initialTrack = withCover ?: track,
                )
                attachSession(session)
                runCatching { RoomPlaybackService.start(getApplication(), created.code) }
                _ui.update {
                    it.copy(
                        screen = AppScreen.Room,
                        session = session.ui.value,
                        busy = null,
                        prefillCode = null,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null) }
                _toasts.tryEmit(e.message ?: "创建房间失败")
                created?.let { room ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            api.deleteRoom(cleanServer, room.code, room.hostToken)
                        }
                    }
                }
            }
        }
    }

    fun joinRoom(code: String, nickname: String, serverUrl: String) {
        if (_ui.value.busy != null) return
        val digits = code.filter(Char::isDigit)
        if (digits.length != 6) {
            _toasts.tryEmit("房间号是 6 位数字")
            return
        }
        saveSettings(nickname, serverUrl)
        val cleanName = _ui.value.nickname
        val cleanServer = _ui.value.serverUrl
        if (cleanServer.isBlank()) {
            _toasts.tryEmit("先填写房间服务器地址")
            return
        }
        _ui.update {
            it.copy(busy = BusyState("正在加入房间…", "房间 $digits"))
        }
        viewModelScope.launch {
            try {
                val base = RelayApi.normalizeServer(cleanServer)
                val room = withContext(Dispatchers.IO) { api.fetchRoom(base, digits) }
                val session = RoomSession(
                    context = getApplication(),
                    api = api,
                    repository = repository,
                    serverUrl = base,
                    code = digits,
                    nickname = cleanName,
                    isHost = false,
                    hostToken = null,
                    initialTrack = null,
                    initialRoom = room,
                )
                attachSession(session)
                runCatching { RoomPlaybackService.start(getApplication(), digits) }
                _ui.update {
                    it.copy(
                        screen = AppScreen.Room,
                        session = session.ui.value,
                        busy = null,
                        prefillCode = null,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null) }
                _toasts.tryEmit(e.message ?: "加入房间失败")
            }
        }
    }

    private fun attachSession(session: RoomSession) {
        sessionJob?.cancel()
        currentSession = session
        sessionJob = viewModelScope.launch {
            launch {
                session.ui.collect { state ->
                    _ui.update { it.copy(session = state) }
                }
            }
            launch {
                session.audio.collect { state ->
                    _ui.update { it.copy(audio = state) }
                }
            }
            launch {
                session.events.collect { event ->
                    when (event) {
                        is SessionEvent.Toast -> _toasts.emit(event.message)
                        is SessionEvent.Reaction -> _reactions.emit(
                            ReactionEvent(event.emoji, event.name, System.currentTimeMillis())
                        )
                        is SessionEvent.HostLeft -> {
                            _toasts.emit("${event.name} 离开了，音乐已暂停")
                        }
                        is SessionEvent.Ended -> {
                            val reason = event.reason
                            currentSession = null
                            RoomPlaybackService.stop(getApplication())
                            _ui.update {
                                it.copy(
                                    screen = AppScreen.Lobby,
                                    session = null,
                                    audio = AudioState(),
                                    busy = null,
                                )
                            }
                            if (!reason.isNullOrBlank()) _toasts.emit(reason)
                        }
                    }
                }
            }
        }
    }

    fun leaveRoom() {
        val session = currentSession ?: run {
            _ui.update { it.copy(screen = AppScreen.Lobby, session = null) }
            return
        }
        val wasHost = _ui.value.session?.isHost == true
        currentSession = null
        sessionJob?.cancel()
        session.leave(endRoom = wasHost)
        RoomPlaybackService.stop(getApplication())
        _ui.update {
            it.copy(
                screen = AppScreen.Lobby,
                session = null,
                audio = AudioState(),
                busy = null,
                prefillCode = null,
            )
        }
    }

    fun togglePlay() = currentSession?.togglePlay()
    fun seekTo(positionMs: Long) = currentSession?.seekTo(positionMs)
    fun skipBy(deltaMs: Long) = currentSession?.skipBy(deltaMs)
    fun sendChat(text: String) = currentSession?.sendChat(text)
    fun sendReaction(emoji: String) = currentSession?.sendReaction(emoji)
    fun changeTrack(song: Song) = currentSession?.changeTrack(song)

    fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme != "tongpin" || uri.host != "join") return
        if (_ui.value.session != null) {
            _toasts.tryEmit("先离开当前房间，再加入新的房间")
            return
        }
        val code = uri.getQueryParameter("code")?.filter(Char::isDigit)
        val server = uri.getQueryParameter("server")
        openJoin(
            prefillCode = code?.takeIf { it.length == 6 },
            prefillServer = server,
        )
    }

    override fun onCleared() {
        currentSession?.leave(endRoom = _ui.value.session?.isHost == true)
        currentSession = null
        RoomPlaybackService.stop(getApplication())
        super.onCleared()
    }
}
