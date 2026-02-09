package com.vrpirates.rookieonquest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.res.stringResource
import com.vrpirates.rookieonquest.R

/**
 * Permission Request Dialog
 *
 * Displays a dialog requesting a specific permission from the user.
 * Shows clear explanation of why the permission is needed and provides
 * options to grant or cancel the request.
 *
 * @param permission The permission being requested
 * @param onGrant Callback when user clicks "Grant Permission"
 * @param onCancel Callback when user clicks "Cancel"
 * @param onDismiss Callback when dialog is dismissed (e.g., back button)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestDialog(
    permission: RequiredPermission,
    onGrant: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Permission Icon - use specific icon for each permission type
                Icon(
                    imageVector = getPermissionIcon(permission),
                    contentDescription = getPermissionIconDescription(permission),
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = getPermissionTitle(permission),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = getPermissionDescription(permission),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Why needed
                Text(
                    text = getPermissionWhyNeeded(permission),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }

                    // Grant Permission Button
                    Button(
                        onClick = onGrant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_grant))
                    }
                }
            }
        }
    }
}

/**
 * Get the title for a permission request dialog.
 */
@Composable
private fun getPermissionTitle(permission: RequiredPermission): String {
    return when (permission) {
        RequiredPermission.INSTALL_UNKNOWN_APPS -> stringResource(R.string.perm_install_title)
        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> stringResource(R.string.perm_storage_title)
        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> stringResource(R.string.perm_battery_title)
    }
}

/**
 * Get the description for a permission request dialog.
 */
@Composable
private fun getPermissionDescription(permission: RequiredPermission): String {
    return when (permission) {
        RequiredPermission.INSTALL_UNKNOWN_APPS -> stringResource(R.string.perm_install_desc)
        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> stringResource(R.string.perm_storage_desc)
        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> stringResource(R.string.perm_battery_desc)
    }
}

/**
 * Get the "why needed" explanation for a permission.
 */
@Composable
private fun getPermissionWhyNeeded(permission: RequiredPermission): String {
    return when (permission) {
        RequiredPermission.INSTALL_UNKNOWN_APPS -> stringResource(R.string.perm_install_why)
        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> stringResource(R.string.perm_storage_why)
        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> stringResource(R.string.perm_battery_why)
    }
}

/**
 * Permission Revocation Dialog
 *
 * Shown when the app detects that a previously granted permission
 * has been revoked by the user in system settings.
 *
 * Updated to handle list of revoked permissions.
 * Shows appropriate message based on whether single or multiple permissions were revoked.
 *
 * @param permissions List of permissions that were revoked
 * @param onOpenSettings Callback to open system settings
 * @param onDismiss Callback when dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRevokedDialog(
    permissions: List<RequiredPermission>,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    val isMultiple = permissions.size > 1
    val primaryPermission = permissions.firstOrNull() ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isMultiple) Icons.Filled.Warning else getPermissionIcon(primaryPermission),
                contentDescription = stringResource(R.string.perm_revoked_title),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(stringResource(R.string.perm_revoked_title))
        },
        text = {
            if (isMultiple) {
                // Multiple permissions revoked - show list
                Column {
                    Text(stringResource(R.string.perm_revoked_multiple_msg, permissions.size))
                    Spacer(modifier = Modifier.height(12.dp))
                    permissions.forEach { permission ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = getPermissionIcon(permission),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                getPermissionName(permission),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                // Single permission revoked - use original format
                Text(
                    stringResource(R.string.perm_revoked_msg, getPermissionName(primaryPermission))
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.btn_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_later))
            }
        }
    )
}

/**
 * Get the display name for a permission.
 */
@Composable
private fun getPermissionName(permission: RequiredPermission): String {
    return when (permission) {
        RequiredPermission.INSTALL_UNKNOWN_APPS -> stringResource(R.string.perm_name_install)
        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> stringResource(R.string.perm_name_storage)
        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> stringResource(R.string.perm_name_battery)
    }
}

/**
 * Get the appropriate icon for a permission type.
 * Uses specific icons to visually distinguish each permission.
 */
private fun getPermissionIcon(permission: RequiredPermission): androidx.compose.ui.graphics.vector.ImageVector {
    return when (permission) {
        RequiredPermission.INSTALL_UNKNOWN_APPS -> Icons.Default.InstallMobile
        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> Icons.Default.Storage
        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> Icons.Default.BatteryChargingFull
    }
}

/**
 * Get the content description for the permission icon.
 */
@Composable
private fun getPermissionIconDescription(permission: RequiredPermission): String {
    return when (permission) {
        RequiredPermission.INSTALL_UNKNOWN_APPS -> stringResource(R.string.perm_install_title)
        RequiredPermission.MANAGE_EXTERNAL_STORAGE -> stringResource(R.string.perm_storage_title)
        RequiredPermission.IGNORE_BATTERY_OPTIMIZATIONS -> stringResource(R.string.perm_battery_title)
    }
}
