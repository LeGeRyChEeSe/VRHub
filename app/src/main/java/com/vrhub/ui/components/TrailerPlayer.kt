package com.vrhub.ui.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vrhub.R
import com.vrhub.logic.TrailerUtils

private const val TAG = "TrailerPlayer"

/**
 * Streaming trailer affordance (Story 11.2 / 11.3).
 *
 * Tapping it opens the trailer in the system YouTube app / browser via
 * [Intent.ACTION_VIEW] — streamed there, never downloaded, full-screen natively
 * (the comfortable path on a Quest). Two shapes, depending on what the server
 * resolved for the game (hybrid policy):
 *   - **specific video** (`watch?v=` / `youtu.be/`): a 16:9 YouTube thumbnail
 *     with a play overlay;
 *   - **search link** (`youtube.com/results?...`, the zero-config fallback) or
 *     any other YouTube URL with no parseable video id: a generic "watch
 *     trailer" card.
 *
 * Why not an in-app IFrame player? YouTube's 2025-07-09 embedder-identity
 * enforcement blocks embedded WebView playback for every video; the library fix
 * needs a Kotlin-2.1 toolchain this app is not on. See the project notes.
 *
 * A blank [trailerUrl] renders nothing, so callers can pass it unconditionally.
 */
@Composable
fun TrailerPlayer(
    trailerUrl: String?,
    modifier: Modifier = Modifier
) {
    if (trailerUrl.isNullOrBlank()) return

    val context = LocalContext.current
    val videoId = remember(trailerUrl) { TrailerUtils.extractYouTubeId(trailerUrl) }

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

    if (videoId != null) {
        // Specific video → rich thumbnail card. hqdefault.jpg always exists.
        val thumbnailUrl = remember(videoId) { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }
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
    } else {
        // Search link / non-video YouTube URL → generic "watch trailer" card.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
                .clickable(onClick = openExternally),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color(0xFF3498db),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.trailer_label),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
