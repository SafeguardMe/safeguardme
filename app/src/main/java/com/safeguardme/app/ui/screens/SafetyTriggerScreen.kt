// ui/screens/SafetyTriggerScreen.kt - Enhanced with Permission Management
package com.safeguardme.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.safeguardme.app.data.models.RiskAssessment
import com.safeguardme.app.data.models.RiskLevel
import com.safeguardme.app.data.models.SafetyCoachPlan
import com.safeguardme.app.data.models.SafetyStatus
import com.safeguardme.app.data.repositories.VoiceDetectionSettings
import com.safeguardme.app.managers.AppPermission
import com.safeguardme.app.managers.VoiceDetectionStatus
import com.safeguardme.app.ui.components.VoiceKeywordSection
import com.safeguardme.app.ui.components.VoiceSensitivitySection
import com.safeguardme.app.ui.viewmodels.MonitoringStats
import com.safeguardme.app.ui.viewmodels.SafetyPermissionStatus
import com.safeguardme.app.ui.viewmodels.SafetyTriggerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyTriggerScreen(
    navController: NavController,
    viewModel: SafetyTriggerViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // State collection
    val user by viewModel.user.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showConfirmation by viewModel.showConfirmation.collectAsState()
    val confirmationTimeout by viewModel.confirmationTimeout.collectAsState()
    val safetyStatus by viewModel.safetyStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val buttonText by viewModel.buttonText.collectAsState()
    val micStatusText by viewModel.micStatusText.collectAsState()
    val volumeButtonTriggerEnabled by viewModel.volumeButtonTriggerEnabled.collectAsState()
    val shakeTriggerEnabled by viewModel.shakeTriggerEnabled.collectAsState()
    val powerButtonTriggerEnabled by viewModel.powerButtonTriggerEnabled.collectAsState()

    // ✅ NEW: Permission state collection
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val permissionWarnings by viewModel.permissionWarnings.collectAsState()
    val canActivateSafety by viewModel.canActivateSafety.collectAsState()
    val canCollectFullEvidence by viewModel.canCollectFullEvidence.collectAsState()
    val safetyCoachPlan by viewModel.safetyCoachPlan.collectAsState()
    val recoveryPrompts by viewModel.recoveryPrompts.collectAsState()
    val riskAssessments by viewModel.riskAssessments.collectAsState()

    val voiceDetectionEnabled by viewModel.voiceDetectionEnabled.collectAsState()
    val voiceDetectionStatus by viewModel.voiceDetectionStatus.collectAsState()
    val currentVoiceKeyword by viewModel.currentVoiceKeyword.collectAsState()

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    // Check permissions on screen entry
    LaunchedEffect(Unit) {
        viewModel.checkAllPermissions()
    }

    // Animation states
    val buttonScale by animateFloatAsState(
        targetValue = if (safetyStatus == SafetyStatus.ENABLED) 1.1f else 1f,
        animationSpec = tween(300), label = "button_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            !canActivateSafety && safetyStatus == SafetyStatus.DISABLED -> Color.Yellow
            safetyStatus == SafetyStatus.DISABLED -> MaterialTheme.colorScheme.primary
            safetyStatus == SafetyStatus.ENABLED -> Color(0xFFE53E3E)
            safetyStatus == SafetyStatus.EMERGENCY -> Color(0xFFD32F2F)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300), label = "button_color"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (safetyStatus) {
            SafetyStatus.DISABLED -> MaterialTheme.colorScheme.background
            SafetyStatus.ENABLED -> Color(0xFFFFF5F5)
            SafetyStatus.EMERGENCY -> Color(0xFFFFEBEE)
        },
        animationSpec = tween(500), label = "background_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            item {
                TopAppBar(
                    title = {
                        Text(
                            "Emergency Safety",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }

            // ✅ NEW: Permission Status Card
            item {
                SafetyPermissionStatusCard(
                    permissionStatus = permissionStatus,
                    permissionWarnings = permissionWarnings,
                    onRequestPermission = { viewModel.showPermissionDialog(it) }
                )
            }

            // ✅ NEW: Safety Capability Overview
            if (!canCollectFullEvidence) {
                item {
                    SafetyCapabilityCard(
                        permissionStatus = permissionStatus,
                        onRequestPermission = { viewModel.showPermissionDialog(it) }
                    )
                }
            }

            // Mic Status Pill
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = when {
                        !permissionStatus.canRecordAudio && safetyStatus == SafetyStatus.DISABLED ->
                            Color.Yellow.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                safetyStatus != SafetyStatus.DISABLED && permissionStatus.canRecordAudio -> Icons.Default.Mic
                                else -> Icons.Default.MicOff
                            },
                            contentDescription = null,
                            tint = when {
                                !permissionStatus.canRecordAudio && safetyStatus == SafetyStatus.DISABLED -> Color.Yellow
                                safetyStatus != SafetyStatus.DISABLED -> Color.Red
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = micStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                !permissionStatus.canRecordAudio && safetyStatus == SafetyStatus.DISABLED -> Color.Yellow
                                safetyStatus != SafetyStatus.DISABLED -> Color.Red
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Central Safety Button
            item {
                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Outer ring animation for emergency state
                    if (safetyStatus == SafetyStatus.ENABLED || safetyStatus == SafetyStatus.EMERGENCY) {
                        Surface(
                            modifier = Modifier.size(220.dp),
                            shape = CircleShape,
                            color = buttonColor.copy(alpha = 0.2f)
                        ) {}
                    }

                    // Main Safety Button
                    FilledIconButton(
                        onClick = {
                            performHapticFeedback(context)
                            viewModel.onSafetyButtonPressed()
                        },
                        modifier = Modifier
                            .size(180.dp)
                            .scale(buttonScale),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = buttonColor
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        !canActivateSafety && safetyStatus == SafetyStatus.DISABLED -> Icons.Default.Lock
                                        safetyStatus == SafetyStatus.DISABLED -> Icons.Default.Security
                                        safetyStatus == SafetyStatus.ENABLED -> Icons.Default.Warning
                                        safetyStatus == SafetyStatus.EMERGENCY -> Icons.Default.Emergency
                                        else -> Icons.Default.Security
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White
                                )
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Status Message
            item {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = when (safetyStatus) {
                        SafetyStatus.DISABLED -> {
                            if (canActivateSafety) MaterialTheme.colorScheme.onSurface
                            else Color.Yellow
                        }
                        SafetyStatus.ENABLED -> Color(0xFFD32F2F)
                        SafetyStatus.EMERGENCY -> Color(0xFFB71C1C)
                    },
                    fontWeight = if (safetyStatus != SafetyStatus.DISABLED) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Gesture Triggers (only when disabled)
            if (safetyStatus == SafetyStatus.DISABLED) {
                item {
                    EnhancedGestureTriggersCard(
                        volumeEnabled = volumeButtonTriggerEnabled,
                        shakeEnabled = shakeTriggerEnabled,
                        powerEnabled = powerButtonTriggerEnabled,
                        onVolumeToggle = viewModel::toggleVolumeButtonTrigger,
                        onShakeToggle = viewModel::toggleShakeTrigger,
                        onPowerToggle = viewModel::togglePowerButtonTrigger,
                        canActivateGestures = canActivateSafety,
                        onRequestPermissions = {
                            // ✅ ENHANCED: Request multiple essential permissions
                            viewModel.requestEssentialPermissions()
                        }
                    )
                }
            }

            if (safetyStatus == SafetyStatus.DISABLED) {
                item {
                    BackgroundVoiceDetectionCard(
                        voiceDetectionEnabled = voiceDetectionEnabled,
                        voiceDetectionStatus = voiceDetectionStatus,
                        currentKeyword = currentVoiceKeyword,
                        onToggleVoiceDetection = {
                            // ✅ ENHANCED: ViewModel should handle permission checking
                            viewModel.toggleVoiceDetection()
                        },
                        onConfigureKeyword = { viewModel.configureVoiceKeyword() },
                        canEnableVoiceDetection = permissionStatus.canRecordAudio,
                        isServiceRunning = isServiceRunning,
                        onRequestMicrophonePermission = {
                            // ✅ NEW: Specific microphone permission request
                            viewModel.showPermissionDialog(AppPermission.AUDIO_RECORDING)
                        }
                    )
                }
            }

            // Emergency Escalation Button (only when safety enabled)
            if (safetyStatus == SafetyStatus.ENABLED) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.escalateToEmergency() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Emergency,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escalate to Emergency Services")
                    }
                }
            }

            // Disable Button (only when safety enabled)
            if (safetyStatus == SafetyStatus.ENABLED || safetyStatus == SafetyStatus.EMERGENCY) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.onSafetyButtonPressed() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Safety Mode")
                    }
                }
            }

            // ✅ NEW: Monitoring Stats (when active)
            if (safetyStatus != SafetyStatus.DISABLED) {
                item {
                    MonitoringStatsCard(
                        monitoringStats = viewModel.monitoringStats.collectAsState().value,
                        permissionStatus = permissionStatus
                    )
                }
            }

            if (safetyCoachPlan != null || recoveryPrompts.isNotEmpty()) {
                item {
                    RecoveryModeCard(
                        plan = safetyCoachPlan,
                        recoveryPrompts = recoveryPrompts,
                        riskAssessments = riskAssessments,
                        onPromptComplete = viewModel::completeRecoveryPrompt
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Error Display
        error?.let { errorMessage ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage)
            }
        }

        // Confirmation Dialog
        if (showConfirmation) {
            ConfirmationDialog(
                title = when (safetyStatus) {
                    SafetyStatus.DISABLED -> "Enable Safety Mode?"
                    SafetyStatus.ENABLED -> "Disable Safety Mode?"
                    SafetyStatus.EMERGENCY -> "Override Emergency Mode?"
                },
                message = when (safetyStatus) {
                    SafetyStatus.DISABLED -> "This will enable emergency monitoring and notify your trusted contacts if needed."
                    SafetyStatus.ENABLED -> "This will disable emergency monitoring. Are you sure you're safe?"
                    SafetyStatus.EMERGENCY -> "This will disable emergency mode. Only do this if you're safe."
                },
                confirmText = when (safetyStatus) {
                    SafetyStatus.DISABLED -> "Enable"
                    SafetyStatus.ENABLED -> "Disable"
                    SafetyStatus.EMERGENCY -> "Override"
                },
                timeoutSeconds = confirmationTimeout,
                onConfirm = {
                    performHapticFeedback(context, strong = true)
                    viewModel.confirmSafetyAction()
                },
                onCancel = { viewModel.cancelConfirmation() }
            )
        }

        // ✅ NEW: Permission Request Dialog
        PermissionRequestDialog(viewModel = viewModel)
    }
}

