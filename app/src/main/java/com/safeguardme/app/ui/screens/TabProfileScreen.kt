// ui/screens/TabProfileScreen.kt
package com.safeguardme.app.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.safeguardme.app.data.repositories.SettingsRepository
import com.safeguardme.app.navigation.Screen
import com.safeguardme.app.ui.viewmodels.ProfileViewModel
import com.safeguardme.app.utils.SecurityUtils
import kotlinx.coroutines.launch

@Composable
fun TabProfileScreen(
    navController: NavController,
    paddingValues: PaddingValues,
    settingsRepository: SettingsRepository,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val fullName by viewModel.fullName.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val nameError by viewModel.nameError.collectAsState()
    val phoneError by viewModel.phoneError.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val canSave by viewModel.canSave.collectAsState()

    // Settings state managed directly from SettingsRepository
    val appSettings by settingsRepository.appSettings.collectAsState(
        initial = SettingsRepository.AppSettings()
    )

    var showSignOutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Settings toggle functions
    val onToggleDarkMode: () -> Unit = {
        scope.launch {
            settingsRepository.setDarkMode(!appSettings.darkMode)
        }
    }

    val onToggleSounds: () -> Unit = {
        scope.launch {
            settingsRepository.setSoundsEnabled(!appSettings.soundsEnabled)
        }
    }

    val onToggleOfflineMode: () -> Unit = {
        scope.launch {
            settingsRepository.setOfflineModeAllowed(!appSettings.offlineModeAllowed)
        }
    }

    val onToggleBiometric: () -> Unit = {
        scope.launch {
            settingsRepository.setBiometricEnabled(!appSettings.biometricEnabled)
        }
    }

    val onToggleEmergencyContactsOnly: () -> Unit = {
        scope.launch {
            settingsRepository.setEmergencyContactsOnly(!appSettings.emergencyContactsOnly)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Header
        item {
            Text(
                text = "Profile & Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Profile Header
            item {
                ProfileHeaderCard(
                    user = user,
                    hasUnsavedChanges = hasUnsavedChanges
                )
            }

            // Editable Profile Fields
            item {
                ProfileEditCard(
                    fullName = fullName,
                    phoneNumber = phoneNumber,
                    nameError = nameError,
                    phoneError = phoneError,
                    hasUnsavedChanges = hasUnsavedChanges,
                    canSave = canSave,
                    isSaving = isSaving,
                    onNameChange = viewModel::updateFullName,
                    onPhoneChange = viewModel::updatePhoneNumber,
                    onSave = viewModel::saveProfile,
                    onDiscard = viewModel::resetForm
                )
            }

            // Quick Actions
            item {
                QuickActionsCard(navController = navController)
            }

            // App Settings
            item {
                AppSettingsCard(
                    appSettings = appSettings,
                    onToggleDarkMode = onToggleDarkMode,
                    onToggleSounds = onToggleSounds,
                    onToggleOfflineMode = onToggleOfflineMode
                )
            }

            // Security Settings
            item {
                SecuritySettingsCard(
                    appSettings = appSettings,
                    onToggleBiometric = onToggleBiometric,
                    onToggleEmergencyContactsOnly = onToggleEmergencyContactsOnly
                )
            }

            // App Information
            item {
                AppInfoCard()
            }

            // Account Actions
            item {
                AccountActionsCard(
                    isSigningOut = isSigningOut,
                    onSignOut = { showSignOutDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Success Message
    successMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSuccessMessage()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar {
                Text(message)
            }
        }
    }

    // Error Message
    error?.let { errorMessage ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage)
            }
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out?") },
            text = {
                Text("Are you sure you want to sign out? You'll need to log in again to access your data.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                        // Navigate to login after sign out
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    user: com.safeguardme.app.data.models.User?,
    hasUnsavedChanges: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Card(
                modifier = Modifier.size(64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Text(
                text = user?.fullName ?: "User",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = SecurityUtils.maskEmail(user?.email ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (hasUnsavedChanges) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Unsaved Changes",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditCard(
    fullName: String,
    phoneNumber: String,
    nameError: String?,
    phoneError: String?,
    hasUnsavedChanges: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Personal Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = onNameChange,
                label = { Text("Full Name") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                label = { Text("Phone Number (Optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (hasUnsavedChanges) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discard")
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = canSave
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(navController: NavController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.ContactPhone,
                    text = "Emergency\nContacts",
                    onClick = { navController.navigate(Screen.EmergencyContacts.route) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.History,
                    text = "Incident\nHistory",
                    onClick = { navController.navigate(Screen.IncidentHistory.route) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.RecordVoiceOver,
                    text = "Voice\nTriggers",
                    onClick = { navController.navigate(Screen.Trigger.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppSettingsCard(
    appSettings: SettingsRepository.AppSettings,
    onToggleDarkMode: () -> Unit,
    onToggleSounds: () -> Unit,
    onToggleOfflineMode: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SettingItem(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                description = "Use dark theme throughout the app",
                isChecked = appSettings.darkMode,
                onCheckedChange = { onToggleDarkMode() }
            )

            SettingItem(
                icon = Icons.Default.VolumeUp,
                title = "Enable Sounds",
                description = "Play sounds for notifications and interactions",
                isChecked = appSettings.soundsEnabled,
                onCheckedChange = { onToggleSounds() }
            )

            SettingItem(
                icon = Icons.Default.CloudOff,
                title = "Allow Offline Mode",
                description = "Store data locally when offline",
                isChecked = appSettings.offlineModeAllowed,
                onCheckedChange = { onToggleOfflineMode() }
            )
        }
    }
}

@Composable
private fun SecuritySettingsCard(
    appSettings: SettingsRepository.AppSettings,
    onToggleBiometric: () -> Unit,
    onToggleEmergencyContactsOnly: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Security Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SettingItem(
                icon = Icons.Default.Fingerprint,
                title = "Biometric Authentication",
                description = "Use fingerprint or face unlock",
                isChecked = appSettings.biometricEnabled,
                onCheckedChange = { onToggleBiometric() }
            )

            SettingItem(
                icon = Icons.Default.Emergency,
                title = "Emergency Contacts Only",
                description = "Only show emergency contact notifications",
                isChecked = appSettings.emergencyContactsOnly,
                onCheckedChange = { onToggleEmergencyContactsOnly() }
            )

            /*item {
                /*VoiceDetectionSettingsCard(
                    voiceDetectionEnabled = viewModel.voiceDetectionEnabled.collectAsState().value,
                    onToggleVoiceDetection = viewModel::toggleVoiceDetection,
                    triggerKeyword = viewModel.triggerKeyword.collectAsState().value
                )*/
            }*/
        }
    }
}



@Composable
private fun AppInfoCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "App Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "License",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Academic License",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountActionsCard(
    isSigningOut: Boolean,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Account Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = !isSigningOut
            ) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSigningOut) "Signing Out..." else "Sign Out")
            }

            Text(
                text = "⚠️ Signing out will require you to log in again to access your data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}


/**
 * ✅ NEW: Voice detection settings card
 */
@Composable
private fun VoiceDetectionSettingsCard(
    voiceDetectionEnabled: Boolean,
    onToggleVoiceDetection: () -> Unit,
    triggerKeyword: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Voice Detection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SettingItem(
                icon = Icons.Default.RecordVoiceOver,
                title = "Always-On Voice Detection",
                description = if (triggerKeyword != null) {
                    "Listening for: \"$triggerKeyword\""
                } else {
                    "Set up trigger word in Voice Triggers"
                },
                isChecked = voiceDetectionEnabled,
                onCheckedChange = { onToggleVoiceDetection() }
            )

            if (voiceDetectionEnabled) {
                //VoiceDetectionStatusCard()
            }
        }
    }
}