package com.tongpin.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tongpin.app.AppUi
import com.tongpin.app.data.ChatMessage
import com.tongpin.app.data.LinkState
import com.tongpin.app.data.Member
import com.tongpin.app.data.ReactionEvent
import com.tongpin.app.data.SessionUi
import com.tongpin.app.ui.components.Avatar
import com.tongpin.app.ui.components.CoverArt
import com.tongpin.app.ui.components.LiveBars
import com.tongpin.app.ui.components.SyncBadge
import com.tongpin.app.ui.components.WaveMark
import com.tongpin.app.ui.components.formatDuration
import com.tongpin.app.ui.components.formatTime
import com.tongpin.app.ui.sheets.SongPickerSheet
import com.tongpin.app.ui.theme.TpColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    ui: AppUi,
    reactions: Flow<ReactionEvent>,
    onLeave: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSendChat: (String) -> Unit,
    onReaction: (String) -> Unit,
    onChangeTrack: (com.tongpin.app.data.Song) -> Unit,
) {
    val session = ui.session ?: return
    val track = session.track
    val isHost = session.isHost
    val audio = ui.audio
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current

    var showLeaveDialog by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showSongPicker by remember { mutableStateOf(false) }
    var unread by remember { mutableIntStateOf(0) }
    var seenMessages by remember { mutableIntStateOf(session.chats.size) }
    val floating = remember { mutableStateListOf<FloatingReaction>() }

    BackHandler { showLeaveDialog = true }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(session.chats.size, showChat) {
        if (showChat) {
            seenMessages = session.chats.size
            unread = 0
        } else {
            unread = (session.chats.size - seenMessages).coerceAtLeast(0)
        }
    }

    LaunchedEffect(Unit) {
        reactions.collect { event ->
            floating.add(
                FloatingReaction(
                    id = System.nanoTime(),
                    emoji = event.emoji,
                    name = event.name,
                    xOffset = Random.nextInt(-90, 91),
                )
            )
        }
    }

    val duration = max(audio.durationMs, track?.durationMs ?: 0L)
    val displayPosition = if (duration > 0) audio.positionMs.coerceIn(0, duration) else 0L

    Box(Modifier.fillMaxSize().background(TpColors.Ink)) {
        // A restrained ambient wash. No full-screen bitmap here: a missing or
        // still-loading cover should never leave a giant fallback watermark.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TpColors.Surface.copy(alpha = 0.55f),
                            TpColors.Ink.copy(alpha = 0.96f),
                            TpColors.Ink,
                        )
                    )
                )
        )
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(TpColors.Peach.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.30f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.5f, size.height * 0.30f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            RoomTopBar(
                session = session,
                onClose = { showLeaveDialog = true },
                onCopy = {
                    clipboard.setText(AnnotatedString(session.code))
                    Toast.makeText(context, "房间号已复制", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    showShare = true
                },
                onPeople = { showPeople = true },
            )

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AlbumStage(
                    coverUrl = track?.coverUrl,
                    trackId = track?.id,
                    playing = session.playback.playing,
                    hasTrack = track != null,
                )
            }

            Spacer(Modifier.height(10.dp))
            TrackInfo(session)
            Spacer(Modifier.height(14.dp))
            ProgressBlock(
                position = displayPosition,
                duration = duration,
                enabled = isHost,
                onSeek = onSeek,
            )
            Spacer(Modifier.height(4.dp))
            Controls(
                playing = session.playback.playing,
                enabled = isHost,
                onToggle = onTogglePlay,
                onBack = { onSkip(-10_000) },
                onForward = { onSkip(10_000) },
            )
            SyncHint(session, isHost)
            Spacer(Modifier.height(8.dp))
            ListeningStrip(session = session, onClick = { showPeople = true })
            Spacer(Modifier.height(8.dp))
            BottomDock(
                session = session,
                unread = unread,
                onChat = { showChat = true },
                onReaction = { onReaction("❤️") },
                onPickSong = { showSongPicker = true },
            )
            Spacer(Modifier.height(8.dp))
        }

        // Floating hearts / reactions.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 190.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            floating.forEach { item ->
                key(item.id) {
                    FloatingReactionView(
                        item = item,
                        onFinished = { floating.remove(item) },
                    )
                }
            }
        }

        session.upload?.let { upload ->
            UploadOverlay(
                progress = upload.progress,
                label = upload.label,
            )
        }
    }

    if (showLeaveDialog) {
        LeaveDialog(
            isHost = isHost,
            onDismiss = { showLeaveDialog = false },
            onConfirm = {
                showLeaveDialog = false
                onLeave()
            },
        )
    }

    if (showChat) {
        ChatSheet(
            session = session,
            onDismiss = { showChat = false },
            onSend = onSendChat,
        )
    }

    if (showPeople) {
        PeopleSheet(session = session, onDismiss = { showPeople = false })
    }

    if (showShare) {
        ShareRoomSheet(session = session, onDismiss = { showShare = false })
    }

    if (showSongPicker) {
        SongPickerSheet(
            songs = ui.songs,
            onDismiss = { showSongPicker = false },
            onSelect = { song ->
                showSongPicker = false
                onChangeTrack(song)
            },
        )
    }
}

