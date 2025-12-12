package com.example.groot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groot.MainActivity
import com.example.groot.ui.theme.GrootTheme
import com.example.groot.user.EmailPreferences
import com.example.groot.user.UserProfileManager

class UserSetupActivity : ComponentActivity() {

    private lateinit var userProfileManager: UserProfileManager
    private lateinit var emailPreferences: EmailPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userProfileManager = UserProfileManager(this)
        emailPreferences = EmailPreferences(this)

        setContent {
            GrootTheme {
                SetupScreen()
            }
        }
    }

    @Composable
    private fun SetupScreen() {
        var currentStep by remember { mutableStateOf(1) }
        var userName by remember { mutableStateOf("") }
        var userEmail by remember { mutableStateOf("") }
        var senderEmail by remember { mutableStateOf("") }
        var appPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF283593),
                            Color(0xFF3949AB)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Logo/Title
                Text(
                    text = "🎤",
                    fontSize = 64.sp
                )

                Text(
                    text = "Welcome to Groot",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = Color.White
                )

                Text(
                    text = "Your Personal AI Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Progress indicator
                StepIndicator(currentStep = currentStep, totalSteps = 2)

                Spacer(modifier = Modifier.height(32.dp))

                // Content card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        when (currentStep) {
                            1 -> {
                                // Step 1: User Profile
                                Text(
                                    text = "Step 1: Your Profile",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF1A237E)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Let's get to know you",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                OutlinedTextField(
                                    value = userName,
                                    onValueChange = { userName = it },
                                    label = { Text("Your Name") },
                                    placeholder = { Text("e.g., Mahendra Singh") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, "Name")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = userEmail,
                                    onValueChange = { userEmail = it },
                                    label = { Text("Your Email (Optional)") },
                                    placeholder = { Text("e.g., your.email@gmail.com") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Email, "Email")
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        if (userName.isBlank()) {
                                            Toast.makeText(
                                                this@UserSetupActivity,
                                                "Please enter your name",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            currentStep = 2
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF3949AB)
                                    )
                                ) {
                                    Text("Next", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, "Next")
                                }
                            }

                            2 -> {
                                // Step 2: Email Configuration
                                Text(
                                    text = "Step 2: Email Setup",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF1A237E)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Configure your Gmail for sending emails",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Info card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFFF9C4)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Info",
                                            tint = Color(0xFFF57F17)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Need App Password?",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "1. Enable 2-Step Verification in Google Account\n2. Generate App Password for 'Mail'\n3. Use that 16-character password here",
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = senderEmail,
                                    onValueChange = { senderEmail = it },
                                    label = { Text("Gmail Address") },
                                    placeholder = { Text("your.email@gmail.com") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Email, "Email")
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = appPassword,
                                    onValueChange = { appPassword = it },
                                    label = { Text("App Password (16 chars)") },
                                    placeholder = { Text("abcd efgh ijkl mnop") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, "Password")
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                if (passwordVisible) Icons.Default.Check else Icons.Default.Close,
                                                "Toggle"
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible)
                                        VisualTransformation.None
                                    else
                                        PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { currentStep = 1 },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, "Back")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Back")
                                    }

                                    Button(
                                        onClick = {
                                            if (senderEmail.isBlank() || appPassword.isBlank()) {
                                                Toast.makeText(
                                                    this@UserSetupActivity,
                                                    "Please fill all fields",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                isLoading = true
                                                completeSetup(
                                                    userName,
                                                    userEmail,
                                                    senderEmail,
                                                    appPassword
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        )
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White
                                            )
                                        } else {
                                            Text("Finish")
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(Icons.Default.Done, "Finish")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                TextButton(
                                    onClick = {
                                        // Skip email setup
                                        userProfileManager.saveUserProfile(userName, userEmail)
                                        navigateToMain()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Skip for now (setup later)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StepIndicator(currentStep: Int, totalSteps: Int) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                val step = index + 1
                val isActive = step <= currentStep

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = step.toString(),
                        color = if (isActive) Color(0xFF1A237E) else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (step < totalSteps) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(
                                if (step < currentStep) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }

    private fun completeSetup(
        userName: String,
        userEmail: String,
        senderEmail: String,
        appPassword: String
    ) {
        try {
            // Save user profile
            userProfileManager.saveUserProfile(userName, userEmail)

            // Save email credentials
            val cleanPassword = appPassword.replace(" ", "")
            emailPreferences.saveSenderCredentials(senderEmail, cleanPassword)

            Toast.makeText(this, "✅ Setup completed!", Toast.LENGTH_SHORT).show()

            navigateToMain()

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}