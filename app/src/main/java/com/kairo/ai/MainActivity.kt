package com.kairo.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.launch

private data class ChatMessage(val text: String, val fromKairo: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { KairoApp(onSpeak = ::speak) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            val preferred = tts?.voices?.firstOrNull {
                it.name.contains("female", ignoreCase = true)
            }
            if (preferred != null) tts?.voice = preferred
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kairo-response")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
private fun KairoApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val configStore = remember { ProviderConfigStore(context) }
    val memoryStore = remember { MemoryStore(context) }
    var providerConfig by remember { mutableStateOf(configStore.load()) }
    var localModelReady by remember { mutableStateOf(LocalModelManager.isDownloaded(context)) }
    var localAiEnabled by remember { mutableStateOf(LocalModelManager.isDownloaded(context)) }
    var isDownloadingModel by remember { mutableStateOf(false) }
    var modelDownloadProgress by remember { mutableStateOf(0) }
    var modelDownloadError by remember { mutableStateOf("") }
    val localEngine = remember { LocalAIEngine(context) }
    DisposableEffect(localEngine) {
        onDispose { localEngine.close() }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    var pendingAppLaunch by remember { mutableStateOf<String?>(null) }
    var memoryItems by remember { mutableStateOf(memoryStore.all()) }
    var providerUrl by remember { mutableStateOf(providerConfig?.baseUrl ?: "") }
    var providerModel by remember { mutableStateOf(providerConfig?.model ?: "") }
    var providerKey by remember { mutableStateOf(providerConfig?.apiKey ?: "") }
    var settingsError by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(
            ChatMessage("KAIRO core initialized. I'm ready when you are.", true)
        )
    }
    var input by remember { mutableStateOf("") }
    var listeningError by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) input = spoken
        else listeningError = "I couldn't catch that. Please try again."
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to KAIRO")
            }
            runCatching { speechLauncher.launch(intent) }
                .onFailure { listeningError = "Speech recognition isn't available on this device." }
        } else listeningError = "Microphone permission was not granted."
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF55E6FF),
        secondary = Color(0xFF9B8CFF),
        background = Color(0xFF070B14),
        surface = Color(0xFF111827)
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF101B31), Color(0xFF070B14))))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("K A I R O", color = Color(0xFF70E9FF), fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("SOVEREIGN INTELLIGENCE", color = Color(0xFF8195B5), fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                    TextButton(onClick = { memoryItems = memoryStore.all(); showMemoryManager = true }) {
                        Text("🧠 MEMORY", color = Color(0xFF70E9FF), fontSize = 10.sp)
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text("⚙ SETTINGS", color = Color(0xFF70E9FF), fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(142.dp).background(
                            Brush.radialGradient(listOf(Color(0x5535DFFF), Color(0x222D6D9B), Color.Transparent)),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(88.dp).background(
                                Brush.linearGradient(listOf(Color(0xFF164E78), Color(0xFF10172D))),
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("K", color = Color(0xFF8AF0FF), fontSize = 43.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("YOUR PERSONAL AI", color = Color(0xFF9FB2D0), fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        ChatBubble(message)
                    }
                }

                if (listeningError.isNotBlank()) {
                    Text(listeningError, color = Color(0xFFFFB4AB), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF151F31), RoundedCornerShape(18.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it; listeningError = "" },
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("Message KAIRO…", color = Color(0xFF71819B))
                            inner()
                        }
                    )
                    Button(
                        onClick = {
                            listeningError = ""
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to KAIRO")
                                }
                                runCatching { speechLauncher.launch(intent) }
                                    .onFailure { listeningError = "Speech recognition isn't available on this device." }
                            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243D59))
                    ) { Text("🎙", fontSize = 16.sp) }
                    Button(
                        onClick = {
                            val prompt = input.trim()
                            if (prompt.isNotEmpty()) {
                                messages.add(ChatMessage(prompt, false))
                                input = ""
                                isThinking = true
                                scope.launch {
                                    val openApp = Regex("(?i)^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$").find(prompt)
                                    if (openApp != null) {
                                        pendingAppLaunch = openApp.groupValues[1].trim()
                                        messages.add(ChatMessage("I can open ${pendingAppLaunch}. Please confirm the action.", true))
                                        isThinking = false
                                        return@launch
                                    }
                                    val lowerPrompt = prompt.lowercase()
                                    val manualRemember = Regex("(?i)^(?:kairo[, ]*)?remember(?: that)?\\s+(.+)$").find(prompt)
                                    val forgetAll = lowerPrompt in listOf("forget all memories", "clear all memories", "delete all memories")
                                    val forgetQuery = Regex("(?i)^(?:kairo[, ]*)?forget\\s+(.+)$").find(prompt)
                                    val memoryAnswer = when {
                                        manualRemember != null -> {
                                            val saved = memoryStore.remember(manualRemember.groupValues[1])
                                            memoryItems = memoryStore.all()
                                            if (saved) "I'll remember that. You can ask me to recall it or forget it anytime."
                                            else "I already have that memory, or it was too short to save."
                                        }
                                        forgetAll -> {
                                            memoryStore.clear()
                                            memoryItems = emptyList()
                                            "All saved memories have been cleared from this device."
                                        }
                                        forgetQuery != null -> {
                                            val count = memoryStore.forgetMatching(forgetQuery.groupValues[1])
                                            memoryItems = memoryStore.all()
                                            if (count > 0) "I've removed $count matching memory item(s)."
                                            else "I couldn't find a saved memory matching that."
                                        }
                                        lowerPrompt.contains("what do you remember") || lowerPrompt == "show memories" || lowerPrompt == "list memories" -> {
                                            val saved = memoryStore.all()
                                            memoryItems = saved
                                            if (saved.isEmpty()) "I don't have any saved memories yet."
                                            else "Here's what I remember:\n" + saved.joinToString("\n")
                                        }
                                        else -> null
                                    }
                                    if (memoryAnswer != null) {
                                        messages.add(ChatMessage(memoryAnswer, true))
                                        onSpeak(memoryAnswer)
                                        isThinking = false
                                        return@launch
                                    }
                                    memoryStore.autoLearn(prompt)
                                    memoryItems = memoryStore.all()
                                    val activeEngine: AIEngine = when {
                                        localAiEnabled && localModelReady -> localEngine
                                        providerConfig != null -> OpenAICompatibleEngine(providerConfig!!)
                                        else -> DemoAIEngine()
                                    }
                                    val answer = runCatching { activeEngine.reply(prompt, memoryStore.context()) }
                                        .getOrElse { "KAIRO: " + (it.message ?: "Something went wrong. Please try again.") }
                                    messages.add(ChatMessage(answer, true))
                                    onSpeak(answer)
                                    isThinking = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087E9B))
                    ) { Text(if (isThinking) "..." else "SEND", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        localAiEnabled && localModelReady -> "VOICE INPUT IS USER-INITIATED • LOCAL AI"
                        providerConfig != null -> "VOICE INPUT IS USER-INITIATED • CLOUD AI"
                        else -> "VOICE INPUT IS USER-INITIATED • DEMO MODE"
                    },
                    color = Color(0xFF60728D), fontSize = 9.sp, letterSpacing = 1.sp
                )
            }
        }
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("AI PROVIDER SETTINGS") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("MEMORY", color = Color(0xFF70E9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("KAIRO learns simple preferences automatically and saves anything you explicitly ask it to remember. Memories are encrypted on this device.", fontSize = 11.sp)
                    TextButton(onClick = { memoryItems = memoryStore.all(); showSettings = false; showMemoryManager = true }) { Text("MANAGE MEMORIES", color = Color(0xFF70E9FF)) }
                    Text("CLOUD AI", color = Color(0xFF70E9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Configure an OpenAI-compatible service. Enter its HTTPS base URL, model name and API key.", fontSize = 12.sp)
                    OutlinedTextField(value = providerUrl, onValueChange = { providerUrl = it; settingsError = "" }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(value = providerModel, onValueChange = { providerModel = it; settingsError = "" }, label = { Text("Model name") }, singleLine = true)
                    OutlinedTextField(value = providerKey, onValueChange = { providerKey = it; settingsError = "" }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (settingsError.isNotBlank()) Text(settingsError, color = Color(0xFFFFB4AB), fontSize = 12.sp)
                    Text("The key is encrypted on this device using Android Keystore. Never paste it into GitHub or share it in chat.", fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("ON-DEVICE AI", color = Color(0xFF70E9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("AVAILABLE MODEL", color = Color(0xFF8195B5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("SmolLM2-360M-Instruct", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("360 million parameters • approximately 374 MB • runs on this phone • no API key", color = Color(0xFF9FB2D0), fontSize = 11.sp)
                    Text(
                        when {
                            localModelReady && localAiEnabled -> "SELECTED: SmolLM2-360M-Instruct • ACTIVE"
                            localModelReady -> "DOWNLOADED: SmolLM2-360M-Instruct • NOT ACTIVE"
                            else -> "SELECTED MODEL: SmolLM2-360M-Instruct • NOT DOWNLOADED"
                        },
                        color = if (localModelReady && localAiEnabled) Color(0xFF72E6A5) else Color(0xFFFFD27A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("This is currently the only bundled on-device model option. It is compact, so Telugu-English answers may be less capable than larger models. Keep KAIRO open and use Wi-Fi while downloading.", fontSize = 11.sp)
                    TextButton(
                        enabled = !isDownloadingModel && !localModelReady,
                        onClick = {
                            isDownloadingModel = true
                            modelDownloadProgress = 0
                            modelDownloadError = ""
                            scope.launch {
                                try {
                                    LocalModelManager.download(context) { progress ->
                                        modelDownloadProgress = progress
                                    }
                                    localModelReady = true
                                    localAiEnabled = true
                                } catch (error: Exception) {
                                    modelDownloadError = error.message ?: "Model download failed."
                                } finally {
                                    isDownloadingModel = false
                                }
                            }
                        }
                    ) {
                        Text(
                            when {
                                localModelReady -> "✓ LOCAL MODEL DOWNLOADED"
                                isDownloadingModel -> "DOWNLOADING…"
                                else -> "DOWNLOAD SMALL LOCAL MODEL (374 MB)"
                            },
                            color = Color(0xFF70E9FF)
                        )
                    }
                    if (isDownloadingModel) {
                        Text("Downloading model: $modelDownloadProgress%", color = Color(0xFF9FB2D0), fontSize = 11.sp)
                    }
                    if (modelDownloadError.isNotBlank()) {
                        Text(modelDownloadError, color = Color(0xFFFFB4AB), fontSize = 11.sp)
                    }
                    if (localModelReady) {
                        TextButton(onClick = { localAiEnabled = true; showSettings = false }) {
                            Text("USE LOCAL AI", color = Color(0xFF70E9FF))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val config = AIProviderConfig(providerUrl.trim(), providerModel.trim(), providerKey.trim())
                    if (!config.baseUrl.startsWith("https://")) settingsError = "Base URL must start with https://"
                    else if (config.model.isBlank() || config.apiKey.isBlank()) settingsError = "Please enter both model name and API key."
                    else runCatching { configStore.save(config) }
                        .onSuccess { providerConfig = config; localAiEnabled = false; showSettings = false; settingsError = "" }
                        .onFailure { settingsError = "Could not save securely: " + (it.message ?: "Unknown error") }
                }) { Text("SAVE SECURELY") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        configStore.clear()
                        providerConfig = null
                        localAiEnabled = false
                        providerUrl = ""
                        providerModel = ""
                        providerKey = ""
                        settingsError = ""
                        showSettings = false
                    }) { Text("USE DEMO") }
                    TextButton(onClick = { showSettings = false }) { Text("CANCEL") }
                }
            }
        )
    }


    if (pendingAppLaunch != null) {
        AlertDialog(
            onDismissRequest = { pendingAppLaunch = null },
            title = { Text("CONFIRM APP LAUNCH") },
            text = { Text("Allow KAIRO to open ${pendingAppLaunch}? No app will open until you confirm.") },
            confirmButton = {
                TextButton(onClick = {
                    val requested = pendingAppLaunch
                    pendingAppLaunch = null
                    if (requested != null) {
                        val result = AppLauncher.open(context, requested)
                        messages.add(ChatMessage(result, true))
                        onSpeak(result)
                    }
                }) { Text("OPEN APP") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingAppLaunch = null
                    messages.add(ChatMessage("Cancelled. I did not open the app.", true))
                }) { Text("CANCEL") }
            }
        )
    }

    if (showMemoryManager) {
        AlertDialog(
            onDismissRequest = { showMemoryManager = false },
            title = { Text("KAIRO MEMORY") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (memoryItems.isEmpty()) {
                        Text("No saved memories yet.")
                    } else {
                        memoryItems.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                TextButton(onClick = {
                                    memoryStore.forgetMatching(item)
                                    memoryItems = memoryStore.all()
                                }) { Text("DELETE") }
                            }
                        }
                    }
                    Text("Try: Remember that … / What do you remember? / Forget … / Forget all memories.", fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemoryManager = false }) { Text("DONE") }
            },
            dismissButton = {
                TextButton(onClick = {
                    memoryStore.clear()
                    memoryItems = emptyList()
                }) { Text("CLEAR ALL") }
            }
        )
    }

}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromKairo) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(
                    if (message.fromKairo) Color(0xFF14263A) else Color(0xFF123D55),
                    RoundedCornerShape(16.dp)
                )
                .padding(13.dp)
        ) {
            Text(
                if (message.fromKairo) "KAIRO" else "YOU",
                color = if (message.fromKairo) Color(0xFF6FE5FF) else Color(0xFF9AD8FF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(message.text, color = Color(0xFFEAF2FF), fontSize = 14.sp)
        }
    }
}
