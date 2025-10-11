package com.example.groot

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.*
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

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
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
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
            Manifest.permission.READ_PHONE_STATE
        )
    }

    // Voice components
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    // Core managers
    private lateinit var memoryManager: MemoryManager

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

    // State
    private var isListening = false
    private var isProcessing = false

    private var shouldAutoRestartMic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memoryManager = MemoryManager(this) // Pass context
        requestPermissions()
        initializeVoiceComponents()

        setContent {
            GrootTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GrootUI()
                }
            }
        }
    }

    @Composable
    private fun GrootUI() {
        var refreshTrigger by remember { mutableStateOf(0) }
        var isConnected by remember { mutableStateOf(false) }
        var isCurrentlyListening by remember { mutableStateOf(false) }
        var isCurrentlyProcessing by remember { mutableStateOf(false) }

        // Update state periodically
        LaunchedEffect(Unit) {
            while (true) {
                val serviceBound = grootService != null && isBound

                // FIX: Check actual server connection
                isConnected = if (serviceBound) {
                    try {
                        grootService?.isServerConnected() ?: false
                    } catch (e: Exception) {
                        Log.e(TAG, "Connection check error", e)
                        false
                    }
                } else {
                    false
                }

                isCurrentlyListening = isListening
                isCurrentlyProcessing = isProcessing
                refreshTrigger++

                delay(500)  // Check every 5 seconds
            }
        }

        // Get messages using the method that exists in MemoryManager
        val messages = remember(refreshTrigger) {
            memoryManager.getRecentConversations(50)
        }

        val listState = rememberLazyListState()

        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Groot AI Assistant",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Connection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isConnected) "🟢 Online Mode" else "🟡 Offline Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = if (isConnected) "AI Server Connected" else "Basic Features Available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable conversation area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "No conversations yet. Tap 'Speak' to start!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(messages) { entry ->
                        MessageCard(entry = entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speak Button
            Button(
                onClick = { startListeningForCommand() },
                enabled = !isCurrentlyListening && !isCurrentlyProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        isCurrentlyListening -> "🎤 Listening..."
                        isCurrentlyProcessing -> "⚙️ Processing..."
                        else -> "🎤 Tap to Speak"
                    }
                )
            }
        }
    }

    @Composable
    private fun MessageCard(entry: MemoryManager.ConversationEntry) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (entry.type) {
                    MemoryManager.ConversationType.USER_INPUT ->
                        MaterialTheme.colorScheme.primaryContainer
                    MemoryManager.ConversationType.ASSISTANT_RESPONSE ->
                        MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when (entry.type) {
                        MemoryManager.ConversationType.USER_INPUT -> "You"
                        MemoryManager.ConversationType.ASSISTANT_RESPONSE -> "Groot"
                        else -> "System"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.message
                        .removePrefix("User: ")
                        .removePrefix("Assistant: ")
                        .removePrefix("System: "),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.System.canWrite(this)) {
                Log.w(TAG, "WRITE_SETTINGS permission not granted")
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
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
        initializeTextToSpeech()
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

    private fun initializeTextToSpeech() {
        tts = TextToSpeech(this, this)
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

    private fun processVoiceCommand(command: String) {
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
    }

    private fun speak(text: String, onComplete: (() -> Unit)? = null) {
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
    }

    private fun restartListening() {
        handler.postDelayed({
            if (!isProcessing && !isListening) {
                Log.d(TAG, "Auto-restarting listening")
            }
        }, 1000)
    }

    override fun onInit(status: Int) {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
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
}