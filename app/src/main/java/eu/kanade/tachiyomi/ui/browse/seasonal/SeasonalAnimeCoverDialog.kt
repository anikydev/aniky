package eu.kanade.tachiyomi.ui.browse.seasonal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toDrawable
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

@Composable
fun SeasonalAnimeCoverDialog(
    coverUrl: String,
    bannerUrl: String? = null,
    title: String,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showingCover by remember { mutableStateOf(true) }

    val currentUrl = if (showingCover || bannerUrl.isNullOrBlank()) coverUrl else bannerUrl

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = {
                    ReaderPageImageView(it).apply {
                        onViewClicked = onDismissRequest
                        clipToPadding = false
                        clipChildren = false
                    }
                },
                update = { view ->
                    val req = ImageRequest.Builder(context)
                        .data(currentUrl)
                        .size(Size.ORIGINAL)
                        .target { image ->
                            val drawable = image.asDrawable(context.resources)
                            val src = (drawable as? BitmapDrawable)?.bitmap
                            val config = src?.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
                            val copy = src?.copy(config, false)?.toDrawable(context.resources) ?: drawable
                            view.setImage(copy, ReaderPageImageView.Config(zoomDuration = 500))
                        }
                        .build()
                    context.imageLoader.enqueue(req)
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom controls overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left pill: Close and optional Cover/Banner switch
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_close),
                            )
                        }

                        if (!bannerUrl.isNullOrBlank()) {
                            IconButton(onClick = { showingCover = !showingCover }) {
                                Icon(
                                    imageVector = if (showingCover) {
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                                    } else {
                                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft
                                    },
                                    contentDescription = stringResource(
                                        if (showingCover) AMR.strings.seasonal_switch_banner else AMR.strings.seasonal_switch_cover,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Right pill: Share and Save
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val uri = saveImageInternal(context, currentUrl, title, temp = true)
                                        if (uri != null) {
                                            context.startActivity(uri.toShareIntent(context))
                                        } else {
                                            val intent = Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                                },
                                                null,
                                            )
                                            context.startActivity(intent)
                                        }
                                    } catch (e: Exception) {
                                        logcat(LogPriority.ERROR, e) { "Failed to share cover" }
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(MR.strings.action_share),
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val uri = saveImageInternal(context, currentUrl, title, temp = false)
                                        if (uri != null) {
                                            context.toast(context.contextStringResource(AMR.strings.seasonal_cover_saved))
                                        } else {
                                            context.toast(context.contextStringResource(AMR.strings.seasonal_cover_save_error))
                                        }
                                    } catch (e: Exception) {
                                        logcat(LogPriority.ERROR, e) { "Failed to save cover" }
                                        context.toast(context.contextStringResource(AMR.strings.seasonal_cover_save_error))
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = stringResource(MR.strings.action_save),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveImageInternal(context: Context, url: String, title: String, temp: Boolean): Uri? {
    val req = ImageRequest.Builder(context)
        .data(url)
        .size(Size.ORIGINAL)
        .build()

    return withContext(Dispatchers.IO) {
        val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)
        val bitmap = result?.getBitmapOrNull() ?: return@withContext null
        val imageSaver = Injekt.get<ImageSaver>()
        imageSaver.save(
            Image.Cover(
                bitmap = bitmap,
                name = "$title-cover",
                location = if (temp) Location.Cache else Location.Pictures.create(),
            ),
        )
    }
}
