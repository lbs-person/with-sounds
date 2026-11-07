package com.tongpin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.tongpin.app.AppUi
import com.tongpin.app.ui.components.TongpinBackground
import com.tongpin.app.ui.components.WaveMark
import com.tongpin.app.ui.theme.TpColors
import android.widget.Toast

@Composable
fun JoinScreen(
    ui: AppUi,
    onBack: () -> Unit,
    onJoin: (code: String, nickname: String, server: String) -> Unit,
    onTestServer: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf(ui.prefillCode.orEmpty().filter(Char::isDigit).take(6)) }
    var nickname by rememberSaveable { mutableStateOf(ui.nickname) }
    var server by rememberSaveable {
        mutableStateOf(ui.prefillServer?.takeIf { it.isNotBlank() } ?: ui.serverUrl)
    }
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val canJoin = code.length == 6 && server.isNotBlank() && ui.busy == null

    BackHandler { onBack() }

    LaunchedEffect(ui.prefillCode, ui.prefillServer) {
        ui.prefillCode?.takeIf { it.length == 6 }?.let { code = it }
        ui.prefillServer?.takeIf { it.isNotBlank() }?.let { server = it }
    }

    TongpinBackground(accent = TpColors.Mint) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = TpColors.Cream,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    WaveMark(Modifier.size(18.dp), color = TpColors.Mint)
                    Text(
                        "同频",
                        style = MaterialTheme.typography.labelLarge,
                        color = TpColors.CreamMuted,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "加入\n一场一起听",
                style = MaterialTheme.typography.displayMedium,
                color = TpColors.Cream,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "向房主要一个 6 位房间号，\n你们听到的会是同一秒。",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.Muted,
            )
            Spacer(Modifier.height(34.dp))
            Text(
                "房间号",
                style = MaterialTheme.typography.labelSmall,
                color = TpColors.Peach,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(6) { index ->
                    val digit = code.getOrNull(index)?.toString().orEmpty()
                    val active = index == code.length && code.length < 6
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (digit.isNotEmpty()) TpColors.SurfaceHigh
                                else TpColors.Surface.copy(alpha = 0.55f)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { focusRequester.requestFocus() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TpColors.Cream,
                        )
                        if (active) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth(0.36f)
                                    .height(2.dp)
                                    .background(TpColors.Peach, CircleShape)
                            )
                        }
                    }
                }
            }
            BasicTextField(
                value = code,
                onValueChange = { value ->
                    code = value.filter(Char::isDigit).take(6)
                },
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                ),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.Transparent),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "从聊天里粘贴",
                    style = MaterialTheme.typography.labelMedium,
                    color = TpColors.Muted,
                    modifier = Modifier.clickable {
                        val text = clipboard.getText()?.text.orEmpty()
                        val digits = text.filter(Char::isDigit)
                        val six = Regex("(?<!\\d)\\d{6}(?!\\d)").find(digits)?.value
                        if (six != null) {
                            code = six
                        } else {
                            Toast.makeText(context, "剪贴板里没有 6 位房间号", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.ContentPaste,
                    contentDescription = null,
                    tint = TpColors.Muted,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.height(30.dp))
            FormField(
                label = "你的名字",
                value = nickname,
                placeholder = "房间里怎么称呼你",
                onValueChange = { nickname = it.take(20) },
            )
            Spacer(Modifier.height(18.dp))
            FormField(
                label = "房间服务器",
                value = server,
                placeholder = "192.168.1.10:8080",
                onValueChange = { server = it },
                trailing = {
                    Text(
                        if (ui.testingServer) "连接中…" else "测试",
                        style = MaterialTheme.typography.labelMedium,
                        color = TpColors.Mint,
                        modifier = Modifier
                            .clickable(enabled = !ui.testingServer) { onTestServer(server) }
                            .padding(8.dp),
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "和房主填同一个服务器地址；同一 Wi-Fi 下通常是电脑的局域网 IP。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = { onJoin(code, nickname, server) },
                enabled = canJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TpColors.Mint,
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
                        "进入房间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "房主开始播放后，声音会在所有设备上同步。\n建议佩戴耳机，避免外放回声。",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.Muted,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TpColors.Surface.copy(alpha = 0.66f))
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TpColors.Muted.copy(alpha = 0.65f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TpColors.Cream),
                    cursorBrush = SolidColor(TpColors.Peach),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
    }
}
