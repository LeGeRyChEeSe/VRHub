package com.vrhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-launch consent dialog for anonymous statistics collection.
 * Shown once when the user first opens the app.
 * Cannot be dismissed by tapping outside - user must choose Accept or Decline.
 */
@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Must choose - non-dismissible */ },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Help Improve VRHub",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Share anonymous usage statistics to help us understand VR game trends and improve your experience.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "We collect:",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                ConsentDialogBulletPoint("Installed games on your device")
                ConsentDialogBulletPoint("Your favorite games (marked with ⭐)")
                ConsentDialogBulletPoint("Your tier (standard, supporter, or lucky)")

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "We do NOT collect any personal information, device identifiers, or location data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Accept")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
private fun ConsentDialogBulletPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}