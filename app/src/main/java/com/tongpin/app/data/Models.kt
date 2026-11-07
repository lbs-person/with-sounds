package com.tongpin.app.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val artworkUri: Uri?,
    val dateAdded: Long,
) {
    val artistOrUnknown: String get() = artist.ifBlank { "未知艺术家" }
    val albumOrUnknown: String get() = album.ifBlank { "未知专辑" }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val contentType: String,
    val hasCover: Boolean,
    val audioUrl: String,
    val coverUrl: String?,
) {
    companion object {
        fun fromJson(json: JSONObject, baseUrl: String): Track = Track(
            id = json.optString("id"),
            title = json.optString("title").ifBlank { "未命名歌曲" },
            artist = json.optString("artist").ifBlank { "未知艺术家" },
            album = json.optString("album"),
            durationMs = json.optLong("durationMs"),
            size = json.optLong("size"),
            contentType = json.optString("contentType"),
            hasCover = json.optBoolean("cover"),
            audioUrl = absolute(baseUrl, json.optString("audioUrl")),
            coverUrl = if (json.isNull("coverUrl")) null else {
                json.optString("coverUrl").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { absolute(baseUrl, it) }
            },
        )

        private fun absolute(base: String, path: String): String {
            if (path.isBlank()) return ""
            if (path.startsWith("http://") || path.startsWith("https://")) return path
            return base.trimEnd('/') + "/" + path.trimStart('/')
        }
    }
}

data class Playback(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val startAt: Long = 0,
) {
    companion object {
        fun fromJson(json: JSONObject?): Playback {
            if (json == null) return Playback()
            return Playback(
                playing = json.optBoolean("playing"),
                positionMs = json.optLong("positionMs").coerceAtLeast(0),
                startAt = json.optLong("startAt"),
            )
        }
    }
}

data class Member(
    val id: String,
    val name: String,
    val role: String,
    val color: String,
    val joinedAt: Long,
) {
    val isHost: Boolean get() = role == "host"

    companion object {
        fun fromJson(json: JSONObject): Member = Member(
            id = json.optString("id"),
            name = json.optString("name").ifBlank { "听众" },
            role = json.optString("role", "guest"),
            color = json.optString("color", "#FF8A66"),
            joinedAt = json.optLong("joinedAt"),
        )

        fun listFromJson(array: JSONArray?): List<Member> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(fromJson(item))
                }
            }
        }
    }
}

data class ChatMessage(
    val id: String,
    val memberId: String,
    val name: String,
    val color: String,
    val text: String,
    val at: Long,
) {
    companion object {
        fun fromJson(json: JSONObject): ChatMessage = ChatMessage(
            id = json.optString("id"),
            memberId = json.optString("memberId"),
            name = json.optString("name"),
            color = json.optString("color", "#FF8A66"),
            text = json.optString("text"),
            at = json.optLong("at"),
        )
    }
}

data class ReactionEvent(
    val emoji: String,
    val name: String,
    val at: Long,
)

enum class LinkState { Connecting, Connected, Reconnecting, Closed }

data class UploadState(
    val progress: Float = 0f,
    val label: String = "正在上传音乐…",
)

data class SessionUi(
    val code: String,
    val serverUrl: String,
    val selfId: String? = null,
    val isHost: Boolean = false,
    val hostId: String? = null,
    val hostName: String = "",
    val members: List<Member> = emptyList(),
    val track: Track? = null,
    val playback: Playback = Playback(),
    val chats: List<ChatMessage> = emptyList(),
    val connection: LinkState = LinkState.Connecting,
    val syncRttMs: Long? = null,
    val upload: UploadState? = null,
    val error: String? = null,
) {
    val self: Member? get() = members.firstOrNull { it.id == selfId }
    val host: Member? get() = members.firstOrNull { it.id == hostId }
    val listenerCount: Int get() = members.size
}
