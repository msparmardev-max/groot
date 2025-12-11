package com.example.groot

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.groot.ui.theme.GrootTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.*

// ✅ ADD THESE NEW IMPORTS BELOW (for animations & interactive UI):
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random
import com.example.groot.TTSManager
import androidx.lifecycle.lifecycleScope
import com.example.groot.user.EmailPreferences
import com.example.groot.user.UserProfileManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.groot.ui.EmailManagementActivity
import com.example.groot.ui.ContactActivity
import com.example.groot.ui.SettingsActivity
import com.example.groot.ui.UserSetupActivity
import com.example.groot.ui.onboarding.OnboardingActivity
import com.example.groot.ui.components.BrandingFooter
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.interaction.MutableInteractionSource
//import androidx.compose.material.ripple
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    //for user management and for email management
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var emailPreferences: EmailPreferences
    private var grootService: GrootService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            grootService = (binder as GrootService.LocalBinder).getService()
            isBound = true
            Log.d(TAG, "GrootService bound successfully")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            grootService = null
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, GrootService::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSIONS_REQUEST_CODE = 1001
        private const val WRITE_SETTINGS_REQUEST_CODE = 1002
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS,
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_EXACT_ALARM"
        )
    }

    // Voice components

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var ttsManager: TTSManager

    // Core managers
    private lateinit var memoryManager: MemoryManager

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

    // State
    private var isListening = false
    private var isProcessing = false

    private var shouldAutoRestartMic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        //ComposeFoundationFlags.isNonComposedClickableEnabled = true
        super.onCreate(savedInstanceState)
        // ⭐ ADD THESE LINES BEFORE setContent
        userProfileManager = UserProfileManager(this)
        emailPreferences = EmailPreferences(this)

        // ⭐ ADD SETUP CHECK
        if (userProfileManager.isFirstLaunch()) {
            // First time - open setup
            startActivity(Intent(this, UserSetupActivity::class.java))
            finish()
            return
        }
        /**
         * LEARNING: Activity Launch Flow
         *
         * Decision tree:
         * 1. Is first launch? → OnboardingActivity
         * 2. Setup completed? → MainActivity
         * 3. Else → OnboardingActivity
         *
         * Why this check?
         * - User experience optimization
         * - Don't show onboarding every time
         * - Clean app flow
         */
        if (!userProfileManager.isSetupCompleted()) {
            // First time user - redirect to onboarding
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        // ⭐ IMPORTANT: Add this after onboarding check
        // Load user's preferred language
        val preferredLanguage = userProfileManager.getPreferredLanguage()
        Log.d("MainActivity", "User prefers: $preferredLanguage")

        memoryManager = MemoryManager(this) // Pass context
        //permissionsHelper.requestNotificationPermission(this)
        requestPermissions()
        initializeVoiceComponents()

        setContent {
            GrootTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenWithDrawer()
                }
            }
        }
    }
    // ⭐ ADD NEW COMPOSABLE FOR DRAWER
    @Composable
    private fun MainScreenWithDrawer() {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    onCloseDrawer = { scope.launch { drawerState.close() } }
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Your existing GrootUI
                GrootUI()

                // Hamburger icon overlay
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }
                // ✅ ADD TEST BUTTON:
                FloatingActionButton(
                    onClick = { testBirthdaySystem() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Color(0xFFE91E63)
                ) {
                    Text("🎂", fontSize = 24.sp)
                }
            }
        }
    }

    // ⭐ ADD DRAWER CONTENT
    @Composable
    private fun DrawerContent(onCloseDrawer: () -> Unit) {
        /**
         * LEARNING: Dynamic Data Loading
         *
         * Load user data from SharedPreferences
         * Display in UI
         */
        val userName = userProfileManager.getUserName() ?: "User"
        val userEmail = userProfileManager.getUserEmail() ?: ""
        val userPhone = userProfileManager.getUserPhone() ?: ""
        val language = userProfileManager.getPreferredLanguage()

        ModalDrawerSheet {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ⭐ UPDATED: Enhanced Header with more info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF1A237E),
                                    Color(0xFF3949AB)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        // Profile icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                               // .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // User name
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        // User email (if exists)
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        // Language indicator
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Language",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = language,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Rest of your existing menu items...
                DrawerMenuItem(
                    icon = Icons.Default.Email,
                    title = "Email Management",
                    onClick = {
                        startActivity(Intent(this@MainActivity, EmailManagementActivity::class.java))
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Email,
                    title = "Birthday Management",
                    onClick = {
                        startActivity(Intent(this@MainActivity, ContactActivity::class.java))
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    onClick = {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        onCloseDrawer()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    onClick = {
                       // showAboutDialog()
                        onCloseDrawer()
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // ⭐ ADD: Branding footer at bottom
                BrandingFooter()

                Spacer(modifier = Modifier.height(8.dp))

                // Logout
                DrawerMenuItem(
                    icon = Icons.Default.ExitToApp,
                    title = "Clear Data & Logout",
                    onClick = {
                        showLogoutConfirmation()
                    }
                )
            }
        }
    }

    /**
     * LEARNING: AlertDialog for Confirmation
     *
     * Why use dialog?
     * - Prevents accidental actions
     * - Professional UX
     * - User confirmation required
     */
   /*private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data?")
            .setMessage("This will delete your profile, email settings, and all saved data. This action cannot be undone.")
            .setPositiveButton("Clear & Logout") { _, _ ->
                // Clear all data
                userProfileManager.clearUserData()
                emailPreferences.clearEmailData()

                Toast.makeText(this, "✅ Data cleared", Toast.LENGTH_SHORT).show()

                // Restart to onboarding
                val intent = Intent(this, OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Composable
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
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
        """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }*/

    @Composable
    private fun DrawerMenuItem(
        icon: ImageVector,
        title: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    indication = null,  // ✅ Material3 ripple explicitly use karo
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF3949AB)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    private fun showLogoutConfirmation() {
        // Show AlertDialog (implementation needed)
    }
    @Composable
    private fun CompactProcessingAnimation() {
        val infiniteTransition = rememberInfiniteTransition(label = "processing")

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size((50 - index * 15).dp)
                        .rotate(rotation + (index * 120f))
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFFF9800).copy(alpha = 0.2f),
                                    Color(0xFFFF9800),
                                    Color(0xFFFF9800).copy(alpha = 0.2f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
        }
    }
    @Composable
    private fun GrootUI() {
        val view = LocalView.current

        var isCurrentlyListening by remember { mutableStateOf(false) }
        var isCurrentlyProcessing by remember { mutableStateOf(false) }
        var isConnected by remember { mutableStateOf(false) }
        var refreshTrigger by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                isCurrentlyListening = isListening
                isCurrentlyProcessing = isProcessing

                val serviceBound = grootService != null && isBound
                isConnected = if (serviceBound) {
                    try {
                        grootService?.isServerConnected() ?: false
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }

                refreshTrigger++
                delay(50)
            }
        }

        val messages = remember(refreshTrigger) {
            memoryManager.getRecentConversations(50)
        }

        val listState = rememberLazyListState()

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        // Animated gradient
        val infiniteTransition = rememberInfiniteTransition(label = "background")

        val gradientOffset1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradient1"
        )

        val gradientOffset2 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradient2"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E).copy(alpha = 0.85f + gradientOffset1 * 0.15f),
                            Color(0xFF283593).copy(alpha = 0.80f + gradientOffset2 * 0.20f),
                            Color(0xFF3949AB).copy(alpha = 0.85f + gradientOffset1 * 0.15f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // COMPACT HEADER - Only text, no glow
                Text(
                    text = "Groot",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )

                // COMPACT STATUS - Single line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isConnected) "Online" else "Offline",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFFB0BEC5)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MAIN CONTENT
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (messages.isEmpty()) {
                        // Empty State
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🎤",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tap the mic to start",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                    } else {
                        // Chat Messages
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(messages) { index, entry ->
                                ImprovedMessageCard(entry = entry)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // LISTENING/PROCESSING OVERLAY (Shows above button)
                if (isCurrentlyListening || isCurrentlyProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isCurrentlyListening -> {
                                // WAVE VISUALIZATION
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🎤 Listening...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFFF44336)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    VoiceWaveVisualization(isListening = true)
                                }
                            }
                            isCurrentlyProcessing -> {
                                // PROCESSING ANIMATION
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⚙️ Processing...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFFFF9800)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    CompactProcessingAnimation()
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(120.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // COMPACT MIC BUTTON
                CompactMicButton(
                    isListening = isCurrentlyListening,
                    isProcessing = isCurrentlyProcessing,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        startListeningForCommand()
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // STATUS TEXT
                Text(
                    text = "Tap to Speak",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFFB0BEC5).copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    @Composable
    private fun CircularWaveVisualization(isListening: Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "circularWave")

        // Multiple wave rings
        val waves = remember { List(8) { it } }

        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            waves.forEach { index ->
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 1500,
                            delayMillis = index * 150,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "wave_$index"
                )

                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 1500,
                            delayMillis = index * 150,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha_$index"
                )

                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .scale(scale)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF4CAF50).copy(alpha = alpha),
                                shape = CircleShape
                            )
                    )
                }
            }

            // Center mic
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Mic",
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }
    }
    @Composable
    private fun VoiceWaveVisualization(isListening: Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "wave")

        val bars = remember { List(15) { Random.nextFloat() } }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bars.forEachIndexed { index, baseHeight ->
                val height by infiniteTransition.animateFloat(
                    initialValue = 10f,
                    targetValue = 10f + (baseHeight * 40f),
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 350 + (index * 30),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$index"
                )

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(if (isListening) height.dp else 10.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF44336),
                                    Color(0xFFFF5722)
                                )
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .animateContentSize()
                )
            }
        }
    }
    @Composable
    private fun FloatingParticles() {
        val particles = remember {
            List(15) {
                ParticleState(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    size = Random.nextInt(3, 8),
                    speed = Random.nextFloat() * 0.5f + 0.5f
                )
            }
        }

        particles.forEach { particle ->
            val infiniteTransition = rememberInfiniteTransition(label = "particle")

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween((3000 / particle.speed).toInt(), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "particleY"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "particleAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (particle.x * 350).dp,
                            y = (offsetY * 800).dp
                        )
                        .size(particle.size.dp)
                        .alpha(alpha)
                        .background(
                            color = Color.White,
                            shape = CircleShape
                        )
                )
            }
        }
    }

    data class ParticleState(
        val x: Float,
        val y: Float,
        val size: Int,
        val speed: Float
    )
    @Composable
    private fun AnimatedHeaderWithGlow() {
        val infiniteTransition = rememberInfiniteTransition(label = "headerGlow")

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "headerScale"
        )

        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "headerGlow"
        )

        Box(
            contentAlignment = Alignment.Center
        ) {
            // Subtle glow (reduced)
            Text(
                text = "Groot",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp, // ✅ REDUCED: 48sp → 32sp
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = Color(0xFF4CAF50).copy(alpha = glowAlpha),
                modifier = Modifier
                    .scale(scale * 1.03f)
                    .blur(8.dp) // ✅ REDUCED: 12dp → 8dp
            )

            // Main text
            Text(
                text = "Groot",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp, // ✅ REDUCED: 48sp → 32sp
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = Color.White,
                modifier = Modifier.scale(scale)
            )
        }
    }
    @Composable
    private fun SiriListeningAnimation() {
        val infiniteTransition = rememberInfiniteTransition(label = "siri")

        // Multiple circles with different timings
        val circles = listOf(
            Triple(240.dp, 0, 0.15f),
            Triple(200.dp, 100, 0.25f),
            Triple(160.dp, 200, 0.35f),
            Triple(120.dp, 300, 0.45f)
        )

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            circles.forEach { (size, delay, maxAlpha) ->
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, delayMillis = delay, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale_$size"
                )

                val alpha by infiniteTransition.animateFloat(
                    initialValue = maxAlpha,
                    targetValue = 0.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, delayMillis = delay),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha_$size"
                )

                Box(
                    modifier = Modifier
                        .size(size)
                        .scale(scale)
                        .background(
                            color = Color(0xFF4CAF50).copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
            }

            // Center mic with pulse
            val micScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micScale"
            )

            val micRotation by infiniteTransition.animateFloat(
                initialValue = -8f,
                targetValue = 8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micRotation"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(micScale)
                    .background(
                        color = Color(0xFF4CAF50),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Listening",
                    tint = Color.White,
                    modifier = Modifier
                        .size(50.dp)
                        .rotate(micRotation)
                )
            }
        }
    }

    @Composable
    private fun ProcessingAnimation() {
        val infiniteTransition = rememberInfiniteTransition(label = "processing")

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Multiple rotating circles
            repeat(4) { index ->
                val delay = index * 100

                val circleRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000 + delay, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "circle_$index"
                )

                Box(
                    modifier = Modifier
                        .size((140 - index * 30).dp)
                        .rotate(circleRotation)
                        .scale(scale)
                        .border(
                            width = (3 + index).dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50).copy(alpha = 0.2f),
                                    Color(0xFF4CAF50).copy(alpha = 0.8f),
                                    Color(0xFF4CAF50).copy(alpha = 0.2f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Center gear
            Text(
                text = "⚙️",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.rotate(rotation)
            )
        }
    }

    @Composable
    private fun IdleAnimation() {
        val infiniteTransition = rememberInfiniteTransition(label = "idle")

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idleScale"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idleAlpha"
        )

        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = alpha * 0.3f),
                        shape = CircleShape
                    )
            )

            // Inner circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }

    @Composable
    private fun InteractiveMicButton(
        isListening: Boolean,
        isProcessing: Boolean,
        onClick: () -> Unit
    ) {
        var isPressed by remember { mutableStateOf(false) }

        val scale by animateFloatAsState(
            targetValue = when {
                isPressed -> 0.92f
                isListening -> 1.08f // ✅ REDUCED: 1.12f → 1.08f
                isProcessing -> 1.03f
                else -> 1f
            },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "buttonScale"
        )

        val buttonColor by animateColorAsState(
            targetValue = when {
                isListening -> Color(0xFFF44336)
                isProcessing -> Color(0xFFFF9800)
                else -> Color(0xFF4CAF50)
            },
            animationSpec = tween(300),
            label = "buttonColor"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "buttonEffects")

        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f, // ✅ REDUCED: 1.4f → 1.3f
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f, // ✅ REDUCED: 0.7f → 0.6f
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(110.dp) // ✅ REDUCED: 140dp → 110dp
        ) {
            // Glow rings (smaller and fewer)
            if (isListening) {
                repeat(2) { index -> // ✅ REDUCED: 3 → 2 rings
                    Box(
                        modifier = Modifier
                            .size((85 + index * 20).dp) // ✅ REDUCED sizes
                            .scale(pulseScale)
                            .border(
                                width = (3 - index).dp,
                                color = Color(0xFFF44336).copy(alpha = glowAlpha / (index + 1.5f)),
                                shape = CircleShape
                            )
                    )
                }
            }

            // Outer glow (reduced)
            Box(
                modifier = Modifier
                    .size(75.dp) // ✅ REDUCED: 100dp → 75dp
                    .scale(scale * 1.08f)
                    .background(
                        color = buttonColor.copy(alpha = 0.25f), // ✅ REDUCED alpha
                        shape = CircleShape
                    )
            )

            // Main FAB (compact size)
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier
                    .size(65.dp) // ✅ REDUCED: 85dp → 65dp
                    .scale(scale)
                    .rotate(if (isProcessing) rotation else 0f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            }
                        )
                    },
                containerColor = buttonColor,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp, // ✅ REDUCED: 12dp → 8dp
                    pressedElevation = 12.dp,
                    hoveredElevation = 10.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Speak",
                    modifier = Modifier.size(32.dp) // ✅ REDUCED: 42dp → 32dp
                )
            }
        }
    }
    @Composable
    private fun CompactMicButton(
        isListening: Boolean,
        isProcessing: Boolean,
        onClick: () -> Unit
    ) {
        var isPressed by remember { mutableStateOf(false) }

        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.9f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "buttonScale"
        )

        val buttonColor by animateColorAsState(
            targetValue = when {
                isListening -> Color(0xFFF44336)
                isProcessing -> Color(0xFFFF9800)
                else -> Color(0xFF4CAF50)
            },
            animationSpec = tween(300),
            label = "buttonColor"
        )

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            containerColor = buttonColor,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Speak",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    @Composable
    private fun ImprovedMessageCard(entry: MemoryManager.ConversationEntry) {
        val isUser = entry.type == MemoryManager.ConversationType.USER_INPUT

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (entry.type) {
                        MemoryManager.ConversationType.USER_INPUT -> Color(0xFF7C4DFF) // ✅ Purple (stands out)
                        MemoryManager.ConversationType.ASSISTANT_RESPONSE -> Color(0xFF424242) // ✅ Dark grey (readable)
                        else -> Color(0xFF37474F)
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = when (entry.type) {
                            MemoryManager.ConversationType.USER_INPUT -> "You"
                            MemoryManager.ConversationType.ASSISTANT_RESPONSE -> "Groot"
                            else -> "System"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (isUser) Color(0xFFB39DDB) else Color(0xFF81C784)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.message
                            .removePrefix("User: ")
                            .removePrefix("Assistant: ")
                            .removePrefix("System: "),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = entry.timestamp.split(" ").lastOrNull() ?: "",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
        }
    }
    @Composable
    private fun AnimatedStatusIndicator(isConnected: Boolean, isProcessing: Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "status")

        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "statusGlow"
        )

        val dotScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f, // ✅ REDUCED: 1.3f → 1.2f
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotScale"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp) // ✅ REDUCED: 12dp → 10dp
                    .scale(if (isProcessing) dotScale else 1f)
                    .background(
                        color = if (isConnected)
                            Color(0xFF4CAF50).copy(alpha = if (isProcessing) glowAlpha else 1f)
                        else
                            Color(0xFFFFC107).copy(alpha = if (isProcessing) glowAlpha else 1f),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(6.dp)) // ✅ REDUCED: 8dp → 6dp

            Text(
                text = if (isConnected) "Online Mode" else "Offline Mode",
                style = MaterialTheme.typography.bodySmall, // ✅ Changed from bodyMedium
                color = Color(0xFFB0BEC5)
            )
        }
    }
    @Composable
    private fun EmptyStateAnimation() {
        val infiniteTransition = rememberInfiniteTransition(label = "empty")

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emptyAlpha"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp), // ✅ REDUCED: 32dp → 24dp
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎤",
                style = MaterialTheme.typography.displayMedium, // ✅ Smaller than displayLarge
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No conversations yet.\nTap the mic to start!",
                style = MaterialTheme.typography.bodyMedium, // ✅ Changed from bodyLarge
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
    @Composable
    private fun AnimatedStatusText(isListening: Boolean, isProcessing: Boolean) {
        val text = when {
            isListening -> "🎤 Listening..."
            isProcessing -> "⚙️ Processing..."
            else -> "Tap to Speak"
        }

        val infiniteTransition = rememberInfiniteTransition(label = "statusText")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "textAlpha"
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy( // ✅ Changed from bodyMedium
                fontWeight = if (isListening || isProcessing) FontWeight.Medium else FontWeight.Normal,
                fontSize = 13.sp // ✅ Explicit small size
            ),
            color = Color(0xFFB0BEC5).copy(
                alpha = if (isListening || isProcessing) alpha else 0.8f
            )
        )
    }

    private fun requestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }

        // Request WRITE_SETTINGS permission separately (it's special)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                Log.w(TAG, "WRITE_SETTINGS permission not granted")
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request WRITE_SETTINGS permission", e)
                }
            }
        }
    }

    private fun initializeVoiceComponents() {
        initializeSpeechRecognizer()
        initializeTTSManager()
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                    Log.d(TAG, "Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    Log.d(TAG, "End of speech")
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error: $error"
                    }
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        speak("Sorry, I didn’t hear that. Please repeat.")
                        restartListening()
                    } else if (!isProcessing) {
                        restartListening()
                    }

                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { command ->
                        Log.d(TAG, "Recognized: $command")
                        processVoiceCommand(command)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    /*private fun initializeTextToSpeech() {
        tts = TextToSpeech(this, this)
    }*/
    private fun initializeTTSManager() {
        ttsManager = TTSManager(this)

        // Launch coroutine from main thread to initialize TTS
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ttsManager.initialize()
                Log.d(TAG, "TTSManager initialized successfully")

                withContext(Dispatchers.Main) {
                    // Pass TTSManager to GrootService
                    grootService?.setTTSManager(ttsManager)

                    // Initial greeting
                    ttsManager.speak(
                        "मैं ग्रूट हूं, आपका AI सहायक। आपकी मदद के लिए तैयार हूं।",
                        emotion = "friendly",
                        callback = null,
                        isHindi = true
                    )
                }
            }
        }
    }

    private fun startListeningForCommand() {
        if (isListening || isProcessing) {
            Log.w(TAG, "Already listening or processing")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN,en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening for command")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            isListening = false
        }
    }

    /*private fun processVoiceCommand(command: String) {
        if (isProcessing) {
            Log.w(TAG, "Already processing a command")
            return
        }

        isProcessing = true
        Log.i(TAG, "Processing command: $command")
        memoryManager.addConversation("User: $command")

        // Use GrootService to process the command
        grootService?.processCommand(command) { reply, action, target, autoRestart ->
            shouldAutoRestartMic = autoRestart
            Log.i(TAG, "GrootService Response: $reply | Action: $action | Target: $target")
            memoryManager.addConversation("Assistant: $reply")
            speak(reply) {
                isProcessing = false
                Log.d(TAG, "Finished processing command")
            }
        } ?: run {
            Log.w(TAG, "GrootService not bound yet")
            val msg = "Service is not ready, please try again."
            memoryManager.addConversation("System: $msg")
            speak(msg) {
                isProcessing = false
            }
        }
    }*/
    @RequiresApi(Build.VERSION_CODES.O)
    private fun processVoiceCommand(command: String) {
        if (isProcessing) {
            Log.w(TAG, "Already processing a command")
            return
        }

        isProcessing = true
        Log.i(TAG, "Processing command: $command")
        memoryManager.addConversation("User: $command")

        // Use GrootService to process the command
        grootService?.processCommand(command) { reply, action, target, autoRestart, emotion ->  // ← ADD emotion
            shouldAutoRestartMic = autoRestart
            Log.i(TAG, "GrootService Response: $reply | Action: $action | Emotion: $emotion")
            memoryManager.addConversation("Assistant: $reply")

            speak(reply, emotion) {  // ← PASS emotion
                isProcessing = false
                Log.d(TAG, "Finished processing command")
            }
        } ?: run {
            Log.w(TAG, "GrootService not bound yet")
            val msg = "Service is not ready, please try again."
            memoryManager.addConversation("System: $msg")

            speak(msg, "neutral") {  // ← ADD emotion
                isProcessing = false
            }
        }
    }

    /*private fun speak(text: String, onComplete: (() -> Unit)? = null) {
        tts?.let { textToSpeech ->
            val locale = if (text.matches(Regex(".*[\\u0900-\\u097F].*"))) {
                Locale("hi", "IN")
            } else {
                Locale.US
            }
            textToSpeech.language = locale

            val utteranceId = UUID.randomUUID().toString()

            if (onComplete != null) {
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        Log.d(TAG, "TTS started: $id")
                    }

                    override fun onDone(id: String?) {
                        if (id == utteranceId) {
                            Log.d(TAG, "TTS completed: $id")
                            handler.post {
                                onComplete?.invoke()

                                // Auto-restart mic if state requires it
                                if (shouldAutoRestartMic) {
                                    Log.d(TAG, "Auto-restarting mic for follow-up")
                                    handler.postDelayed({
                                        startListeningForCommand()
                                    }, 500) // Small delay for smooth transition
                                }
                            }
                        }
                    }

                    override fun onError(id: String?) {
                        if (id == utteranceId) {
                            Log.e(TAG, "TTS error: $id")
                            handler.post { onComplete() }
                        }
                    }
                })
            }

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            try {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                Log.d(TAG, "Speaking: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to speak", e)
                onComplete?.invoke()
            }
        } ?: run {
            Log.w(TAG, "TTS not initialized")
            onComplete?.invoke()
        }
    }*/
    private fun speak(
        text: String,
        emotion: String = "neutral",
        onComplete: (() -> Unit)? = null
    ) {
        if (!::ttsManager.isInitialized) {
            Log.w(TAG, "TTSManager not initialized")
            onComplete?.invoke()
            return
        }
        val isHindi = LanguageDetector.isHindiIntent(text)
        ttsManager.speak(text, emotion, object : TTSManager.TTSCallback {
            override fun onStart() {
                Log.d(TAG, "TTS started speaking")
            }

            override fun onDone() {
                Log.d(TAG, "TTS completed")
                handler.post {
                    onComplete?.invoke()

                    // Auto-restart mic if needed
                    if (shouldAutoRestartMic) {
                        Log.d(TAG, "Auto-restarting mic for follow-up")
                        handler.postDelayed({
                            startListeningForCommand()
                        }, 500)
                    }
                }
            }

            override fun onError() {
                Log.e(TAG, "TTS error occurred")
                handler.post {
                    onComplete?.invoke()
                }
            }
        },isHindi)
    }

    private fun restartListening() {
        handler.postDelayed({
            if (!isProcessing && !isListening) {
                Log.d(TAG, "Auto-restarting listening")
            }
        }, 1000)
    }

    /*override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                val hindiResult = textToSpeech.setLanguage(Locale("hi", "IN"))
                if (hindiResult == TextToSpeech.LANG_MISSING_DATA ||
                    hindiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Hindi language not supported, falling back to English")
                    textToSpeech.setLanguage(Locale.US)
                }

                textToSpeech.setPitch(0.9f)
                textToSpeech.setSpeechRate(0.9f)

                Log.d(TAG, "TTS initialized successfully")
                speak("मैं ग्रूट हूं, आपका AI सहायक। आपकी मदद के लिए तैयार हूं।")
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }*/

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()

        if (::ttsManager.isInitialized) {
            ttsManager.shutdown()
        }

        Log.d(TAG, "MainActivity destroyed")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {

                Log.d(TAG, "All permissions granted")
                speak("All permissions granted. I'm ready to help!")
            } else {
                Log.w(TAG, "Some permissions denied")
                speak("I need all permissions to work properly")
            }
        }
    }
    /**
     * Manual birthday system test
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun testBirthdaySystem() {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "═══════════════════════════════")
                Log.d("MainActivity", "🧪 MANUAL BIRTHDAY CHECK")

                val birthdayManager = com.example.groot.birthday.BirthdayManager(this@MainActivity)
                val communicationManager = com.example.groot.birthday.CommunicationManager(this@MainActivity)

                // Check birthdays
                val birthdays = birthdayManager.checkAndSendBirthdayWishes()

                Log.d("MainActivity", "Found ${birthdays.size} birthdays today")

                if (birthdays.isEmpty()) {
                    Log.d("MainActivity", "📭 No birthdays today")

                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "📭 No birthdays today",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    birthdays.forEach { contact ->
                        Log.d("MainActivity", "───────────────────────────────")
                        Log.d("MainActivity", "🎉 ${contact.name}")
                        Log.d("MainActivity", "Phone: ${contact.phoneNumber}")
                        Log.d("MainActivity", "Email: ${contact.email}")

                        // Generate message
                        val message = communicationManager.generateBirthdayMessage(contact)
                        Log.d("MainActivity", "Message: ${message.take(50)}...")

                        // Send wishes
                        val result = communicationManager.sendBirthdayWish(
                            contact = contact,
                            message = message,
                            viaSMS = contact.wishViaSMS,
                            viaEmail = contact.wishViaEmail
                        )

                        Log.d("MainActivity", "SMS: ${result.smsSuccess}, Email: ${result.emailSuccess}")
                    }

                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "🎉 Found ${birthdays.size} birthdays! Check Logcat",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }

                Log.d("MainActivity", "═══════════════════════════════")

            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Test failed", e)
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "❌ Error: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}
