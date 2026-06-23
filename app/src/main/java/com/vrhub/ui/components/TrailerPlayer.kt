package com.vrhub.ui.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.vrhub.R
import com.vrhub.logic.TrailerUtils

private const val TAG = "TrailerPlayer"

/**
 * Streaming trailer player (Story 11.2).
 *
 * Renders an inline 16:9 YouTube IFrame player for [trailerUrl] plus an "open externally" action.
 * The video is **streamed**, never downloaded. Playback uses the official YouTube IFrame Player
 * (per YouTube ToS). The IFrame UI exposes a fullscreen button that fires [FullscreenListener];
 * we honour it by re-parenting the player into a full-screen Compose [Dialog] — important for the
 * VR/Quest use case where a large view matters.
 *
 * Degradation: if [trailerUrl] does not yield a valid YouTube ID, nothing is rendered. If the
 * embedded WebView cannot initialise (no system WebView), the user can still tap "open externally"
 * to launch the system browser / YouTube app.
 */
@Composable
fun TrailerPlayer(
    trailerUrl: String?,
    modifier: Modifier = Modifier
) {
    val videoId = remember(trailerUrl) { TrailerUtils.extractYouTubeId(trailerUrl) }
    // No parseable YouTube ID → no trailer UI at all.
    if (videoId == null) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The view handed to us by the IFrame fullscreen callback. When non-null we host it in a Dialog.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    // Set to true once the player has loaded the cued video so we can auto-resume after fullscreen.
    var playerReady by remember { mutableStateOf(false) }
    // True if the embedded WebView failed to initialise (no system WebView) → only external open works.
    var playerFailed by remember { mutableStateOf(false) }

    val openExternally: () -> Unit = {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open trailer externally: $trailerUrl", e)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (playerFailed) {
            // Graceful degradation: no embedded player available → offer external launch only.
            TrailerExternalFallback(onOpenExternally = openExternally)
        } else {
            TrailerInlinePlayer(
                videoId = videoId,
                lifecycleOwner = lifecycleOwner,
                onFullscreenEnter = { view -> fullscreenView = view },
                onFullscreenExit = { fullscreenView = null },
                onReady = { playerReady = true },
                onError = { playerFailed = true },
                onOpenExternally = openExternally
            )
        }
    }

    // Fullscreen overlay: host the view supplied by the IFrame fullscreen callback edge-to-edge.
    val fsView = fullscreenView
    if (fsView != null) {
        Dialog(
            onDismissRequest = { fullscreenView = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { container ->
                    if (container.childCount == 0 || container.getChildAt(0) !== fsView) {
                        (fsView.parent as? android.view.ViewGroup)?.removeView(fsView)
                        container.removeAllViews()
                        container.addView(
                            fsView,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}

/**
 * Inline 16:9 player + a small action row ("open externally"). Owns the single
 * [YouTubePlayerView] instance and binds it to the host lifecycle for correct pause/release.
 */
@Composable
private fun TrailerInlinePlayer(
    videoId: String,
    lifecycleOwner: LifecycleOwner,
    onFullscreenEnter: (View) -> Unit,
    onFullscreenExit: () -> Unit,
    onReady: () -> Unit,
    onError: () -> Unit,
    onOpenExternally: () -> Unit
) {
    val context = LocalContext.current

    // Build a single YouTubePlayerView and keep it across recompositions.
    val playerView = remember {
        try {
            YouTubePlayerView(context).apply {
                // We initialise manually so we can pass IFramePlayerOptions + a FullscreenListener.
                enableAutomaticInitialization = false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "YouTubePlayerView could not be created (no WebView?)", e)
            onError()
            null
        }
    }

    // Stable non-null reference so closures (DisposableEffect / AndroidView) smart-cast cleanly.
    val view = playerView ?: return

    DisposableEffect(view, videoId) {
        var initialised = false
        try {
            view.addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                    onFullscreenEnter(fullscreenView)
                }

                override fun onExitFullscreen() {
                    onFullscreenExit()
                }
            })

            val options = IFramePlayerOptions.Builder()
                .controls(1)
                .fullscreen(1) // expose the IFrame fullscreen button → fires FullscreenListener
                .build()

            playerView.initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    // cueVideo: load but do NOT autoplay — streaming starts on user tap.
                    youTubePlayer.cueVideo(videoId, 0f)
                    onReady()
                }

                override fun onError(
                    youTubePlayer: YouTubePlayer,
                    error: PlayerConstants.PlayerError
                ) {
                    Log.w(TAG, "YouTube player error for $videoId: $error")
                }
            }, options)

            // Bind to the host lifecycle so the player pauses/releases with the screen.
            lifecycleOwner.lifecycle.addObserver(playerView)
            initialised = true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialise YouTube player", e)
            onError()
        }

        onDispose {
            try {
                if (initialised) {
                    lifecycleOwner.lifecycle.removeObserver(playerView)
                }
                playerView.release()
            } catch (e: Throwable) {
                Log.w(TAG, "Error releasing YouTube player", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onOpenExternally) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color(0xFF3498db),
                modifier = Modifier.height(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = context.getString(R.string.trailer_open_externally),
                color = Color(0xFF3498db),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Shown when the embedded WebView is unavailable: a single tappable card that launches the
 * trailer in the system browser / YouTube app (works on Quest).
 */
@Composable
private fun TrailerExternalFallback(onOpenExternally: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .clickable { onOpenExternally() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color(0xFF3498db),
                modifier = Modifier.height(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = context.getString(R.string.trailer_open_externally),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
