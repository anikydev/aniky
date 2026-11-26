package eu.kanade.tachiyomi.ui.browse.seasonal

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MusicVideo
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.browse.seasonal.model.AnimeThemeSong
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder
import java.util.Locale

@Composable
fun ThemeSongPlayerSheet(
    song: AnimeThemeSong,
    onOpenYouTube: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasVideo = !song.videoUrl.isNullOrBlank()
    val hasAudio = !song.audioUrl.isNullOrBlank()
    var audioOnlyMode by remember(song) { mutableStateOf(!hasVideo && hasAudio) }
    val streamUrl = if (audioOnlyMode && hasAudio) song.audioUrl else (song.videoUrl ?: song.audioUrl)

    var isPrepared by remember(streamUrl) { mutableStateOf(false) }
    var isPlaying by remember(streamUrl) { mutableStateOf(false) }
    var hasError by remember(streamUrl) { mutableStateOf(streamUrl.isNullOrBlank()) }
    var currentPosMs by remember(streamUrl) { mutableIntStateOf(0) }
    var durationMs by remember(streamUrl) { mutableIntStateOf(0) }
    var isScrubbing by remember(streamUrl) { mutableStateOf(false) }
    var scrubPosMs by remember(streamUrl) { mutableIntStateOf(0) }

    val mediaPlayerHolder = remember { arrayOfNulls<MediaPlayer>(1) }
    val surfaceHolder = remember { arrayOfNulls<Surface>(1) }

    DisposableEffect(Unit) {
        onDispose {
            surfaceHolder[0]?.release()
            surfaceHolder[0] = null
        }
    }

    DisposableEffect(streamUrl) {
        if (streamUrl.isNullOrBlank()) {
            return@DisposableEffect onDispose {}
        }
        val player = MediaPlayer()
        mediaPlayerHolder[0] = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            surfaceHolder[0]?.takeIf { it.isValid && !audioOnlyMode }?.let { player.setSurface(it) }
            player.setDataSource(
                context,
                Uri.parse(streamUrl),
                mapOf(
                    "Referer" to "https://animethemes.moe/",
                    "User-Agent" to "Aniky/1.0 (https://github.com/anikydev/aniky)",
                ),
            )
            player.setOnPreparedListener { mp ->
                isPrepared = true
                durationMs = mp.duration.coerceAtLeast(0)
                runCatching {
                    mp.start()
                    isPlaying = true
                }
            }
            player.setOnCompletionListener {
                isPlaying = false
                currentPosMs = durationMs
            }
            player.setOnErrorListener { _, _, _ ->
                hasError = true
                isPlaying = false
                true
            }
            player.prepareAsync()
        }.onFailure {
            hasError = true
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                runCatching {
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaPlayerHolder[0] = null
            runCatching {
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
            }
            runCatching { player.setSurface(null) }
            if (isPrepared) {
                runCatching { player.stop() }
            }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(isPrepared, isPlaying, isScrubbing, streamUrl) {
        while (isPrepared && isPlaying && !isScrubbing) {
            val mp = mediaPlayerHolder[0]
            if (mp != null) {
                runCatching {
                    currentPosMs = mp.currentPosition.coerceAtLeast(0)
                    if (durationMs <= 0 && mp.duration > 0) {
                        durationMs = mp.duration
                    }
                }
            }
            delay(250L)
        }
    }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        enableSwipeDismiss = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicVideo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(song.label, song.artist).joinToString(" • ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (hasVideo && hasAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !audioOnlyMode,
                        onClick = { audioOnlyMode = false },
                        label = { Text(stringResource(AMR.strings.theme_song_video_preview)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(30.dp),
                    )
                    FilterChip(
                        selected = audioOnlyMode,
                        onClick = { audioOnlyMode = true },
                        label = { Text(stringResource(AMR.strings.theme_song_audio_only)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .then(
                        if (audioOnlyMode) {
                            Modifier.height(120.dp)
                        } else {
                            Modifier.aspectRatio(16f / 9f)
                        },
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .pureBlackBorder(RoundedCornerShape(12.dp))
                    .background(
                        if (audioOnlyMode) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Black
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!audioOnlyMode && !hasError) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surface: SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) {
                                        surfaceHolder[0]?.release()
                                        val s = Surface(surface)
                                        surfaceHolder[0] = s
                                        runCatching { mediaPlayerHolder[0]?.setSurface(s) }
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surface: SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) = Unit

                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        runCatching { mediaPlayerHolder[0]?.setSurface(null) }
                                        surfaceHolder[0]?.release()
                                        surfaceHolder[0] = null
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                                }
                            }
                        },
                    )
                } else if (audioOnlyMode && !hasError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(AMR.strings.theme_song_audio_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!isPrepared && !hasError) {
                    CircularProgressIndicator(
                        color = if (audioOnlyMode) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }

                if (hasError) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = if (audioOnlyMode) MaterialTheme.colorScheme.error else Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = stringResource(AMR.strings.theme_song_stream_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (audioOnlyMode) MaterialTheme.colorScheme.onSurface else Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onOpenYouTube) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(AMR.strings.theme_song_open_youtube))
                        }
                    }
                }
            }

            if (!hasError) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    val displayedPos = if (isScrubbing) scrubPosMs else currentPosMs
                    val sliderMax = durationMs.coerceAtLeast(1).toFloat()
                    Slider(
                        value = displayedPos.coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
                        onValueChange = { value ->
                            isScrubbing = true
                            scrubPosMs = value.toInt()
                        },
                        onValueChangeFinished = {
                            val target = scrubPosMs
                            runCatching {
                                mediaPlayerHolder[0]?.seekTo(target)
                                currentPosMs = target
                            }
                            isScrubbing = false
                        },
                        valueRange = 0f..sliderMax,
                        enabled = isPrepared && durationMs > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(
                            onClick = {
                                val mp = mediaPlayerHolder[0] ?: return@FilledIconButton
                                runCatching {
                                    if (mp.isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        if (durationMs > 0 && currentPosMs >= durationMs - 250) {
                                            mp.seekTo(0)
                                            currentPosMs = 0
                                        }
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            enabled = isPrepared,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) AMR.strings.theme_song_pause else AMR.strings.theme_song_play,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${formatDurationMs(displayedPos)} / ${formatDurationMs(durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = onOpenYouTube,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(AMR.strings.theme_song_open_youtube))
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
