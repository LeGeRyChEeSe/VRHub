package com.vrhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrhub.BuildConfig
import com.vrhub.ui.DebugMonetizationViewModel

/**
 * Floating debug panel for testing monetization endpoints.
 * Only visible in dev flavor AND debug build type (not devRelease).
 */
@Composable
fun DebugMonetizationPanel() {
    if (!BuildConfig.DEBUG || !BuildConfig.APPLICATION_ID.endsWith(".debug")) {
        return
    }

    val viewModel: DebugMonetizationViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    // Panel is hidden when isVisible is false
    if (!state.isVisible) {
        // Show a small floating indicator that panel can be reopened
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                onClick = { viewModel.toggleVisibility() },
                shape = CircleShape,
                color = Color(0xFF1E1E1E),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "🧪",
                    color = Color.Red,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧪 DEBUG",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        TextButton(
                            onClick = { viewModel.toggleVisibility() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "[×]",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = { viewModel.toggleMinimized() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (state.isMinimized) "[+]" else "[-]",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Content
                AnimatedVisibility(
                    visible = !state.isMinimized,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Email field
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = { viewModel.updateEmail(it) },
                            label = { Text("Email", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )

                        // Token field
                        OutlinedTextField(
                            value = state.token,
                            onValueChange = { viewModel.updateToken(it) },
                            label = { Text("Token (for verify)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // Verification token field
                        OutlinedTextField(
                            value = state.verificationToken,
                            onValueChange = { viewModel.updateVerificationToken(it) },
                            label = { Text("Ko-fi Verification Token", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )

                        // Error message
                        state.error?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }

                        // Button row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { viewModel.testInit() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Init", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.testVerify() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Verify", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.testHealth() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Health", fontSize = 11.sp)
                            }
                        }

                        // Button row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { viewModel.testWebhookSupporter() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Webhook S", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.testWebhookLucky() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Webhook L", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.testValidate() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isLoading,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Validate", fontSize = 11.sp)
                            }
                        }

                        // Loading
                        if (state.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                            )
                        }

                        // Response
                        if (state.response.isNotBlank()) {
                            Text(
                                text = "Response:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp, max = 150.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF0D0D0D),
                                border = BorderStroke(1.dp, Color(0xFF333333))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = state.response,
                                        color = Color(0xFF00FF00),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}