@Composable
private fun RoomTopBar(
    session: SessionUi,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPeople: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "收起房间",
                tint = TpColors.Cream,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.clickable(onClick = onCopy)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "房间 ${session.code}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.Cream,
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "复制房间号",
                    tint = TpColors.Muted,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                "点房间号复制",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
                letterSpacing = 0.4.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        SyncBadge(
            rttMs = session.syncRttMs,
            connected = session.connection == LinkState.Connected,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onPeople, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.PeopleOutline,
                contentDescription = "房间成员",
                tint = TpColors.CreamMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.Share,
                contentDescription = "邀请朋友",
                tint = TpColors.CreamMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AlbumStage(
    coverUrl: String?,
    trackId: String?,
    playing: Boolean,
    hasTrack: Boolean,
) {
    val rotation = remember(trackId) { Animatable(0f) }
    LaunchedEffect(playing, trackId) {
        if (!playing) return@LaunchedEffect
        while (isActive) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(28_000, easing = LinearEasing),
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val diameter = min(maxWidth.value, maxHeight.value).dp * 0.98f
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(
                    elevation = 36.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black,
                    spotColor = TpColors.Peach.copy(alpha = 0.5f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF080708))
                val maxR = size.minDimension / 2f
                for (i in 1..13) {
                    drawCircle(
                        color = TpColors.Cream.copy(alpha = 0.035f),
                        radius = maxR * (0.98f - i * 0.018f),
                        style = Stroke(width = 1f),
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(TpColors.Peach.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.34f, size.height * 0.28f),
                        radius = maxR,
                    ),
                    radius = maxR,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize(0.82f)
                    .graphicsLayer { rotationZ = rotation.value }
                    .shadow(18.dp, CircleShape)
                    .clip(CircleShape)
                    .border(1.dp, TpColors.Cream.copy(alpha = 0.15f), CircleShape),
            ) {
                CoverArt(
                    model = coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    fallbackSeed = "",
                    fallbackColor = TpColors.Peach,
                )
            }
            if (!hasTrack) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaveMark(Modifier.size(42.dp), color = TpColors.Peach)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "等待房主选歌",
                        style = MaterialTheme.typography.labelMedium,
                        color = TpColors.CreamMuted,
                    )
                }
            }
        }
        if (playing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clip(RoundedCornerShape(50))
                    .background(TpColors.Ink.copy(alpha = 0.7f))
                    .border(1.dp, TpColors.Line, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveBars(Modifier.size(width = 18.dp, height = 12.dp))
                    Text(
                        "PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TpColors.Mint,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackInfo(session: SessionUi) {
    val track = session.track
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = track?.title ?: "还没有选歌",
            style = MaterialTheme.typography.headlineMedium,
            color = TpColors.Cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track?.let { "${it.artist} · ${it.album.ifBlank { "未知专辑" }}" }
                ?: "房主选好歌后会自动出现",
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProgressBlock(
    position: Long,
    duration: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else position.toFloat()
    Column {
        Slider(
            value = value.coerceIn(0f, max(1f, duration.toFloat())),
            onValueChange = {
                if (enabled) {
                    dragging = true
                    dragValue = it
                }
            },
            onValueChangeFinished = {
                if (enabled) {
                    onSeek(dragValue.toLong())
                    dragging = false
                }
            },
            enabled = enabled && duration > 0,
            valueRange = 0f..max(1f, duration.toFloat()),
            colors = SliderDefaults.colors(
                thumbColor = TpColors.Peach,
                activeTrackColor = TpColors.Peach,
                inactiveTrackColor = TpColors.Cream.copy(alpha = 0.16f),
                disabledThumbColor = TpColors.Peach.copy(alpha = 0.38f),
                disabledActiveTrackColor = TpColors.Peach.copy(alpha = 0.24f),
                disabledInactiveTrackColor = TpColors.Cream.copy(alpha = 0.12f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatTime(if (dragging) dragValue.toLong() else position),
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
            )
        }
    }
}

@Composable
private fun Controls(
    playing: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(54.dp)) {
            Icon(
                Icons.Rounded.Replay10,
                contentDescription = "后退 10 秒",
                tint = if (enabled) TpColors.CreamMuted else TpColors.Muted.copy(alpha = 0.38f),
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (enabled) TpColors.Cream else TpColors.SurfaceHigh)
                .clickable(enabled = enabled, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = if (enabled) TpColors.Ink else TpColors.Muted,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        IconButton(onClick = onForward, enabled = enabled, modifier = Modifier.size(54.dp)) {
            Icon(
                Icons.Rounded.Forward10,
                contentDescription = "前进 10 秒",
                tint = if (enabled) TpColors.CreamMuted else TpColors.Muted.copy(alpha = 0.38f),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun SyncHint(session: SessionUi, isHost: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isHost) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = TpColors.Muted,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (session.hostId == null) "房主已离开，播放已暂停"
                else "由房主控制播放 · 你听到的和大家同步",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
                letterSpacing = 0.2.sp,
            )
        } else {
            Text(
                "你是房主 · 你的操作会同步给所有人",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

@Composable
private fun ListeningStrip(session: SessionUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TpColors.Surface.copy(alpha = 0.38f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            session.members.take(4).forEachIndexed { index, member ->
                Avatar(
                    name = member.name,
                    color = memberColor(member),
                    size = 24.dp,
                    modifier = Modifier.offset(x = (-6 * index).dp),
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            "${session.listenerCount} 人在听",
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.CreamMuted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (session.hostId != null) "${session.hostName} 是房主" else "房主已离开",
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Rounded.KeyboardArrowRight,
            contentDescription = "查看成员",
            tint = TpColors.Muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun BottomDock(
    session: SessionUi,
    unread: Int,
    onChat: () -> Unit,
    onReaction: () -> Unit,
    onPickSong: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (session.isHost) {
            DockPill(
                modifier = Modifier.weight(1f),
                onClick = onPickSong,
                icon = { Icon(Icons.Rounded.LibraryMusic, null, tint = TpColors.Mint, modifier = Modifier.size(18.dp)) },
                text = "换一首",
            )
        } else {
            DockPill(
                modifier = Modifier.weight(1f),
                onClick = onChat,
                icon = {
                    Box {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = TpColors.CreamMuted, modifier = Modifier.size(18.dp))
                        if (unread > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(TpColors.Peach)
                            )
                        }
                    }
                },
                text = if (unread > 0) "聊天 · $unread" else "聊天",
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TpColors.Peach.copy(alpha = 0.14f))
                .clickable(onClick = onReaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "送一个爱心",
                tint = TpColors.Peach,
                modifier = Modifier.size(23.dp),
            )
        }
        if (session.isHost) {
            DockPill(
                modifier = Modifier.weight(1f),
                onClick = onChat,
                icon = {
                    Box {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = TpColors.CreamMuted, modifier = Modifier.size(18.dp))
                        if (unread > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(TpColors.Peach)
                            )
                        }
                    }
                },
                text = if (unread > 0) "聊天 · $unread" else "聊天",
            )
        } else {
            MembersPill(
                session = session,
                onChat = onChat,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MembersPill(
    session: SessionUi,
    onChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TpColors.Surface.copy(alpha = 0.55f))
            .clickable(onClick = onChat)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            session.members.take(3).forEachIndexed { index, member ->
                Avatar(
                    name = member.name,
                    color = memberColor(member),
                    size = 26.dp,
                    modifier = Modifier.offset(x = (-6 * index).dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "${session.listenerCount} 人在听",
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.CreamMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun DockPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TpColors.Surface.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = TpColors.CreamMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun MembersHeader(session: SessionUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${session.listenerCount} 人在听",
            style = MaterialTheme.typography.labelMedium,
            color = TpColors.CreamMuted,
        )
        Spacer(Modifier.width(8.dp))
        LiveBars(Modifier.size(width = 16.dp, height = 10.dp), color = TpColors.Mint)
    }
}

@Composable
private fun LeaveDialog(isHost: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TpColors.Surface,
        titleContentColor = TpColors.Cream,
        textContentColor = TpColors.Muted,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(if (isHost) "结束这场一起听？" else "离开房间？")
        },
        text = {
            Text(
                if (isHost) "房主离开会结束整个房间，所有人都会断开连接。"
                else "离开后音乐停止；房主还在的话，随时可以用房间号回来。",
                lineHeight = 21.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHost) TpColors.Danger else TpColors.Peach,
                    contentColor = TpColors.Ink,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text(if (isHost) "结束房间" else "离开")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("再听会儿", color = TpColors.CreamMuted)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSheet(session: SessionUi, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(session.chats.size) {
        if (session.chats.isNotEmpty()) {
            listState.animateScrollToItem(session.chats.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TpColors.InkSoft,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TpColors.Cream.copy(alpha = 0.18f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "房间聊天",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TpColors.Cream,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${session.listenerCount} 人",
                    style = MaterialTheme.typography.labelMedium,
                    color = TpColors.Muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (session.chats.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WaveMark(Modifier.size(34.dp), color = TpColors.SurfaceHigh, accent = TpColors.Muted)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "说点什么，让大家知道你在听",
                        style = MaterialTheme.typography.bodySmall,
                        color = TpColors.Muted,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(session.chats, key = { it.id }) { message ->
                        ChatBubble(
                            message = message,
                            mine = message.memberId == session.selfId,
                        )
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(TpColors.Surface.copy(alpha = 0.8f))
                    .padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(300) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TpColors.Cream),
                    cursorBrush = SolidColor(TpColors.Peach),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (draft.isNotBlank()) {
                                onSend(draft)
                                draft = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    "说点什么…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TpColors.Muted.copy(alpha = 0.6f),
                                )
                            }
                            inner()
                        }
                    },
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSend(draft)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "发送",
                        tint = if (draft.isNotBlank()) TpColors.Peach else TpColors.Muted.copy(alpha = 0.4f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, mine: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (mine) "我" else message.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (mine) TpColors.Peach else memberColor(message.color),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .widthIn(max = 286.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 6.dp,
                        bottomEnd = if (mine) 6.dp else 18.dp,
                    )
                )
                .background(if (mine) TpColors.Peach else TpColors.SurfaceHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) TpColors.Ink else TpColors.Cream,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareRoomSheet(session: SessionUi, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val deepLink = remember(session.serverUrl, session.code) {
        "tongpin://join?server=${Uri.encode(session.serverUrl)}&code=${session.code}"
    }
    val inviteText = remember(session.serverUrl, session.code) {
        buildString {
            append("来同频一起听歌 🎧\n")
            append("房间号：${session.code}\n")
            append("服务器：${session.serverUrl}\n")
            append("打开「同频」→ 加入房间，填上面两个信息；也可以直接用相机扫分享页里的二维码。")
        }
    }
    val qr = remember(deepLink) { makeQr(deepLink) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TpColors.InkSoft,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TpColors.Cream.copy(alpha = 0.18f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "邀请朋友",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "同一个服务器，同一个房间号，就会听到同一秒。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(242.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "房间二维码",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("二维码生成失败", color = Color.Black, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                session.code.toCharArray().joinToString(" "),
                style = MaterialTheme.typography.displayMedium,
                color = TpColors.Cream,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                session.serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                style = MaterialTheme.typography.labelMedium,
                color = TpColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TpColors.Surface)
                        .clickable {
                            clipboard.setText(AnnotatedString(inviteText))
                            Toast.makeText(context, "邀请信息已复制", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "复制邀请",
                        style = MaterialTheme.typography.labelLarge,
                        color = TpColors.Cream,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TpColors.Peach)
                        .clickable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, inviteText)
                            }
                            context.startActivity(Intent.createChooser(intent, "邀请朋友一起听"))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "系统分享",
                        style = MaterialTheme.typography.labelLarge,
                        color = TpColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "朋友用系统相机扫码，会直接打开同频并填好房间号和服务器。",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )
        }
    }
}

private fun makeQr(content: String): Bitmap? = runCatching {
    val size = 640
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleSheet(session: SessionUi, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TpColors.InkSoft,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TpColors.Cream.copy(alpha = 0.18f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "在听的人",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.listenerCount} 个人，正在同一个频率上。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
            )
            Spacer(Modifier.height(18.dp))
            session.members.forEach { member ->
                MemberRow(member = member, selfId = session.selfId, hostId = session.hostId)
                Spacer(Modifier.height(4.dp))
            }
            if (session.members.isEmpty()) {
                Text(
                    "还在连接…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TpColors.Muted,
            )
            }
        }
    }
}

@Composable
private fun MemberRow(member: Member, selfId: String?, hostId: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TpColors.Surface.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = member.name, color = memberColor(member), size = 38.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member.name + if (member.id == selfId) "（我）" else "",
                style = MaterialTheme.typography.titleMedium,
                color = TpColors.Cream,
            )
            Text(
                if (member.id == hostId) "房主" else "听众",
                style = MaterialTheme.typography.labelSmall,
                color = if (member.id == hostId) TpColors.Peach else TpColors.Muted,
            )
        }
        if (member.id == hostId) {
            WaveMark(Modifier.size(18.dp), color = TpColors.Peach)
        } else {
            LiveBars(Modifier.size(width = 14.dp, height = 10.dp), color = TpColors.Muted)
        }
    }
}

@Composable
private fun UploadOverlay(progress: Float, label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(26.dp))
                .background(TpColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaveMark(Modifier.size(38.dp), color = TpColors.Peach)
            Spacer(Modifier.height(16.dp))
            Text(
                "正在换歌",
                style = MaterialTheme.typography.titleLarge,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = TpColors.Peach,
                trackColor = TpColors.Cream.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
            )
        }
    }
}

private data class FloatingReaction(
    val id: Long,
    val emoji: String,
    val name: String,
    val xOffset: Int,
)

@Composable
private fun FloatingReactionView(item: FloatingReaction, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(item.id) {
        progress.animateTo(1f, animationSpec = tween(1900, easing = LinearEasing))
        onFinished()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset(x = item.xOffset.dp, y = (-160 * progress.value).dp)
            .alpha((1f - progress.value).coerceIn(0f, 1f)),
    ) {
        Text(item.emoji, fontSize = 34.sp)
        Text(
            item.name,
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.CreamMuted,
        )
    }
}

private fun memberColor(member: Member): Color = runCatching {
    Color(android.graphics.Color.parseColor(member.color))
}.getOrDefault(TpColors.Peach)

private fun memberColor(color: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(color))
}.getOrDefault(TpColors.Peach)