/**
 * ✅ NEW: Safety-specific permission status card
 */
@Composable
private fun SafetyPermissionStatusCard(
    permissionStatus: SafetyPermissionStatus,
    permissionWarnings: List<String>,
    onRequestPermission: (AppPermission) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                permissionStatus.hasAllOptimalPermissions() -> Color.Green.copy(alpha = 0.1f)
                permissionStatus.hasCriticalPermissions() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> Color.Yellow.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        permissionStatus.hasAllOptimalPermissions() -> Icons.Default.CheckCircle
                        permissionStatus.hasCriticalPermissions() -> Icons.Default.Warning
                        else -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when {
                        permissionStatus.hasAllOptimalPermissions() -> Color.Green
                        permissionStatus.hasCriticalPermissions() -> Color.Yellow
                        else -> Color.Red
                    }
                )

                Text(
                    text = "Safety Capabilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Permission details
            SafetyPermissionRow(
                icon = Icons.Default.Mic,
                label = "Voice Evidence",
                isGranted = permissionStatus.canRecordAudio,
                isEssential = true,
                onRequest = { onRequestPermission(AppPermission.AUDIO_RECORDING) }
            )

            SafetyPermissionRow(
                icon = Icons.Default.LocationOn,
                label = "Location Tracking",
                isGranted = permissionStatus.canAccessLocation,
                isEssential = true,
                onRequest = { onRequestPermission(AppPermission.LOCATION) }
            )

            SafetyPermissionRow(
                icon = Icons.Default.Camera,
                label = "Photo Evidence",
                isGranted = permissionStatus.canTakePhotos,
                isEssential = false,
                onRequest = { onRequestPermission(AppPermission.CAMERA) }
            )

            SafetyPermissionRow(
                icon = Icons.Default.Save,
                label = "Evidence Storage",
                isGranted = permissionStatus.canSaveEvidence,
                isEssential = false,
                onRequest = { onRequestPermission(AppPermission.STORAGE) }
            )

            SafetyPermissionRow(
                icon = Icons.Default.Email,
                label = "SMS Messaging",
                isGranted = permissionStatus.canSendSMS,
                isEssential = false,
                onRequest = {
                    onRequestPermission(AppPermission.SMS_MESSAGING)
                }
            )

            // Warnings
            if (permissionWarnings.isNotEmpty()) {
                Divider()
                permissionWarnings.forEach { warning ->
                    Text(
                        text = "⚠️ $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyPermissionRow(
    icon: ImageVector,
    label: String,
    isGranted: Boolean,
    isEssential: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isGranted) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )

            if (isEssential) {
                Surface(
                    color = Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "ESSENTIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                modifier = Modifier.size(16.dp),
                tint = Color.Green
            )
        } else {
            OutlinedButton(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * ✅ NEW: Safety capability overview card
 */
@Composable
private fun SafetyCapabilityCard(
    permissionStatus: SafetyPermissionStatus,
    onRequestPermission: (AppPermission) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Safety Features Available",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            val activeCapabilities = permissionStatus.getActiveCapabilities()
            val missingFeatures = permissionStatus.getMissingFeatures()

            if (activeCapabilities.isNotEmpty()) {
                Text(
                    text = "✅ Available: ${activeCapabilities.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Green.copy(red = 0.2f)
                )
            }

            if (missingFeatures.isNotEmpty()) {
                Text(
                    text = "⚠️ Limited: ${missingFeatures.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Yellow
                )
            }
        }
    }
}

/**
 * ✅ ENHANCED: Gesture triggers card with permission awareness
 */
/**
 * ✅ UPDATED: Enhanced gesture triggers with better permission handling
 */
@Composable
private fun EnhancedGestureTriggersCard(
    volumeEnabled: Boolean,
    shakeEnabled: Boolean,
    powerEnabled: Boolean,
    onVolumeToggle: () -> Unit,
    onShakeToggle: () -> Unit,
    onPowerToggle: () -> Unit,
    canActivateGestures: Boolean,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canActivateGestures) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Yellow.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎭 Gesture Triggers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!canActivateGestures) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Yellow
                    )
                }
            }

            Text(
                text = if (canActivateGestures) {
                    "Trigger safety mode discreetly using phone gestures"
                } else {
                    "Grant essential permissions to enable gesture triggers"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (canActivateGestures) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Yellow
                }
            )

            if (!canActivateGestures) {
                Surface(
                    color = Color.Yellow.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ Gestures require basic safety permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "• Microphone for evidence recording\n• Location for emergency contacts\n• Camera for incident documentation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                ) {
                    Text("Grant Permissions to Enable Gestures", color = Color.Black)
                }

                Divider()
            }

            // ✅ ENHANCED: Gesture items with smart enable/disable
            SmartGestureTriggerItem(
                icon = "🔊",
                title = "Volume Buttons",
                description = "Press volume up/down 3 times rapidly",
                enabled = volumeEnabled,
                canEnable = canActivateGestures,
                onToggle = onVolumeToggle,
                onRequestPermissions = onRequestPermissions
            )

            SmartGestureTriggerItem(
                icon = "📳",
                title = "Phone Shake",
                description = "Shake phone vigorously for 2 seconds",
                enabled = shakeEnabled,
                canEnable = canActivateGestures,
                onToggle = onShakeToggle,
                onRequestPermissions = onRequestPermissions
            )

            SmartGestureTriggerItem(
                icon = "⚡",
                title = "Power Button",
                description = "Press power button 5 times quickly",
                enabled = powerEnabled,
                canEnable = canActivateGestures,
                onToggle = onPowerToggle,
                onRequestPermissions = onRequestPermissions
            )
        }
    }
}

