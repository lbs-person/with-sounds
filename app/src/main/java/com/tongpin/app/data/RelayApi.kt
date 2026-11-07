package com.tongpin.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RelayException(message: String) : IOException(message)

data class CreatedRoom(
    val code: String,
    val hostToken: String,
    val hostName: String,
)

class RelayApi(private val context: Context) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun health(baseUrl: String): JSONObject = withContext(Dispatchers.IO) {
        val base = normalizeServer(baseUrl)
        val request = Request.Builder().url("$base/api/health").get().build()
        client.newCall(request).execute().use { response ->
            parseJson(response)
        }
    }

    suspend fun createRoom(baseUrl: String, hostName: String): CreatedRoom =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(baseUrl)
            val payload = JSONObject().put("hostName", hostName).toString()
            val request = Request.Builder()
                .url("$base/api/rooms")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                val json = parseJson(response)
                CreatedRoom(
                    code = json.optString("code"),
                    hostToken = json.optString("hostToken"),
                    hostName = json.optString("hostName"),
                )
            }
        }

    suspend fun fetchRoom(baseUrl: String, code: String): JSONObject =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(baseUrl)
            val request = Request.Builder()
                .url("$base/api/rooms/$code")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                parseJson(response)
            }
        }

    suspend fun uploadTrack(        baseUrl: String,
        code: String,
        hostToken: String,
        song: Song,
        onProgress: (Float) -> Unit,
    ): Track = withContext(Dispatchers.IO) {
        val base = normalizeServer(baseUrl)
        val url = "$base/api/rooms/$code/track".toHttpUrl().newBuilder()
            .addQueryParameter("title", song.title)
            .addQueryParameter("artist", song.artistOrUnknown)
            .addQueryParameter("album", song.albumOrUnknown)
            .addQueryParameter("durationMs", song.durationMs.toString())
            .build()
        val requestBody = object : RequestBody() {
            override fun contentType() = song.mimeType.toMediaTypeOrNullSafe()
            override fun contentLength(): Long = song.sizeBytes.takeIf { it > 0 } ?: -1L

            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(song.uri)
                    ?: throw RelayException("无法读取这首本地音乐")
                input.use { source ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var uploaded = 0L
                    var lastReported = -1
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        uploaded += read
                        val percent = if (song.sizeBytes > 0) {
                            (uploaded * 100 / song.sizeBytes).toInt()
                        } else {
                            -1
                        }
                        if (percent != lastReported && percent >= 0) {
                            lastReported = percent
                            onProgress((percent / 100f).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Host-Token", hostToken)
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val json = parseJson(response)
            val trackJson = json.optJSONObject("track")
                ?: throw RelayException("服务器没有返回歌曲信息")
            Track.fromJson(trackJson, base)
        }
    }

    suspend fun uploadCover(
        baseUrl: String,
        code: String,
        hostToken: String,
        song: Song,
        repository: MusicRepository,
    ): Track? = withContext(Dispatchers.IO) {
        val bytes = repository.loadCoverJpeg(song) ?: return@withContext null
        val base = normalizeServer(baseUrl)
        val request = Request.Builder()
            .url("$base/api/rooms/$code/cover")
            .header("X-Host-Token", hostToken)
            .post(bytes.toRequestBody(IMAGE_MEDIA))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val json = JSONObject(response.body?.string().orEmpty())
                json.optJSONObject("track")?.let { Track.fromJson(it, base) }
            }
        }.getOrNull()
    }

    suspend fun deleteRoom(baseUrl: String, code: String, hostToken: String) =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(baseUrl)
            val request = Request.Builder()
                .url("$base/api/rooms/$code")
                .header("X-Host-Token", hostToken)
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    parseJson(response)
                }
                Unit
            }
        }

    fun openSocket(
        baseUrl: String,
        code: String,
        name: String,
        role: String,
        hostToken: String?,
        listener: WebSocketListener,
    ): WebSocket {
        val base = normalizeServer(baseUrl)
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            else -> "ws://" + base.removePrefix("http://")
        }
        val url = "$wsBase/ws".toHttpUrl().newBuilder()
            .addQueryParameter("code", code)
            .addQueryParameter("name", name)
            .addQueryParameter("role", role)
            .build()
        val request = Request.Builder()
            .url(url)
            .apply { if (!hostToken.isNullOrBlank()) header("X-Host-Token", hostToken) }
            .build()
        return client.newWebSocket(request, listener)
    }

    fun cancelAll() {
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val IMAGE_MEDIA = "image/jpeg".toMediaType()

        fun normalizeServer(input: String): String {
            var value = input.trim()
            if (value.isBlank()) throw RelayException("还没有填写房间服务器地址")
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            value = value.trimEnd('/')
            val parsed = runCatching { value.toHttpUrl() }.getOrElse {
                throw RelayException("服务器地址格式不对，例如 http://192.168.1.10:8080")
            }
            if (parsed.scheme != "http" && parsed.scheme != "https") {
                throw RelayException("服务器地址只支持 http:// 或 https://")
            }
            return parsed.toString().trimEnd('/')
        }

        fun isReachable(server: String): Boolean = server.isNotBlank()

        private fun parseJson(response: Response): JSONObject {
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("error")
                }.getOrNull().orEmpty().ifBlank {
                    "服务器返回 ${response.code}"
                }
                throw RelayException(message)
            }
            if (text.isBlank()) return JSONObject()
            return runCatching { JSONObject(text) }.getOrElse {
                throw RelayException("服务器返回了无法解析的内容")
            }
        }

        private fun String.toMediaTypeOrNullSafe() = runCatching {
            if (startsWith("audio/")) toMediaType() else "application/octet-stream".toMediaType()
        }.getOrDefault("application/octet-stream".toMediaType())
    }
}
