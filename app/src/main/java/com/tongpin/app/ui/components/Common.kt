package com.tongpin.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.tongpin.app.data.Song
import com.tongpin.app.ui.theme.TpColors
import kotlin.math.roundToInt

@Composable
fun WaveMark(
    modifier: Modifier = Modifier,
    color: Color = TpColors.Peach,
    accent: Color = TpColors.Cream,
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.11f
        val gap = size.width * 0.075f
        val xs = floatArrayOf(0.12f, 0.34f, 0.56f, 0.78f)
        val heights = floatArrayOf(0.38f, 0.86f, 0.60f, 0.28f)
        xs.forEachIndexed { index, x ->
            val h = size.height * heights[index]
            drawRoundRect(
                color = if (index == 1) accent else color,
                topLeft = Offset(size.width * x, (size.height - h) / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
            )
        }
        // A tiny dot keeps the mark from feeling like a generic equalizer.
        drawCircle(
            color = color,
            radius = gap * 0.32f,
            center = Offset(size.width * 0.95f, size.height * 0.5f),
        )
    }
}

@Composable
fun TongpinBackground(
    modifier: Modifier = Modifier,
    accent: Color = TpColors.Peach,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        TpColors.InkSoft,
                        TpColors.Ink,
                        Color(0xFF090709),
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.06f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.82f, size.height * 0.06f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(TpColors.Mint.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.82f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.05f, size.height * 0.82f),
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = TpColors.Surface.copy(alpha = 0.66f),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, TpColors.Line),
        content = content,
    )
}

@Composable
fun CoverArt(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    fallbackSeed: String = "",
    fallbackColor: Color = TpColors.Peach,
) {
    val context = LocalContext.current
    val localRes = remember(model) { localDrawableId(context, model) }
    Box(modifier = modifier.clip(shape)) {
        when {
            localRes != null -> {
                // Preview/test-friendly and avoids an async round trip when a
                // caller already has a local drawable resource.
                Image(
                    painter = painterResource(localRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            model == null -> CoverPlaceholder(fallbackSeed, fallbackColor)
            else -> {
                val request = remember(model) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(220)
                        .build()
                }
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { CoverPlaceholder(fallbackSeed, fallbackColor) },
                    error = { CoverPlaceholder(fallbackSeed, fallbackColor) },
                    success = { SubcomposeAsyncImageContent() },
                )
            }
        }
    }
}

private fun localDrawableId(context: android.content.Context, model: Any?): Int? {
    if (model is Int) return model.takeIf { it != 0 }
    val uri = when (model) {
        is android.net.Uri -> model
        is String -> runCatching { android.net.Uri.parse(model) }.getOrNull()
        else -> null
    } ?: return null
    if (uri.scheme != "android.resource") return null
    val segments = uri.pathSegments
    val host = uri.host ?: context.packageName
    if (segments.size >= 2) {
        return context.resources.getIdentifier(segments[1], segments[0], host)
            .takeIf { it != 0 }
    }
    return uri.lastPathSegment?.toIntOrNull()?.takeIf { it != 0 }
}

@Composable
private fun CoverPlaceholder(seed: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = 0.82f),
                        TpColors.SurfaceHigh,
                        TpColors.InkSoft,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        WaveMark(
            modifier = Modifier.fillMaxSize(0.5f),
            color = TpColors.Ink.copy(alpha = 0.78f),
            accent = TpColors.Cream.copy(alpha = 0.9f),
        )
        if (seed.isNotBlank()) {
            Text(
                text = seed.take(1),
                color = Color.White.copy(alpha = 0.16f),
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

@Composable
fun Avatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    borderColor: Color = TpColors.Ink,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).ifBlank { "♪" },
            color = TpColors.Ink,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

@Composable
fun LiveBars(
    modifier: Modifier = Modifier,
    color: Color = TpColors.Mint,
    playing: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(780, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "barsPhase",
    )
    Canvas(modifier = modifier) {
        val widths = size.width * 0.13f
        val gap = (size.width - widths * 4) / 3f
        for (i in 0..3) {
            val base = if (playing) {
                (0.28f + 0.62f * kotlin.math.abs(kotlin.math.sin((phase + i * 0.19f) * Math.PI * 2)).toFloat())
            } else {
                0.22f
            }
            val h = size.height * base
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (widths + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(widths, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(widths / 2),
            )
        }
    }
}

@Composable
fun SyncBadge(rttMs: Long?, connected: Boolean) {
    val color = when {
        !connected -> TpColors.Gold
        rttMs == null -> TpColors.CreamMuted
        rttMs < 120 -> TpColors.Mint
        else -> TpColors.Gold
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TpColors.Ink.copy(alpha = 0.38f))
            .border(1.dp, TpColors.Line, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = when {
                !connected -> "连接中"
                rttMs == null -> "对齐中"
                else -> "同步 ${rttMs}ms"
            },
            style = MaterialTheme.typography.labelSmall,
            color = TpColors.CreamMuted,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            model = song.artworkUri,
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(14.dp),
            fallbackSeed = song.title,
            fallbackColor = colorForId(song.id),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = TpColors.Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artistOrUnknown} · ${song.albumOrUnknown}",
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = TpColors.Muted,
            )
        }
    }
}

fun colorForId(id: Long): Color {
    val palette = listOf(
        TpColors.Peach, TpColors.Mint, TpColors.Rose, TpColors.Gold,
        Color(0xFFA8A6FF), Color(0xFF79C7FF),
    )
    return palette[(id % palette.size).toInt()]
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatTime(ms: Long): String = formatDuration(ms)

fun progressPercent(position: Long, duration: Long): Int {
    if (duration <= 0) return 0
    return ((position.toFloat() / duration) * 100).roundToInt().coerceIn(0, 100)
}
