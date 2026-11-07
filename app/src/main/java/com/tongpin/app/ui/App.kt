package com.tongpin.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tongpin.app.AppScreen
import com.tongpin.app.AppUi
import com.tongpin.app.BusyState
import com.tongpin.app.MainViewModel
import com.tongpin.app.ui.components.WaveMark
import com.tongpin.app.ui.screens.JoinScreen
import com.tongpin.app.ui.screens.LobbyScreen
import com.tongpin.app.ui.screens.RoomScreen
import com.tongpin.app.ui.sheets.CreateRoomSheet
import com.tongpin.app.ui.sheets.SettingsSheet
import com.tongpin.app.ui.sheets.SongPickerSheet
import com.tongpin.app.ui.theme.TpColors
import com.tongpin.app.ui.theme.TongpinTheme
import kotlinx.coroutines.launch

@Composable
fun TongpinAppRoot(viewModel: MainViewModel) {
    TongpinTheme {
        val ui by viewModel.ui.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }

        var showSettings by remember { mutableStateOf(false) }
        var showPicker by remember { mutableStateOf(false) }
        var createSong by remember { mutableStateOf<com.tongpin.app.data.Song?>(null) }

        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            viewModel.onAudioPermission(granted)
            if (!granted) {
                scope.launch {
                    snackbar.showSnackbar("需要音频权限才能扫描本地音乐，可在系统设置里重新允许。")
                }
            }
        }
        val notificationLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* The foreground service still works if this is denied. */ }

        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
            viewModel.onAudioPermission(granted)
            if (!granted) permissionLauncher.launch(audioPermission)
        }
        LaunchedEffect(Unit) {
            viewModel.toasts.collect { message -> snackbar.showSnackbar(message) }
        }
        LaunchedEffect(ui.screen) {
            if (ui.screen == AppScreen.Room) {
                showPicker = false
                createSong = null
                showSettings = false
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Crossfade(
                targetState = ui.screen,
                animationSpec = tween(240),
                label = "screens",
            ) { screen ->
                when (screen) {
                    AppScreen.Lobby -> LobbyScreen(
                        ui = ui,
                        onOpenCreate = {
                            when {
                                !ui.hasAudioPermission -> permissionLauncher.launch(audioPermission)
                                ui.songs.isEmpty() -> scope.launch {
                                    snackbar.showSnackbar("先往手机里放几首歌，或者点「我的音乐」右侧刷新。")
                                }
                                else -> showPicker = true
                            }
                        },
                        onOpenJoin = { viewModel.openJoin() },
                        onSongClick = { song -> createSong = song },
                        onOpenSettings = { showSettings = true },
                        onRefresh = { viewModel.loadLibrary(force = true) },
                        onRequestPermission = { permissionLauncher.launch(audioPermission) },
                    )

                    AppScreen.Join -> JoinScreen(
                        ui = ui,
                        onBack = viewModel::backToLobby,
                        onJoin = viewModel::joinRoom,
                        onTestServer = viewModel::testServer,
                    )

                    AppScreen.Room -> RoomScreen(
                        ui = ui,
                        reactions = viewModel.reactions,
                        onLeave = viewModel::leaveRoom,
                        onTogglePlay = viewModel::togglePlay,
                        onSeek = viewModel::seekTo,
                        onSkip = viewModel::skipBy,
                        onSendChat = viewModel::sendChat,
                        onReaction = viewModel::sendReaction,
                        onChangeTrack = viewModel::changeTrack,
                    )
                }
            }

            if (showSettings) {
                SettingsSheet(
                    ui = ui,
                    onDismiss = { showSettings = false },
                    onSave = { nickname, server -> viewModel.saveSettings(nickname, server) },
                    onTest = viewModel::testServer,
                )
            }

            if (showPicker) {
                SongPickerSheet(
                    songs = ui.songs,
                    onDismiss = { showPicker = false },
                    onSelect = { song ->
                        showPicker = false
                        createSong = song
                    },
                )
            }

            createSong?.let { song ->
                CreateRoomSheet(
                    ui = ui,
                    song = song,
                    onDismiss = { if (ui.busy == null) createSong = null },
                    onCreate = { selected, nickname, server ->
                        viewModel.createRoom(selected, nickname, server)
                        createSong = null
                    },
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = TpColors.SurfaceHigh,
                    contentColor = TpColors.Cream,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            ui.busy?.let { busy ->
                BusyOverlay(busy)
            }
        }
    }
}

@Composable
private fun BusyOverlay(busy: BusyState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* swallow touches */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(28.dp))
                .background(TpColors.Surface)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            WaveMark(Modifier.size(44.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                busy.title,
                style = MaterialTheme.typography.titleLarge,
                color = TpColors.Cream,
                textAlign = TextAlign.Center,
            )
            busy.detail?.let { detail ->
                Spacer(Modifier.height(7.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TpColors.Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(22.dp))
            if (busy.progress == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = TpColors.Peach,
                    strokeWidth = 2.dp,
                )
            } else {
                LinearProgressIndicator(
                    progress = { busy.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = TpColors.Peach,
                    trackColor = TpColors.Cream.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(busy.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TpColors.Muted,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "请保持屏幕亮着",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Muted.copy(alpha = 0.7f),
                letterSpacing = 0.6.sp,
            )
        }
    }
}
