package com.tongpin.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.tongpin.app.AppUi
import com.tongpin.app.data.Song
import com.tongpin.app.ui.components.CoverArt
import com.tongpin.app.ui.components.SongRow
import com.tongpin.app.ui.components.WaveMark
import com.tongpin.app.ui.theme.TpColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    ui: AppUi,
    onDismiss: () -> Unit,
    onSave: (nickname: String, server: String) -> Unit,
    onTest: (server: String) -> Unit,
) {
    var nickname by remember { mutableStateOf(ui.nickname) }
    var server by remember { mutableStateOf(ui.serverUrl) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val configured = server.isNotBlank()
    val host = server.removePrefix("https://").removePrefix("http://").trimEnd('/')

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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "房间设置",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "只要大家填同一个服务器地址，就能在同一个房间里。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
            )
            Spacer(Modifier.height(22.dp))
            SheetField(
                label = "你的名字",
                value = nickname,
                placeholder = "晚风",
                onValueChange = { nickname = it.take(20) },
            )
            Spacer(Modifier.height(16.dp))
            SheetField(
                label = "房间服务器",
                value = server,
                placeholder = "192.168.1.10:8080",
                onValueChange = { server = it },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TpColors.Surface.copy(alpha = 0.5f))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
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
                    if (configured) "将连接 · $host" else "还没有填写服务器",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (configured) TpColors.CreamMuted else TpColors.Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (ui.testingServer) "连接中…" else "测试",
                    style = MaterialTheme.typography.labelMedium,
                    color = TpColors.Mint,
                    modifier = Modifier
                        .clickable(enabled = !ui.testingServer) { onTest(server) }
                        .padding(6.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TpColors.Surface.copy(alpha = 0.45f))
                    .padding(17.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaveMark(Modifier.size(22.dp), color = TpColors.Peach)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "还没有服务器？",
                        style = MaterialTheme.typography.titleMedium,
                        color = TpColors.Cream,
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    "在电脑上双击「启动同频服务器.bat」，或在任意一台能联网的电脑 / VPS 上运行：",
                    style = MaterialTheme.typography.bodySmall,
                    color = TpColors.Muted,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TpColors.Ink)
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "node server.js 8080",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TpColors.Mint,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "复制命令",
                        tint = TpColors.Muted,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                clipboard.setText(AnnotatedString("node server.js 8080"))
                                Toast.makeText(context, "命令已复制", Toast.LENGTH_SHORT).show()
                            },
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    "同一 Wi-Fi：填电脑的局域网 IP，例如 192.168.1.10:8080。\n" +
                        "异地：把服务器部署到有公网 IP 的机器，并填它的地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TpColors.Muted,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    onSave(nickname, server)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TpColors.Peach,
                    contentColor = TpColors.Ink,
                ),
            ) {
                Text("保存设置", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerSheet(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSelect: (Song) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "选一首歌",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "这首歌会上传到房间服务器，所有人同步播放。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(TpColors.Surface.copy(alpha = 0.62f))
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
                            "搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TpColors.Muted,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TpColors.Cream),
                        cursorBrush = SolidColor(TpColors.Peach),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
            ) {
                items(filtered, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { onSelect(song) },
                        trailing = {
                            Text(
                                "选它",
                                style = MaterialTheme.typography.labelMedium,
                                color = TpColors.Peach,
                            )
                        },
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomSheet(
    ui: AppUi,
    song: Song,
    onDismiss: () -> Unit,
    onCreate: (song: Song, nickname: String, server: String) -> Unit,
) {
    var nickname by remember { mutableStateOf(ui.nickname) }
    var server by remember { mutableStateOf(ui.serverUrl) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canCreate = server.isNotBlank() && nickname.isNotBlank() && ui.busy == null

    ModalBottomSheet(
        onDismissRequest = { if (ui.busy == null) onDismiss() },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "创建房间",
                style = MaterialTheme.typography.headlineMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TpColors.Surface.copy(alpha = 0.55f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    model = song.artworkUri,
                    modifier = Modifier.size(62.dp),
                    shape = RoundedCornerShape(15.dp),
                    fallbackSeed = song.title,
                )
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TpColors.Cream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${song.artistOrUnknown} · ${song.albumOrUnknown}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TpColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SheetField(
                label = "你的名字",
                value = nickname,
                placeholder = "房主",
                onValueChange = { nickname = it.take(20) },
            )
            Spacer(Modifier.height(16.dp))
            SheetField(
                label = "房间服务器",
                value = server,
                placeholder = "192.168.1.10:8080",
                onValueChange = { server = it },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "创建后会先把这首歌上传到服务器，其他人用房间号加入。文件只在房间存在期间保存。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = { onCreate(song, nickname, server) },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TpColors.Peach,
                    contentColor = TpColors.Ink,
                    disabledContainerColor = TpColors.SurfaceHigh,
                    disabledContentColor = TpColors.Muted,
                ),
            ) {
                if (ui.busy != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TpColors.Ink,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "开始一起听",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "创建即代表你确认有权分享这首音乐给房间里的朋友。",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.Muted,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(TpColors.Surface.copy(alpha = 0.72f))
                .padding(horizontal = 15.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TpColors.Muted.copy(alpha = 0.62f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TpColors.Cream),
                cursorBrush = SolidColor(TpColors.Peach),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