/**
 * ✅ NEW: Smart gesture trigger item with permission handling
 */
@Composable
private fun SmartGestureTriggerItem(
    icon: String,
    title: String,
    description: String,
    enabled: Boolean,
    canEnable: Boolean,
    onToggle: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineSmall,
                color = if (canEnable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canEnable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (canEnable) 1f else 0.6f
                    )
                )
            }
        }

        // ✅ ENHANCED: Smart switch for gestures
        Switch(
            checked = enabled && canEnable,
            onCheckedChange = { isChecked ->
                if (isChecked && !canEnable) {
                    // Request permissions when trying to enable without them
                    Log.d("GestureTrigger", "⚠️ Permissions needed for $title")
                    onRequestPermissions()
                } else if (canEnable) {
                    // Normal toggle when permissions are available
                    onToggle()
                }
                // If unchecking, always allow (even without permissions)
                else if (!isChecked) {
                    onToggle()
                }
            },
            enabled = true // Always allow interaction, handle permissions in onClick
        )
    }
}


@Composable
private fun GestureTriggerItem(
    icon: String,
    title: String,
    description: String,
    enabled: Boolean,
    canEnable: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineSmall
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canEnable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (canEnable) 1f else 0.6f
                    )
                )
            }
        }

        Switch(
            checked = enabled,
            onCheckedChange = { if (canEnable) onToggle() },
            enabled = canEnable
        )
    }
}

