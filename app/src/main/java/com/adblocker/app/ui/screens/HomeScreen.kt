package com.adblocker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adblocker.app.blocklist.BlocklistStore
import com.adblocker.app.service.VpnStateManager

@Composable
fun HomeScreen(
    vpnState: VpnStateManager.VpnState,
    totalBlocked: Long,
    totalQueries: Long,
    errorMessage: String?,
    onToggleVpn: () -> Unit
) {
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) showError = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Header
        Text(
            text = "AdBlocker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "DNS-based ad blocking",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Main toggle card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (vpnState == VpnStateManager.VpnState.RUNNING)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (vpnState) {
                        VpnStateManager.VpnState.IDLE -> "VPN is OFF"
                        VpnStateManager.VpnState.STARTING -> "Starting..."
                        VpnStateManager.VpnState.RUNNING -> "VPN is ON"
                        VpnStateManager.VpnState.STOPPING -> "Stopping..."
                        VpnStateManager.VpnState.ERROR -> "Error"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Switch(
                    checked = vpnState == VpnStateManager.VpnState.RUNNING ||
                            vpnState == VpnStateManager.VpnState.STARTING,
                    onCheckedChange = { onToggleVpn() },
                    modifier = Modifier.height(40.dp),
                    enabled = vpnState != VpnStateManager.VpnState.STARTING &&
                            vpnState != VpnStateManager.VpnState.STOPPING
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Ads Blocked",
                value = totalBlocked.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "DNS Queries",
                value = totalQueries.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Blocklist info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Blocklist",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${BlocklistStore.count} domains loaded",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = when {
                vpnState == VpnStateManager.VpnState.RUNNING -> "AdBlocker is protecting your device"
                vpnState == VpnStateManager.VpnState.ERROR -> "Tap to retry"
                else -> "Only one VPN can be active at a time"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Error dialog
    if (showError && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
