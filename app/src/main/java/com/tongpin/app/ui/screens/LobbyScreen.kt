package com.tongpin.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tongpin.app.AppUi
import com.tongpin.app.data.Song
import com.tongpin.app.ui.components.GlassCard
import com.tongpin.app.ui.components.SongRow
import com.tongpin.app.ui.components.TongpinBackground
import com.tongpin.app.ui.components.WaveMark
import com.tongpin.app.ui.theme.TpColors

@Composable
fun LobbyScreen(
    ui: AppUi,
    onOpenCreate: () -> Unit,
    onOpenJoin: () -> Unit,
    onSongClick: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(ui.songs, query) {
        if (query.isBlank()) ui.songs
        else ui.songs.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }

    TongpinBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Header(onOpenSettings) }
            item { Spacer(Modifier.height(24.dp)) }
            item { Hero() }
            item { Spacer(Modifier.height(20.dp)) }
            item { ActionCards(ui.songs.isNotEmpty(), onOpenCreate, onOpenJoin) }
            item { Spacer(Modifier.height(14.dp)) }
            item { ServerChip(ui.serverUrl, onOpenSettings) }
            item { Spacer(Modifier.height(30.dp)) }
            item {
                MusicHeader(
                    count = ui.songs.size,
                    query = query,
                    onQueryChange = { query = it },
                    onRefresh = onRefresh,
                    loading = ui.libraryLoading,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }

            when {
                !ui.hasAudioPermission -> item {
                    PermissionCard(onRequestPermission)
                }

                ui.libraryLoading && ui.songs.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = TpColors.Peach,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                ui.libraryError != null -> item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "音乐库读取失败",
                                style = MaterialTheme.typography.titleMedium,
                                color = TpColors.Cream,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                ui.libraryError,
                                style = MaterialTheme.typography.bodySmall,
                                color = TpColors.Muted,
                            )
                        }
                    }
                }

                filtered.isEmpty() -> item {
                    EmptyLibrary(hasQuery = query.isNotBlank())
                }

                else -> items(filtered, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(TpColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            WaveMark(Modifier.size(24.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                "同频",
                style = MaterialTheme.typography.titleLarge,
                color = TpColors.Cream,
                fontWeight = FontWeight.Black,
            )
            Text(
                "LISTEN TOGETHER",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
                letterSpacing = 1.4.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "设置",
                tint = TpColors.CreamMuted,
            )
        }
    }
}

@Composable
private fun Hero() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "SYNCED LISTENING",
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.Peach,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "把耳机\n分你一半。",
            style = MaterialTheme.typography.displayMedium,
            color = TpColors.Cream,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "你的本地音乐，两个人或一群人，\n在同一秒听见同一句。",
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.Muted,
        )
        Spacer(Modifier.height(18.dp))
        HeroArt()
    }
}

@Composable
private fun HeroArt() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        TpColors.SurfaceHigh,
                        Color(0xFF2B1C1E),
                        TpColors.InkSoft,
                    )
                )
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.82f, size.height * 0.52f)
            val maxRadius = size.height * 0.78f
            for (i in 1..7) {
                drawCircle(
                    color = TpColors.Cream.copy(alpha = 0.045f + i * 0.006f),
                    radius = maxRadius * i / 7f,
                    center = center,
                    style = Stroke(width = 1.1f),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(TpColors.Peach.copy(alpha = 0.85f), TpColors.PeachDeep),
                    center = center,
                    radius = maxRadius * 0.32f,
                ),
                radius = maxRadius * 0.32f,
                center = center,
            )
            drawCircle(
                color = TpColors.Ink,
                radius = maxRadius * 0.035f,
                center = center,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TpColors.Mint)
                )
                Text(
                    "LIVE · 一起听",
                    style = MaterialTheme.typography.labelSmall,
                    color = TpColors.Mint,
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "同一首歌，\n同一个节拍。",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "2 人起，最多 32 人",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
            )
        }
    }
}

@Composable
private fun ActionCards(
    hasMusic: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1.15f)
                .height(92.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(TpColors.Peach, TpColors.PeachDeep)
                    )
                )
                .clickable(onClick = onCreate)
                .padding(16.dp),
        ) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = TpColors.Ink,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "创建房间",
                    style = MaterialTheme.typography.titleLarge,
                    color = TpColors.Ink,
                )
                Text(
                    if (hasMusic) "用一首歌开始" else "先扫描本地音乐",
                    style = MaterialTheme.typography.bodySmall,
                    color = TpColors.Ink.copy(alpha = 0.64f),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(0.85f)
                .height(92.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TpColors.Surface.copy(alpha = 0.8f))
                .clickable(onClick = onJoin)
                .padding(16.dp),
        ) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Icon(
                    Icons.Rounded.Login,
                    contentDescription = null,
                    tint = TpColors.Mint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "加入房间",
                    style = MaterialTheme.typography.titleLarge,
                    color = TpColors.Cream,
                )
                Text(
                    "输入 6 位房间号",
                    style = MaterialTheme.typography.bodySmall,
                    color = TpColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun ServerChip(serverUrl: String, onClick: () -> Unit) {
    val configured = serverUrl.isNotBlank()
    val host = remember(serverUrl) {
        serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TpColors.Surface.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (configured) TpColors.Mint else TpColors.Gold)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = if (configured) "房间服务器 · $host" else "还没有连接房间服务器",
            style = MaterialTheme.typography.labelMedium,
            color = if (configured) TpColors.CreamMuted else TpColors.Gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (configured) "更改" else "去设置",
            style = MaterialTheme.typography.labelMedium,
            color = TpColors.Peach,
        )
        Icon(
            Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TpColors.Peach,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MusicHeader(
    count: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "我的音乐",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = TpColors.Muted,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = TpColors.Peach,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "刷新音乐库",
                        tint = TpColors.Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TpColors.Surface.copy(alpha = 0.56f))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = TpColors.Muted,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "搜索歌曲、艺术家、专辑",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TpColors.Muted,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TpColors.Cream),
                    cursorBrush = SolidColor(TpColors.Peach),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaveMark(Modifier.size(44.dp), color = TpColors.Peach)
            Spacer(Modifier.height(14.dp))
            Text(
                "让我看看你的音乐",
                style = MaterialTheme.typography.titleLarge,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "同频只读取本机音频的名称、封面和文件，\n不会修改或上传到别处。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TpColors.Peach,
                    contentColor = TpColors.Ink,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text("允许读取本地音乐", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(hasQuery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WaveMark(Modifier.size(36.dp), color = TpColors.SurfaceHigh, accent = TpColors.Muted)
        Spacer(Modifier.height(14.dp))
        Text(
            if (hasQuery) "没有找到这首歌" else "音乐库还是空的",
            style = MaterialTheme.typography.titleMedium,
            color = TpColors.CreamMuted,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            if (hasQuery) "换个关键词试试" else "把 MP3 / FLAC 放进手机后刷新一下",
            style = MaterialTheme.typography.bodySmall,
            color = TpColors.Muted,
        )
    }
}