/**
 * ✅ NEW: Monitoring statistics card
 */
@Composable
private fun MonitoringStatsCard(
    monitoringStats: MonitoringStats,
    permissionStatus: SafetyPermissionStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Blue.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🔄 Active Monitoring",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Duration: ${monitoringStats.getDurationMinutes()}m",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Evidence: ${monitoringStats.evidenceCount} items",
                style = MaterialTheme.typography.bodySmall
            )

            if (permissionStatus.getActiveCapabilities().isNotEmpty()) {
                Text(
                    text = "Active: ${permissionStatus.getActiveCapabilities().joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Green.copy(red = 0.2f)
                )
            }
        }
    }
}

/**
 * ✅ NEW: Permission request dialog for safety features
 */
@Composable
private fun PermissionRequestDialog(
    viewModel: SafetyTriggerViewModel
) {
    val showPermissionRequest by viewModel.showPermissionRequest.collectAsState()

    showPermissionRequest?.let { permission ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            icon = {
                Icon(
                    imageVector = when (permission) {
                        AppPermission.AUDIO_RECORDING -> Icons.Default.Mic
                        AppPermission.LOCATION -> Icons.Default.LocationOn
                        AppPermission.CAMERA -> Icons.Default.Camera
                        AppPermission.STORAGE -> Icons.Default.Save
                        else -> Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("${permission.title} for Safety")
            },
            text = {
                Column {
                    Text(permission.rationale)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Safety impact without this permission:",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )

                    when (permission) {
                        AppPermission.AUDIO_RECORDING -> {
                            Text("• No voice evidence collection", style = MaterialTheme.typography.bodySmall)
                            Text("• Limited emergency documentation", style = MaterialTheme.typography.bodySmall)
                            Text("• Reduced evidence quality", style = MaterialTheme.typography.bodySmall)
                        }
                        AppPermission.LOCATION -> {
                            Text("• No location tracking for emergencies", style = MaterialTheme.typography.bodySmall)
                            Text("• Emergency contacts won't know your location", style = MaterialTheme.typography.bodySmall)
                            Text("• Limited incident context", style = MaterialTheme.typography.bodySmall)
                        }
                        AppPermission.CAMERA -> {
                            Text("• No photo evidence collection", style = MaterialTheme.typography.bodySmall)
                            Text("• Visual documentation unavailable", style = MaterialTheme.typography.bodySmall)
                        }
                        AppPermission.STORAGE -> {
                            Text("• Evidence may not be saved permanently", style = MaterialTheme.typography.bodySmall)
                            Text("• Limited data backup capabilities", style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.requestPermission(permission) }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissPermissionDialog() }
                ) {
                    Text(
                        if (permission in listOf(AppPermission.AUDIO_RECORDING, AppPermission.LOCATION)) {
                            "Continue with Limited Safety"
                        } else {
                            "Continue Without"
                        }
                    )
                }
            }
        )
    }
}

// ✅ ENHANCED: Confirmation dialog (unchanged)
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    timeoutSeconds: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(message)
                if (timeoutSeconds > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Auto-cancel in ${timeoutSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmText == "Enable")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

/**
 * ✅ NEW: Voice detection toggle card
 */
@Composable
private fun VoiceDetectionToggleCard(
    voiceDetectionEnabled: Boolean,
    onToggleVoiceDetection: () -> Unit,
    canActivateVoiceDetection: Boolean,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canActivateVoiceDetection) {
                if (voiceDetectionEnabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Yellow.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (voiceDetectionEnabled && canActivateVoiceDetection)
                            Icons.Default.RecordVoiceOver else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (voiceDetectionEnabled && canActivateVoiceDetection)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )

                    Column {
                        Text(
                            text = "🎙️ Always-On Voice Detection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (canActivateVoiceDetection) {
                                if (voiceDetectionEnabled) "Listening for your trigger word"
                                else "Activate to enable keyword detection"
                            } else {
                                "Grant microphone permission to enable"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (canActivateVoiceDetection && voiceDetectionEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Switch(
                    checked = voiceDetectionEnabled && canActivateVoiceDetection,
                    onCheckedChange = {
                        if (canActivateVoiceDetection) {
                            onToggleVoiceDetection()
                        } else {
                            onRequestPermissions()
                        }
                    },
                    enabled = canActivateVoiceDetection
                )
            }

            if (voiceDetectionEnabled && canActivateVoiceDetection) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "🔒 Privacy: 100% on-device processing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "🔋 Battery impact: <1% per day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "📡 No data sent until emergency triggered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!canActivateVoiceDetection) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                ) {
                    Text("Grant Microphone Permission")
                }
            }
        }
    }
}

/**
 * ✅ UPDATED: Voice Detection Card for SafetyTriggerScreen.kt
 *
 * Key Changes:
 * - "Always-On" → "Background Voice Detection"
 * - Shows foreground service status
 * - Updated battery impact estimates (2-5% vs <1%)
 * - Service-based status indicators
 * - Clear user understanding of foreground service
 */
@Composable
private fun BackgroundVoiceDetectionCard(
    voiceDetectionEnabled: Boolean,
    voiceDetectionStatus: VoiceDetectionStatus,
    isServiceRunning: Boolean,
    currentKeyword: String?,
    onToggleVoiceDetection: () -> Unit,
    onConfigureKeyword: () -> Unit,
    canEnableVoiceDetection: Boolean,
    onRequestMicrophonePermission: () -> Unit // ✅ NEW: Specific callback for microphone permission
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                voiceDetectionStatus == VoiceDetectionStatus.ACTIVE && isServiceRunning ->
                    Color.Green.copy(alpha = 0.1f)
                voiceDetectionStatus == VoiceDetectionStatus.NO_PERMISSIONS ->
                    Color.Yellow.copy(alpha = 0.1f)
                voiceDetectionStatus == VoiceDetectionStatus.ERROR ->
                    Color.Red.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with updated title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🗣️ Background Voice Detection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Service status indicator
                if (voiceDetectionStatus == VoiceDetectionStatus.ACTIVE && isServiceRunning) {
                    Surface(
                        color = Color.Green,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }

            // Updated description
            Text(
                text = getBackgroundVoiceDetectionDescription(voiceDetectionStatus, isServiceRunning, currentKeyword),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Service status row
            BackgroundVoiceDetectionStatusRow(voiceDetectionStatus, isServiceRunning, currentKeyword)

            // ✅ ENHANCED: Permission-aware toggle section
            VoiceDetectionToggleSection(
                voiceDetectionEnabled = voiceDetectionEnabled,
                canEnableVoiceDetection = canEnableVoiceDetection,
                onToggleVoiceDetection = onToggleVoiceDetection,
                onRequestMicrophonePermission = onRequestMicrophonePermission
            )

            // Keyword configuration
            if (voiceDetectionEnabled || currentKeyword != null) {
                Divider()

                KeywordConfigurationRow(
                    currentKeyword = currentKeyword,
                    onConfigureKeyword = onConfigureKeyword,
                    isActive = voiceDetectionStatus == VoiceDetectionStatus.ACTIVE && isServiceRunning
                )
            }

            // Service information (when active)
            if (voiceDetectionEnabled) {
                BackgroundVoiceDetectionServiceInfo(isServiceRunning)
            }

            // ✅ NEW: Permission warning when needed
            if (!canEnableVoiceDetection) {
                VoiceDetectionPermissionWarning(onRequestMicrophonePermission)
            }
        }
    }
}

/**
 * ✅ NEW: Dedicated toggle section with permission handling
 */
@Composable
private fun VoiceDetectionToggleSection(
    voiceDetectionEnabled: Boolean,
    canEnableVoiceDetection: Boolean,
    onToggleVoiceDetection: () -> Unit,
    onRequestMicrophonePermission: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Enable Background Voice Detection",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (canEnableVoiceDetection) {
                    if (voiceDetectionEnabled) "Service will run with persistent notification"
                    else "Tap to start background listening service"
                } else {
                    "Microphone permission required"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (canEnableVoiceDetection) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Yellow
                }
            )
        }

        // ✅ ENHANCED: Smart toggle that handles permissions
        VoiceDetectionSmartSwitch(
            voiceDetectionEnabled = voiceDetectionEnabled,
            canEnableVoiceDetection = canEnableVoiceDetection,
            onToggleVoiceDetection = onToggleVoiceDetection,
            onRequestMicrophonePermission = onRequestMicrophonePermission
        )
    }
}

