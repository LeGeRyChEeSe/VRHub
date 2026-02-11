package com.vrpirates.rookieonquest.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Banner displayed when a new catalog is available.
 * Implements AC 2: Update Notification UI.
 */
@Composable
fun CatalogUpdateBanner(
    isVisible: Boolean,
    updateCount: Int,
    onSyncClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            color = Color(0xFF2C3E50),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF3498DB).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        tint = Color(0xFF3498DB)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New catalog available",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (updateCount > 0) "$updateCount games added/updated" else "Update now to see the latest VR games",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onSyncClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3498DB),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SYNC NOW", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
