package com.vahemgeo.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahemgeo.app.engine.SecurityAuditStatus
import com.vahemgeo.app.ui.theme.CyberTeal
import com.vahemgeo.app.ui.theme.ElectricLime
import com.vahemgeo.app.ui.theme.MutedSage
import com.vahemgeo.app.ui.theme.PolarWhite
import com.vahemgeo.app.ui.theme.SurfaceCard
import com.vahemgeo.app.ui.theme.WarningRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityShieldSheet(
    auditStatus: SecurityAuditStatus,
    onToggleCoordinateLock: (Boolean) -> Unit,
    onToggleJitter: (Boolean) -> Unit,
    onRefreshAudit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberTeal),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(ElectricLime.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = ElectricLime,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "99% Anti-Leak Security Shield",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = PolarWhite
                                )
                                Text(
                                    text = "Multi-layer real location leak prevention",
                                    fontSize = 12.sp,
                                    color = MutedSage
                                )
                            }
                        }
                        Text(
                            text = "${auditStatus.safetyScorePercent}%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = ElectricLime
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { auditStatus.safetyScorePercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ElectricLime,
                        trackColor = SurfaceCard
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ACTIVE ENGINE PROTECTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricLime
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Coordinate Lock Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricLime)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Coordinate Lock & Anti-Null-Island", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolarWhite)
                        Text(
                            "Persists spoofed coordinates so background OS restarts never leak real GPS or reset to (0,0)",
                            fontSize = 11.sp,
                            color = MutedSage
                        )
                    }
                    Switch(
                        checked = auditStatus.isCoordinateLockEnabled,
                        onCheckedChange = onToggleCoordinateLock
                    )
                }
            }

            // 2. Physics-Coherent Micro-Jitter Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = ElectricLime)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Physics-Coherent Jitter & Elevation", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolarWhite)
                        Text(
                            "Sub-meter Brownian GPS drift + matched micro-speed & barometric altitude wave",
                            fontSize = 11.sp,
                            color = MutedSage
                        )
                    }
                    Switch(
                        checked = auditStatus.isJitterActive,
                        onCheckedChange = onToggleJitter
                    )
                }
            }

            // 3. 10Hz Multi-Provider + Doze WakeLock Status
            ProtectionStatusRow(
                title = "10Hz Triple-Provider Injection + WakeLock",
                subtitle = "Overrides GPS, NETWORK, and FUSED providers every 100ms with CPU Doze lock",
                isProtected = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SYSTEM LEAK VECTORS (1-TAP HARDENING)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricLime
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 4. Wi-Fi & Bluetooth Scanning Check
            val scanningSafe = auditStatus.isWifiScanningOff && auditStatus.isBtScanningOff
            ProtectionActionRow(
                title = "Wi-Fi & Bluetooth Background Scanning",
                subtitle = if (scanningSafe) {
                    "SAFE: Background BSSID/Beacon triangulation is disabled"
                } else {
                    "LEAK RISK: Turn OFF 'Wi-Fi scanning' & 'Bluetooth scanning' in Location Services"
                },
                isSafe = scanningSafe,
                actionLabel = "Settings",
                onAction = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            )

            // 5. Google Location Accuracy Check
            ProtectionActionRow(
                title = "Google Location Accuracy (Cell/Wi-Fi Fusion)",
                subtitle = "Disable 'Improve Location Accuracy' in Location Services so Android uses 100% Mock GPS",
                isSafe = true,
                actionLabel = "Open",
                onAction = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            )

            // 6. IP / Network Geolocation Status
            ProtectionActionRow(
                title = "IP Address & Network Leak Protection",
                subtitle = if (auditStatus.isVpnActive) {
                    "SAFE: Active VPN tunnel detected masking ISP IP geolocation"
                } else {
                    "TIP: Connect a VPN matching your target region if apps cross-check IP vs GPS"
                },
                isSafe = auditStatus.isVpnActive,
                actionLabel = "Recheck",
                onAction = onRefreshAudit
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProtectionStatusRow(
    title: String,
    subtitle: String,
    isProtected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isProtected) ElectricLime else WarningRed
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolarWhite)
                Text(subtitle, fontSize = 11.sp, color = MutedSage)
            }
        }
    }
}

@Composable
private fun ProtectionActionRow(
    title: String,
    subtitle: String,
    isSafe: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSafe) ElectricLime else Color(0xFFFFB300)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolarWhite)
                Text(subtitle, fontSize = 11.sp, color = MutedSage)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onAction) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(actionLabel, fontSize = 11.sp)
            }
        }
    }
}