/**
 * ✅ NEW: Smart switch that handles permission checking
 */
@Composable
private fun VoiceDetectionSmartSwitch(
    voiceDetectionEnabled: Boolean,
    canEnableVoiceDetection: Boolean,
    onToggleVoiceDetection: () -> Unit,
    onRequestMicrophonePermission: () -> Unit
) {
    Switch(
        checked = voiceDetectionEnabled && canEnableVoiceDetection,
        onCheckedChange = { isChecked ->
            if (isChecked) {
                // ✅ ENHANCED: Check permissions before enabling
                if (canEnableVoiceDetection) {
                    Log.d("VoiceDetection", "✅ Permissions granted, enabling voice detection")
                    onToggleVoiceDetection()
                } else {
                    Log.d("VoiceDetection", "⚠️ Microphone permission needed, requesting...")
                    onRequestMicrophonePermission()
                }
            } else {
                // ✅ UNCHANGED: Always allow disabling
                Log.d("VoiceDetection", "🔇 Disabling voice detection")
                onToggleVoiceDetection()
            }
        },
        // ✅ ENHANCED: Always enable the switch (let onClick handle permissions)
        enabled = true
    )
}

/**
 * ✅ NEW: Permission warning section
 */
@Composable
private fun VoiceDetectionPermissionWarning(
    onRequestMicrophonePermission: () -> Unit
) {
    Surface(
        color = Color.Yellow.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.Yellow,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Microphone Permission Required",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Yellow
                )
            }

            Text(
                text = "Background voice detection needs microphone access to listen for your emergency trigger words.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onRequestMicrophonePermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Yellow,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Microphone Permission")
            }
        }
    }
}

