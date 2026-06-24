package com.vrhub.ui.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vrhub.R
import com.vrhub.logic.TrailerUtils

private const val TAG = "TrailerPlayer"

/**
 * Streaming trailer affordance (Story 11.2).
 *
 * Renders the game's YouTube trailer as a 16:9 **thumbnail with a play overlay**. Tapping it
 * launches the trailer in the system YouTube app / browser via [Intent.ACTION_VIEW] — the video
 * is **streamed there, never downloaded**, and plays full-screen natively (which is also the
 * comfortable path on a Quest headset).
 *
 * Why not an in-app IFrame player? YouTube's 2025-07-09 embedder-identity enforcement blocks
 * embedded WebView playback ("Video unavailable", error 150/152/153) for every video unless the
 * player can present a verifiable HTTP referer. The fix in androidyoutubeplayer 13.x requires a
 * Kotlin-2.1 toolchain this app is not on yet, so inline embedding is not viable today. External
 * playback is reliable, ToS-compliant, and immersive on VR. If the toolchain is later upgraded,
 * an inline player can be reintroduced.
 *
 * A non-parseable [trailerUrl] renders nothing (no trailer UI), so callers can pass it
 * unconditionally.
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
    // hqdefault.jpg always exists for a valid video (unlike maxresdefault.jpg).
    val thumbnailUrl = remember(videoId) { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable(onClick = openExternally),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.trailer_label),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Dim scrim so the play glyph stays legible over bright thumbnails.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = stringResource(R.string.trailer_open_externally),
            tint = Color.White,
            modifier = Modifier.size(56.dp)
        )
    }
}
