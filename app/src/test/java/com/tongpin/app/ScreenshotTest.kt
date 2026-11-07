package com.tongpin.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tongpin.app.data.ChatMessage
import com.tongpin.app.data.Member
import com.tongpin.app.data.Playback
import com.tongpin.app.data.SessionUi
import com.tongpin.app.data.Song
import com.tongpin.app.data.Track
import com.tongpin.app.playback.AudioState
import com.tongpin.app.ui.screens.JoinScreen
import com.tongpin.app.ui.screens.LobbyScreen
import com.tongpin.app.ui.screens.RoomScreen
import com.tongpin.app.ui.theme.TongpinTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val cover1 = "android.resource://com.tongpin.app/drawable/demo_cover_1"
    private val cover2 = "android.resource://com.tongpin.app/drawable/demo_cover_2"
    private val cover3 = "android.resource://com.tongpin.app/drawable/demo_cover_3"

    private val songs = listOf(
        Song(
            id = 1,
            uri = Uri.parse("android.resource://com.tongpin.app/drawable/demo_cover_1"),
            title = "晚风",
            artist = "橘子海岸",
            album = "夏日留声",
            albumId = 1,
            durationMs = 218_000,
            sizeBytes = 900_000,
            mimeType = "audio/mpeg",
            artworkUri = Uri.parse(cover1),
            dateAdded = 1,
        ),
        Song(
            id = 2,
            uri = Uri.parse("android.resource://com.tongpin.app/drawable/demo_cover_2"),
            title = "午夜电台",
            artist = "北纬三十度",
            album = "城市失眠",
            albumId = 2,
            durationMs = 246_000,
            sizeBytes = 950_000,
            mimeType = "audio/mpeg",
            artworkUri = Uri.parse(cover2),
            dateAdded = 2,
        ),
        Song(
            id = 3,
            uri = Uri.parse("android.resource://com.tongpin.app/drawable/demo_cover_3"),
            title = "雨停之前",
            artist = "小岛工作室",
            album = "潮湿的下午",
            albumId = 3,
            durationMs = 203_000,
            sizeBytes = 880_000,
            mimeType = "audio/mpeg",
            artworkUri = Uri.parse(cover3),
            dateAdded = 3,
        ),
    )

    private val members = listOf(
        Member("m1", "晚风", "host", "#FF8A66", 1),
        Member("m2", "橘子海", "guest", "#91E8C9", 2),
        Member("m3", "小满", "guest", "#A8A6FF", 3),
    )

    private val session = SessionUi(
        code = "482731",
        serverUrl = "http://192.168.1.10:8080",
        selfId = "m1",
        isHost = true,
        hostId = "m1",
        hostName = "晚风",
        members = members,
        track = Track(
            id = "t1",
            title = "晚风",
            artist = "橘子海岸",
            album = "夏日留声",
            durationMs = 218_000,
            size = 900_000,
            contentType = "audio/mpeg",
            hasCover = true,
            audioUrl = "http://192.168.1.10:8080/audio",
            coverUrl = cover1,
        ),
        playback = Playback(playing = false, positionMs = 64_000, startAt = 0),
        connection = com.tongpin.app.data.LinkState.Connected,
        syncRttMs = 23,
        chats = listOf(
            ChatMessage("c1", "m2", "橘子海", "#91E8C9", "这首歌好适合傍晚。", 1),
            ChatMessage("c2", "m3", "小满", "#A8A6FF", "耳机已戴好 🎧", 2),
            ChatMessage("c3", "m1", "晚风", "#FF8A66", "那我们从头听一遍。", 3),
        ),
    )

    @Test
    fun lobby() {
        setAndCapture("lobby") {
            LobbyScreen(
                ui = AppUi(
                    songs = songs,
                    hasAudioPermission = true,
                    libraryLoaded = true,
                    nickname = "晚风",
                    serverUrl = "http://192.168.1.10:8080",
                ),
                onOpenCreate = {},
                onOpenJoin = {},
                onSongClick = {},
                onOpenSettings = {},
                onRefresh = {},
                onRequestPermission = {},
            )
        }
    }

    @Test
    fun join() {
        setAndCapture("join") {
            JoinScreen(
                ui = AppUi(
                    nickname = "橘子海",
                    serverUrl = "http://192.168.1.10:8080",
                    prefillCode = "482731",
                ),
                onBack = {},
                onJoin = { _, _, _ -> },
                onTestServer = {},
            )
        }
    }

    @Test
    fun room() {
        setAndCapture("room") {
            RoomScreen(
                ui = AppUi(
                    songs = songs,
                    hasAudioPermission = true,
                    libraryLoaded = true,
                    session = session,
                    audio = AudioState(
                        positionMs = 64_000,
                        durationMs = 218_000,
                        playing = false,
                    ),
                ),
                reactions = emptyFlow(),
                onLeave = {},
                onTogglePlay = {},
                onSeek = {},
                onSkip = {},
                onSendChat = {},
                onReaction = {},
                onChangeTrack = {},
            )
        }
    }

    private fun setAndCapture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            TongpinTheme { content() }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        repeat(4) {
            Thread.sleep(250)
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
        }
        val view = compose.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            val width = 1080
            val height = 2340
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val cwd = System.getProperty("user.dir") ?: "."
        val dir = File(cwd, "docs/screenshots").let { direct ->
            if (direct.parentFile?.exists() == true) direct else File(cwd, "../docs/screenshots")
        }
        dir.mkdirs()
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("screenshot: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