/**
 * ✅ UPDATED: Status row with service information
 */
@Composable
private fun BackgroundVoiceDetectionStatusRow(
    status: VoiceDetectionStatus,
    isServiceRunning: Boolean,
    currentKeyword: String?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when {
                status == VoiceDetectionStatus.ACTIVE && isServiceRunning -> Icons.Default.Mic
                status == VoiceDetectionStatus.NO_PERMISSIONS -> Icons.Default.MicOff
                status == VoiceDetectionStatus.ERROR -> Icons.Default.Error
                else -> Icons.Default.MicOff
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = when {
                status == VoiceDetectionStatus.ACTIVE && isServiceRunning -> Color.Green
                status == VoiceDetectionStatus.NO_PERMISSIONS -> Color.Yellow
                status == VoiceDetectionStatus.ERROR -> Color.Red
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Text(
            text = getServiceStatusText(status, isServiceRunning),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                status == VoiceDetectionStatus.ACTIVE && isServiceRunning -> Color.Green
                status == VoiceDetectionStatus.NO_PERMISSIONS -> Color.Yellow
                status == VoiceDetectionStatus.ERROR -> Color.Red
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium
        )
    }
}


/**
 * ✅ UPDATED: Profile screen voice detection settings
 *
 * Key Changes:
 * - Updated terminology throughout
 * - Battery impact information revised
 * - Service management options
 * - Clear foreground service explanation
 */
@Composable
private fun BackgroundVoiceDetectionSettingsCard(
    voiceDetectionSettings: VoiceDetectionSettings,
    voiceDetectionStatus: VoiceDetectionStatus,
    isServiceRunning: Boolean,
    currentKeyword: String?,
    onToggleVoiceDetection: () -> Unit,
    onUpdateSensitivity: (Float) -> Unit,
    onToggleBatteryOptimization: () -> Unit,
    onConfigureKeyword: () -> Unit,
    onTestVoiceDetection: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Background Voice Detection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Service status indicator
                if (voiceDetectionStatus == VoiceDetectionStatus.ACTIVE && isServiceRunning) {
                    Surface(
                        color = Color.Green,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }

            // Service status overview
            BackgroundServiceStatusOverview(
                status = voiceDetectionStatus,
                isServiceRunning = isServiceRunning,
                currentKeyword = currentKeyword,
                settings = voiceDetectionSettings
            )

            // Main toggle
            SettingItem(
                icon = Icons.Default.Mic,
                title = "Background Voice Detection",
                description = "Run foreground service to detect emergency triggers",
                isChecked = voiceDetectionSettings.enabled,
                onCheckedChange = { onToggleVoiceDetection() }
            )

            if (voiceDetectionSettings.enabled) {
                Divider()

                // Service information
                ForegroundServiceExplanation()

                Divider()

                // Keyword management
                VoiceKeywordSection(
                    currentKeyword = currentKeyword,
                    onConfigureKeyword = onConfigureKeyword,
                    onTestVoiceDetection = onTestVoiceDetection
                )

                Divider()

                // Sensitivity slider
                VoiceSensitivitySection(
                    sensitivity = voiceDetectionSettings.sensitivity,
                    onUpdateSensitivity = onUpdateSensitivity
                )

                Divider()

                // Battery optimization
                SettingItem(
                    icon = Icons.Default.Battery6Bar,
                    title = "Battery Optimization",
                    description = "Optimize service for battery life",
                    isChecked = voiceDetectionSettings.batteryOptimized,
                    onCheckedChange = { onToggleBatteryOptimization() }
                )
            }
        }
    }
}

