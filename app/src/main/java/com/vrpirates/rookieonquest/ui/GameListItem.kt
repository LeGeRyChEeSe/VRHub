package com.vrpirates.rookieonquest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vrpirates.rookieonquest.R
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.size.Precision
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GameListItem(
    game: GameItemState,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onDownloadOnlyClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onToggleFavorite: (Boolean) -> Unit = {},
    isGridItem: Boolean = false,
    permissionsMissing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val context = LocalContext.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else if (isHovered) 1.02f else 1f,
        label = "scale"
    )
    
    val isPaused = game.queueStatus == InstallTaskStatus.PAUSED
    val isShelved = game.queueStatus == InstallTaskStatus.SHELVED || game.queueStatus == InstallTaskStatus.PENDING_INSTALL
    val isProcessing = game.queueStatus?.isProcessing() == true 
    val isQueued = game.queueStatus == InstallTaskStatus.QUEUED
    val canResume = isPaused && game.isFirstInQueue

    val buttonColor = when {
        canResume -> Color(0xFF2ecc71)
        isShelved -> Color(0xFF2ecc71) // Green for ready to install
        game.installStatus == InstallStatus.INSTALLED -> Color(0xFF3498db)
        game.installStatus == InstallStatus.UPDATE_AVAILABLE -> Color(0xFF2ecc71)
        else -> MaterialTheme.colorScheme.secondary
    }
    
    val buttonText = when {
        canResume -> context.getString(R.string.game_btn_resume)
        isShelved -> context.getString(R.string.game_btn_complete_install)
        isQueued -> context.getString(R.string.game_btn_in_queue)
        isProcessing -> context.getString(R.string.game_btn_in_queue)
        isPaused -> context.getString(R.string.game_btn_paused)
        game.installStatus == InstallStatus.UPDATE_AVAILABLE -> context.getString(R.string.game_btn_update)
        game.installStatus == InstallStatus.INSTALLED -> context.getString(R.string.game_btn_installed)
        else -> context.getString(R.string.game_btn_install)
    }
    
    val isEnabled = (game.installStatus != InstallStatus.INSTALLED || canResume || isShelved || isQueued) && 
                    (!isProcessing || isShelved) && 
                    (game.queueStatus == null || canResume || isShelved || isQueued)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .scale(scale)
            .shadow(
                elevation = if (isHovered) 8.dp else 2.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) Color(0xFF1E1E1E) else Color(0xFF121212)
        ),
        border = if (isHovered) BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else if (game.isFavorite) BorderStroke(1.dp, Color(0xFFf1c40f).copy(alpha = 0.5f)) else null
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isGridItem) 56.dp else 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        .border(1.dp, if (game.isFavorite) Color(0xFFf1c40f).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(game.iconFile)
                            .crossfade(true)
                            .size(120, 120) 
                            .precision(Precision.EXACT)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Show "Permissions required" badge if permissions missing
                        if (permissionsMissing) {
                            Surface(
                                color = Color(0xFFe74c3c),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { onToggleFavorite(!game.isFavorite) },
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (game.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (game.isFavorite) Color(0xFFf1c40f) else Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v${game.version}", 
                            style = MaterialTheme.typography.bodySmall,
                            color = if (game.installStatus == InstallStatus.UPDATE_AVAILABLE) Color(0xFF2ecc71) else Color(0xFF3498db),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        
                        if (game.size != null) {
                            Text(
                                text = " • ${game.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp),
                                fontSize = 11.sp
                            )
                        }

                        if (game.isDownloaded) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloaded",
                                tint = Color(0xFF2ecc71),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        
                        if (isPaused) {
                            Text(
                                text = " • ${context.getString(R.string.game_btn_paused)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFf1c40f),
                                modifier = Modifier.padding(start = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                if (!isGridItem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (game.isDownloaded && game.installStatus == InstallStatus.NOT_INSTALLED) {
                             IconButton(
                                onClick = { onDeleteDownloadClick() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.game_btn_delete_download), tint = Color(0xFFCF6679).copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        } else if (game.installStatus == InstallStatus.INSTALLED || game.installStatus == InstallStatus.UPDATE_AVAILABLE) {
                            IconButton(
                                onClick = { onUninstallClick() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.game_btn_uninstall), tint = Color(0xFFCF6679), modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { onDownloadOnlyClick() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = context.getString(R.string.game_btn_download), tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = { if (canResume) onResumeClick() else onInstallClick() },
                            enabled = isEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                disabledContainerColor = if (game.installStatus == InstallStatus.INSTALLED) buttonColor.copy(alpha = 0.5f) else Color.DarkGray
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp).widthIn(min = 60.dp)
                        ) {
                            Text(buttonText, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                        .fillMaxWidth()
                ) {
                    Divider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Metadata Info
                    if (isGridItem) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoItem("Release Name", game.releaseName)
                            if (game.popularity > 0) {
                                InfoItem("Popularity", "⭐ ${game.popularity}")
                            }
                            InfoItem("Last Updated", formatDate(game.lastUpdated))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                InfoItem("Release Name", game.releaseName)
                                if (game.popularity > 0) {
                                    InfoItem("Popularity", "⭐ ${game.popularity}")
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                InfoItem("Last Updated", formatDate(game.lastUpdated))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (!game.screenshotUrls.isNullOrEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        ) {
                            items(game.screenshotUrls) { url ->
                                Card(shape = RoundedCornerShape(6.dp)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(url)
                                            .size(480, 270) 
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.aspectRatio(16/9f).fillMaxHeight(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    if (!game.description.isNullOrEmpty()) {
                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = game.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                lineHeight = 16.sp,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    
                    if (isGridItem) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (game.isDownloaded && game.installStatus == InstallStatus.NOT_INSTALLED) {
                                TextButton(onClick = onDeleteDownloadClick) {
                                    Text(context.getString(R.string.game_btn_delete_download), color = Color(0xFFCF6679).copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                            } else if (game.installStatus != InstallStatus.NOT_INSTALLED) {
                                TextButton(onClick = onUninstallClick) {
                                    Text(context.getString(R.string.game_btn_uninstall), color = Color(0xFFCF6679), fontSize = 12.sp)
                                }
                            } else {
                                TextButton(onClick = onDownloadOnlyClick) {
                                    Text(context.getString(R.string.game_btn_download), color = Color.Gray, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { if (canResume) onResumeClick() else onInstallClick() }, 
                                enabled = isEnabled, 
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 9.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
