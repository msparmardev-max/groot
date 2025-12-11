package com.example.groot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groot.ui.components.BrandingFooter
import com.example.groot.ui.onboarding.OnboardingActivity
import com.example.groot.ui.theme.GrootTheme
import com.example.groot.user.UserProfileManager
import com.example.groot.user.EmailPreferences
import androidx.compose.foundation.interaction.MutableInteractionSource
//import androidx.compose.material.ripple
import androidx.compose.runtime.remember
import androidx.compose.foundation.ComposeFoundationFlags

/**
 * LEARNING: SettingsActivity
 *
 * Purpose:
 * - User preferences management
 * - App configuration
 * - Profile editing
 * - Data management
 *
 * Features:
 * - Edit profile
 * - Music on/off
 * - Voice settings
 * - Theme selection
 * - Clear data
 * - About info
 */

class SettingsActivity : ComponentActivity() {

    private lateinit var userProfileManager: UserProfileManager
    private lateinit var emailPreferences: EmailPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //ComposeFoundationFlags.isNonComposedClickableEnabled = true

        // Initialize managers
        userProfileManager = UserProfileManager(this)
        emailPreferences = EmailPreferences(this)

        setContent {
            GrootTheme {
                SettingsScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen() {
        /**
         * LEARNING: State Management
         *
         * Load current settings from SharedPreferences
         * Update UI when values change
         */

        // User profile states
        val userName = remember { userProfileManager.getUserName() ?: "User" }
        val userEmail = remember { userProfileManager.getUserEmail() ?: "" }

        // App settings states
        var musicEnabled by remember {
            mutableStateOf(
                getSharedPreferences("groot_app_settings", MODE_PRIVATE)
                    .getBoolean("music_enabled", true)
            )
        }

        var voiceEnabled by remember {
            mutableStateOf(
                getSharedPreferences("groot_app_settings", MODE_PRIVATE)
                    .getBoolean("voice_enabled", true)
            )
        }

        var selectedTheme by remember {
            mutableStateOf(
                getSharedPreferences("groot_app_settings", MODE_PRIVATE)
                    .getString("theme", "System") ?: "System"
            )
        }

        var showLogoutDialog by remember { mutableStateOf(false) }
        var showEditProfileDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A237E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // User Profile Section
                UserProfileSection(
                    userName = userName,
                    userEmail = userEmail,
                    onEditClick = { showEditProfileDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // App Settings Section
                SettingsSectionTitle("App Settings")

                // Music Toggle
                SettingsToggleItem(
                    icon = Icons.Default.MusicNote,
                    title = "Background Music",
                    subtitle = "Play music on splash screen",
                    checked = musicEnabled,
                    onCheckedChange = { enabled ->
                        musicEnabled = enabled
                        getSharedPreferences("groot_app_settings", MODE_PRIVATE)
                            .edit()
                            .putBoolean("music_enabled", enabled)
                            .apply()

                        Toast.makeText(
                            this@SettingsActivity,
                            if (enabled) "Music enabled" else "Music disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Voice Toggle
                SettingsToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Voice Feedback",
                    subtitle = "Play 'I am Groot' voice",
                    checked = voiceEnabled,
                    onCheckedChange = { enabled ->
                        voiceEnabled = enabled
                        getSharedPreferences("groot_app_settings", MODE_PRIVATE)
                            .edit()
                            .putBoolean("voice_enabled", enabled)
                            .apply()

                        Toast.makeText(
                            this@SettingsActivity,
                            if (enabled) "Voice enabled" else "Voice disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Theme Selection
                SettingsClickableItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = "Current: $selectedTheme",
                    onClick = {
                        // Show theme selection dialog
                        Toast.makeText(
                            this@SettingsActivity,
                            "Theme selection coming soon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Data & Privacy Section
                SettingsSectionTitle("Data & Privacy")

                SettingsClickableItem(
                    icon = Icons.Default.Storage,
                    title = "Storage Usage",
                    subtitle = "View app data usage",
                    onClick = {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Storage info coming soon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickableItem(
                    icon = Icons.Default.Delete,
                    title = "Clear All Data",
                    subtitle = "Delete profile and settings",
                    onClick = { showLogoutDialog = true },
                    isDangerous = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // About Section
                SettingsSectionTitle("About")

                SettingsClickableItem(
                    icon = Icons.Default.Info,
                    title = "About Groot",
                    subtitle = "Version 1.0.0",
                    onClick = {
                        showAboutDialog()
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickableItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "Read our privacy policy",
                    onClick = {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Privacy policy coming soon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Branding Footer
                BrandingFooter()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Logout Confirmation Dialog
        if (showLogoutDialog) {
            LogoutConfirmationDialog(
                onConfirm = {
                    clearAllData()
                    showLogoutDialog = false
                },
                onDismiss = { showLogoutDialog = false }
            )
        }

        // Edit Profile Dialog
        if (showEditProfileDialog) {
            EditProfileDialog(
                currentName = userName,
                currentEmail = userEmail,
                onSave = { newName, newEmail ->
                    updateProfile(newName, newEmail)
                    showEditProfileDialog = false
                },
                onDismiss = { showEditProfileDialog = false }
            )
        }
    }

    /**
     * LEARNING: User Profile Section Component
     */
    @Composable
    private fun UserProfileSection(
        userName: String,
        userEmail: String,
        onEditClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1A237E),
                                Color(0xFF3949AB)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = userName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (userEmail.isNotBlank()) {
                                    Text(
                                        text = userEmail,
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onEditClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    /**
     * LEARNING: Section Title Component
     */
    @Composable
    private fun SettingsSectionTitle(title: String) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A237E),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    /**
     * LEARNING: Toggle Setting Item
     */
    @Composable
    private fun SettingsToggleItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF3949AB),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF3949AB),
                    checkedTrackColor = Color(0xFF3949AB).copy(alpha = 0.5f)
                )
            )
        }
    }

    /**
     * LEARNING: Clickable Setting Item
     */
    @Composable
    private fun SettingsClickableItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit,
        isDangerous: Boolean = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    indication = null,  // ✅ Material3 ripple explicitly use karo
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isDangerous) Color.Red else Color(0xFF3949AB),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDangerous) Color.Red else Color.Black
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = Color.Gray
            )
        }
    }

    /**
     * LEARNING: Logout Confirmation Dialog
     */
    @Composable
    private fun LogoutConfirmationDialog(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Clear All Data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will delete:\n" +
                            "• Your profile information\n" +
                            "• Email contacts\n" +
                            "• All saved settings\n\n" +
                            "This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Clear & Logout")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    /**
     * LEARNING: Edit Profile Dialog
     */
    @Composable
    private fun EditProfileDialog(
        currentName: String,
        currentEmail: String,
        onSave: (String, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var name by remember { mutableStateOf(currentName) }
        var email by remember { mutableStateOf(currentEmail) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Edit Profile",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name, email)
                        } else {
                            Toast.makeText(
                                this@SettingsActivity,
                                "Name cannot be empty",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A237E)
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    /**
     * Update user profile
     */
    private fun updateProfile(newName: String, newEmail: String) {
        val currentPhone = userProfileManager.getUserPhone() ?: ""
        val currentLanguage = userProfileManager.getPreferredLanguage()

        userProfileManager.saveUserProfile(
            name = newName,
            email = newEmail,
            phone = currentPhone,
            language = currentLanguage
        )

        Toast.makeText(this, "✅ Profile updated", Toast.LENGTH_SHORT).show()

        // Recreate activity to show updated info
        recreate()
    }

    /**
     * Clear all user data
     */
    private fun clearAllData() {
        // Clear user profile
        userProfileManager.clearUserData()

        // Clear email settings
        emailPreferences.clearEmailData()

        // Clear app settings
        getSharedPreferences("groot_app_settings", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        Toast.makeText(this, "✅ All data cleared", Toast.LENGTH_SHORT).show()

        // Navigate to onboarding
        val intent = Intent(this, OnboardingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Show about dialog
     */
    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Groot")
            .setMessage("""
                Groot - Your Personal AI Assistant
                Version 1.0.0
                
                Developed with ❤️ in India 🇮🇳
                by RS Labs
                
                Features:
                • Voice-controlled assistant
                • Smart email management
                • Reminder system
                • Weather updates
                • And much more!
                
                © 2024 RS Labs. All rights reserved.
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
}