/**
 * ✅ NEW: Service status overview for profile screen
 */
@Composable
private fun BackgroundServiceStatusOverview(
    status: VoiceDetectionStatus,
    isServiceRunning: Boolean,
    currentKeyword: String?,
    settings: VoiceDetectionSettings
) {
    Surface(
        color = when {
            status == VoiceDetectionStatus.ACTIVE && isServiceRunning -> Color.Green.copy(alpha = 0.1f)
            status == VoiceDetectionStatus.NO_PERMISSIONS -> Color.Yellow.copy(alpha = 0.1f)
            status == VoiceDetectionStatus.ERROR -> Color.Red.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        status == VoiceDetectionStatus.ACTIVE && isServiceRunning -> "🟢 Service Active"
                        status == VoiceDetectionStatus.DISABLED -> "⚪ Service Disabled"
                        status == VoiceDetectionStatus.NO_PERMISSIONS -> "🟡 No Permissions"
                        status == VoiceDetectionStatus.ERROR -> "🔴 Service Error"
                        else -> "⚪ Service Inactive"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = when {
                    isServiceRunning -> "Foreground service running with persistent notification"
                    settings.enabled -> "Service will start when permissions are granted"
                    else -> "Background voice detection is disabled"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * ✅ NEW: Foreground service explanation
 */
@Composable
private fun ForegroundServiceExplanation() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "How Background Detection Works",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            ServiceInfoRow(
                icon = "🔔",
                title = "Persistent Notification",
                description = "Service runs with visible notification for transparency"
            )

            ServiceInfoRow(
                icon = "🔒",
                title = "On-Device Processing",
                description = "All voice processing happens locally on your device"
            )

            ServiceInfoRow(
                icon = "🔋",
                title = "Battery Usage",
                description = "Estimated 2-5% per day with optimization enabled"
            )

            ServiceInfoRow(
                icon = "📱",
                title = "Background Operation",
                description = "Works when app is backgrounded, stops if force-closed"
            )
        }
    }
}

@Composable
private fun ServiceInfoRow(
    icon: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyMedium
        )

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * ✅ UPDATED: Service status text
 */
private fun getServiceStatusText(status: VoiceDetectionStatus, isServiceRunning: Boolean): String {
    return when {
        status == VoiceDetectionStatus.ACTIVE && isServiceRunning -> "SERVICE RUNNING - Background listening"
        status == VoiceDetectionStatus.ACTIVE && !isServiceRunning -> "Service starting..."
        status == VoiceDetectionStatus.DISABLED -> "Service disabled"
        status == VoiceDetectionStatus.NO_PERMISSIONS -> "No microphone access"
        status == VoiceDetectionStatus.SERVICE_NOT_SET -> "Service not running"
        status == VoiceDetectionStatus.UNAVAILABLE -> "Not available"
        status == VoiceDetectionStatus.ERROR -> "Service error"
        else -> "Checking service..."
    }
}

@Composable
private fun ServiceDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * ✅ NEW: Service information section
 */
@Composable
private fun BackgroundVoiceDetectionServiceInfo(isServiceRunning: Boolean) {
    Surface(
        color = if (isServiceRunning) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isServiceRunning) "Service Active" else "Service Information",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (isServiceRunning) {
                ServiceDetailRow("🔔 Notification", "Persistent service notification active")
                ServiceDetailRow("🔒 Privacy", "All processing on your device")
                ServiceDetailRow("🔋 Battery", "Estimated 2-5% per day")
                ServiceDetailRow("📱 Detection", "Works when app is backgrounded")
            } else {
                Text(
                    text = "Background service will run with a persistent notification for transparency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * ✅ UPDATED: Description text for background voice detection
 */
private fun getBackgroundVoiceDetectionDescription(
    status: VoiceDetectionStatus,
    isServiceRunning: Boolean,
    currentKeyword: String?
): String {
    return when {
        status == VoiceDetectionStatus.ACTIVE && isServiceRunning ->
            "Background service listening for \"$currentKeyword\" to instantly trigger emergency mode. Runs with a persistent notification for transparency."

        status == VoiceDetectionStatus.DISABLED ->
            "Background voice detection is disabled. When enabled, SafeguardMe will run a foreground service to listen for your trigger word."

        status == VoiceDetectionStatus.NO_PERMISSIONS ->
            "Microphone access required for voice detection. Grant permission to enable background emergency triggers."

        status == VoiceDetectionStatus.SERVICE_NOT_SET ->
            "Voice service not running. Tap enable to start background voice detection service."

        status == VoiceDetectionStatus.UNAVAILABLE ->
            "Voice detection not supported on this device. Use manual or gesture triggers instead."

        status == VoiceDetectionStatus.ERROR ->
            "Voice detection error. Check permissions and try again."

        else ->
            "Checking voice detection capabilities..."
    }
}


/**
 * ✅ HELPER: Keyword configuration row
 */
@Composable
private fun KeywordConfigurationRow(
    currentKeyword: String?,
    onConfigureKeyword: () -> Unit,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Trigger Keyword",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (currentKeyword != null) {
                    "\"$currentKeyword\"${if (isActive) " (Active)" else " (Ready)"}"
                } else {
                    "No keyword set"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (currentKeyword != null) {
                    if (isActive) Color.Green else MaterialTheme.colorScheme.primary
                } else {
                    Color.Red
                }
            )
        }

        OutlinedButton(
            onClick = onConfigureKeyword,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (currentKeyword != null) "Change" else "Set Keyword",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * ✅ HELPER: Technical details section
 */
@Composable
private fun VoiceDetectionTechnicalDetails() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Technical Details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            TechnicalDetailRow("🔒 Processing", "100% on-device")
            TechnicalDetailRow("🔋 Battery impact", "<1% per day")
            TechnicalDetailRow("📡 Data transmission", "None until triggered")
            TechnicalDetailRow("🎚️ Detection method", "Hardware DSP + Android system")
        }
    }
}

@Composable
private fun TechnicalDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * ✅ HELPER: Get voice detection description text
 */
private fun getVoiceDetectionDescription(
    status: VoiceDetectionStatus,
    currentKeyword: String?
): String {
    return when (status) {
        VoiceDetectionStatus.ACTIVE ->
            "Always listening for \"$currentKeyword\" to instantly trigger emergency mode. Uses hardware DSP for maximum battery efficiency."

        VoiceDetectionStatus.DISABLED ->
            "Voice detection is disabled. Enable to activate emergency mode by speaking your trigger word."

        VoiceDetectionStatus.NO_PERMISSIONS ->
            "Microphone access required for voice detection. Grant permission to enable always-on emergency triggers."

        VoiceDetectionStatus.SERVICE_NOT_SET ->
            "Voice service configuration required. Tap to set up always-on detection."

        VoiceDetectionStatus.UNAVAILABLE ->
            "Voice detection not supported on this device. Use manual or gesture triggers instead."

        VoiceDetectionStatus.ERROR ->
            "Voice detection error. Check permissions and try again."

        VoiceDetectionStatus.UNKNOWN ->
            "Checking voice detection capabilities..."
    }
}

/**
 * ✅ HELPER: Get status text
 */
private fun getStatusText(status: VoiceDetectionStatus): String {
    return when (status) {
        VoiceDetectionStatus.ACTIVE -> "ACTIVE - Always listening"
        VoiceDetectionStatus.DISABLED -> "Disabled"
        VoiceDetectionStatus.NO_PERMISSIONS -> "No microphone access"
        VoiceDetectionStatus.SERVICE_NOT_SET -> "Setup required"
        VoiceDetectionStatus.UNAVAILABLE -> "Not available"
        VoiceDetectionStatus.ERROR -> "Error"
        VoiceDetectionStatus.UNKNOWN -> "Checking..."
    }
}




// ✅ UNCHANGED: Haptic feedback function
private fun performHapticFeedback(context: Context, strong: Boolean = false) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (strong) {
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (strong) 200 else 50)
        }
    } catch (e: Exception) {
        // Haptic feedback not available, continue silently
    }
}

@Composable
private fun RecoveryModeCard(
    plan: SafetyCoachPlan?,
    recoveryPrompts: List<String>,
    riskAssessments: List<RiskAssessment>,
    onPromptComplete: (String) -> Unit
) {
    val latestRisk = riskAssessments.lastOrNull()
    val riskLevel = plan?.riskLevel ?: latestRisk?.level ?: RiskLevel.UNKNOWN
    val riskScore = plan?.riskScore ?: latestRisk?.score ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recovery Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            plan?.let {
                Text(
                    text = it.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (it.immediateActions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Immediate next steps",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        it.immediateActions.forEach { action ->
                            Text(
                                text = "• $action",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = when (riskLevel) {
                        RiskLevel.CRITICAL -> Color(0xFFD32F2F)
                        RiskLevel.HIGH -> Color(0xFFF57C00)
                        RiskLevel.MODERATE -> Color(0xFFFFC107)
                        RiskLevel.LOW -> Color(0xFF43A047)
                        RiskLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "Risk level: ${riskLevel.name} (score $riskScore)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recoveryPrompts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Grounding prompts",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )

                    recoveryPrompts.forEach { prompt ->
                        RecoveryPromptRow(
                            prompt = prompt,
                            onComplete = { onPromptComplete(prompt) }
                        )
                    }
                }
            } else {
                Text(
                    text = "All recovery prompts completed. Take your time to rest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecoveryPromptRow(
    prompt: String,
    onComplete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onComplete) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Done")
        }
    }
}
