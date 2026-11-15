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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
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
        val containsTelugu = text.any { it in '\u0C00'..'\u0C7F' }
        val locale = if (containsTelugu) Locale("te", "IN") else Locale.getDefault()
        val availability = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = locale
        } else if (containsTelugu) {
            tts?.language = Locale.getDefault()
        }
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
    val conversationStore = remember { ConversationStore(context) }
    val researchClient = remember { WebResearchClient(context) }
    var researchApiKey by remember { mutableStateOf(researchClient.loadApiKey()) }
    var screenReadStatus by remember { mutableStateOf("") }
    var providerConfigs by remember { mutableStateOf(configStore.loadAll()) }
    var providerConfig by remember { mutableStateOf(providerConfigs.firstOrNull()) }
    var localModelReady by remember { mutableStateOf(LocalModelManager.isDownloaded(context)) }
    val aiModeStore = remember { AIExecutionModeStore(context) }
    var localAiEnabled by remember {
        mutableStateOf(
            aiModeStore.load(
                defaultMode = if (LocalModelManager.isDownloaded(context)) AIExecutionMode.LOCAL
                    else if (providerConfigs.isNotEmpty()) AIExecutionMode.CLOUD else AIExecutionMode.LOCAL
            ) == AIExecutionMode.LOCAL
        )
    }
    var isDownloadingModel by remember { mutableStateOf(false) }
    var modelDownloadProgress by remember { mutableStateOf(0) }
    var modelDownloadError by remember { mutableStateOf("") }
    val localEngine = remember { LocalAIEngine(context) }
    DisposableEffect(localEngine) {
        onDispose { localEngine.close() }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var pendingAppLaunch by remember { mutableStateOf<String?>(null) }
    var memoryItems by remember { mutableStateOf(memoryStore.all()) }
    var providerUrl by remember { mutableStateOf(providerConfig?.baseUrl ?: "") }
    var providerModel by remember { mutableStateOf(providerConfig?.model ?: "") }
    var providerKey by remember { mutableStateOf(providerConfig?.apiKey ?: "") }
    var providerTestResult by remember { mutableStateOf("") }
    var isTestingProvider by remember { mutableStateOf(false) }
    var provider2Url by remember { mutableStateOf(providerConfigs.getOrNull(1)?.baseUrl ?: "") }
    var provider2Model by remember { mutableStateOf(providerConfigs.getOrNull(1)?.model ?: "") }
    var provider2Key by remember { mutableStateOf(providerConfigs.getOrNull(1)?.apiKey ?: "") }
    var provider3Url by remember { mutableStateOf(providerConfigs.getOrNull(2)?.baseUrl ?: "") }
    var provider3Model by remember { mutableStateOf(providerConfigs.getOrNull(2)?.model ?: "") }
    var provider3Key by remember { mutableStateOf(providerConfigs.getOrNull(2)?.apiKey ?: "") }
    var provider4Url by remember { mutableStateOf(providerConfigs.getOrNull(3)?.baseUrl ?: "") }
    var provider4Model by remember { mutableStateOf(providerConfigs.getOrNull(3)?.model ?: "") }
    var provider4Key by remember { mutableStateOf(providerConfigs.getOrNull(3)?.apiKey ?: "") }
    var settingsError by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(conversationStore.load().map { ChatMessage(it.text, it.fromKairo) })
            if (isEmpty()) add(ChatMessage("KAIRO core initialized. I'm ready when you are.", true))
        }
    }
    LaunchedEffect(messages.size) {
        conversationStore.save(messages.map { ConversationEntry(it.text, it.fromKairo) })
    }
    var input by remember { mutableStateOf("") }
    var listeningError by remember { mutableStateOf("") }

    val screenImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            screenReadStatus = "Reading selected screenshot…"
            try {
                val extracted = ScreenTextReader.extract(context, uri)
                if (extracted.isBlank()) screenReadStatus = "No readable text found in that image."
                else {
                    input = "Please understand this screen content and help me with it:\n" + extracted.take(6000)
                    screenReadStatus = "Text extracted. Review the message before sending."
                }
            } catch (error: Exception) { screenReadStatus = "Could not read image: " + (error.message ?: "unknown error") }
        }
    }

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
                    TextButton(onClick = { showClearChatDialog = true }) {
                        Text("CLEAR CHAT", color = Color(0xFF9FB2D0), fontSize = 10.sp)
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

                if (screenReadStatus.isNotBlank()) Text(screenReadStatus, color = Color(0xFF9FB2D0), fontSize = 11.sp)
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
                    Button(onClick = { input = if (input.isBlank()) "Help me define requirements for: " else "Create a requirements brief for: " + input }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243D59))) { Text("REQ", fontSize = 9.sp) }
                    Button(onClick = { screenImageLauncher.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243D59))) { Text("SCREEN", fontSize = 9.sp) }
                    Button(
                        enabled = !isThinking,
                        onClick = {
                            val prompt = input.trim()
                            if (prompt.isNotEmpty() && !isThinking) {
                                messages.add(ChatMessage(prompt, false))
                                input = ""
                                isThinking = true
                                scope.launch {
                                    try {
                                        val skillRequest = KairoSkillRegistry.detect(prompt)
                                    val directToolAnswer = KairoToolExecutor.execute(skillRequest)
                                    if (directToolAnswer != null) {
                                        messages.add(ChatMessage(directToolAnswer, true))
                                        onSpeak(directToolAnswer)
                                        isThinking = false
                                        return@launch
                                    }
                                    if (skillRequest.type == KairoSkillType.OPEN_APP) {
                                        pendingAppLaunch = skillRequest.argument
                                        messages.add(ChatMessage("I can open ${pendingAppLaunch}. Please confirm the action.", true))
                                        isThinking = false
                                        return@launch
                                    }
                                    val memoryAnswer = when (skillRequest.type) {
                                        KairoSkillType.REMEMBER -> {
                                            val saved = memoryStore.remember(skillRequest.argument)
                                            memoryItems = memoryStore.all()
                                            if (saved) "I'll remember that. You can ask me to recall it or forget it anytime."
                                            else "I already have that memory, or it was too short to save."
                                        }
                                        KairoSkillType.FORGET -> {
                                            if (skillRequest.argument == "*") {
                                                memoryStore.clear()
                                                memoryItems = emptyList()
                                                "All saved memories have been cleared from this device."
                                            } else {
                                                val count = memoryStore.forgetMatching(skillRequest.argument)
                                                memoryItems = memoryStore.all()
                                                if (count > 0) "I've removed $count matching memory item(s)."
                                                else "I couldn't find a saved memory matching that."
                                            }
                                        }
                                        KairoSkillType.RECALL -> {
                                            val saved = memoryStore.all()
                                            memoryItems = saved
                                            if (saved.isEmpty()) "I don't have any saved memories yet."
                                            else "Here's what I remember:\n" + saved.joinToString("\n")
                                        }
                                        else -> memoryStore.answerPersonalFact(prompt)
                                    }
                                    if (memoryAnswer != null) {
                                        messages.add(ChatMessage(memoryAnswer, true))
                                        onSpeak(memoryAnswer)
                                        isThinking = false
                                        return@launch
                                    }
                                    memoryStore.autoLearn(prompt)
                                    memoryItems = memoryStore.all()
                                    val taskProfile = KairoTaskPlanner.classify(prompt)
                                    val aiPrompt = if (taskProfile.type == TaskType.REQUIREMENTS) KairoRequirementsAgent.enhance(prompt) else prompt
                                    val liveResults = if (KairoTaskPlanner.shouldFetchLiveData(taskProfile, localAiEnabled, researchApiKey.isNotBlank())) {
                                        try { researchClient.search(prompt) } catch (error: Exception) {
                                            messages.add(ChatMessage("Live research failed: " + (error.message ?: "search error"), true))
                                            isThinking = false
                                            return@launch
                                        }
                                    } else emptyList()
                                    val researchContext = if (liveResults.isEmpty()) "" else buildString {
                                        append("LIVE WEB RESEARCH RESULTS (retrieved now; use and cite these source URLs):\n")
                                        liveResults.forEachIndexed { index, item ->
                                            append((index + 1).toString() + ". " + item.title + "\n" + item.content + "\nSource: " + item.url + "\n\n")
                                        }
                                    }
                                    val kairoBrain = KairoBrain(
                                        localEngine = localEngine,
                                        providers = providerConfigs,
                                        preferLocal = localAiEnabled,
                                        localReady = localModelReady
                                    )
                                    val savedMemory = listOf(memoryStore.context(prompt), researchContext).filter { it.isNotBlank() }.joinToString("\n\n")
                                    val history = messages.dropLast(1).takeLast(12).map {
                                        ChatTurn(if (it.fromKairo) "assistant" else "user", it.text)
                                    }
                                    val recentConversation = messages.dropLast(1).takeLast(8).joinToString("\n") { entry ->
                                        (if (entry.fromKairo) "KAIRO: " else "User: ") + entry.text.take(1200)
                                    }
                                    val localContext = buildString {
                                        if (savedMemory.isNotBlank()) append(savedMemory)
                                        if (recentConversation.isNotBlank()) {
                                            if (isNotEmpty()) append("\n\n")
                                            append("Recent conversation context (most recent last):\n")
                                            append(recentConversation)
                                        }
                                    }
                                    messages.add(ChatMessage("", true))
                                    val responseSlot = messages.lastIndex
                                    val responseBuffer = StringBuilder()
                                    try {
                                        kairoBrain.replyStream(aiPrompt, history, savedMemory, localContext, liveResults.isNotEmpty()).collect { chunk ->
                                            responseBuffer.append(chunk)
                                            messages[responseSlot] = ChatMessage(responseBuffer.toString(), true)
                                        }
                                        if (responseBuffer.isBlank()) {
                                            messages[responseSlot] = ChatMessage("The AI provider returned an empty response.", true)
                                        } else if (liveResults.isNotEmpty()) {
                                            messages[responseSlot] = ChatMessage(
                                                ResearchPresentation.appendSources(responseBuffer.toString(), liveResults),
                                                true
                                            )
                                        }
                                    } catch (cancel: CancellationException) {
                                        throw cancel
                                    } catch (error: Exception) {
                                        val note = "KAIRO: " + (error.message ?: "Something went wrong. Please try again.")
                                        messages[responseSlot] = ChatMessage(
                                            if (responseBuffer.isBlank()) note else responseBuffer.toString() + "\n\n[" + note + "]",
                                            true
                                        )
                                    } finally {
                                        // Streaming edits the existing response row, so messages.size
                                        // does not change and the size-keyed autosave won't run.
                                        // Persist the completed (or interrupted) response explicitly.
                                        conversationStore.save(messages.map {
                                            ConversationEntry(it.text, it.fromKairo)
                                        })
                                        isThinking = false
                                    }
                                    onSpeak(messages[responseSlot].text)
                                    } catch (cancel: CancellationException) {
                                        throw cancel
                                    } catch (error: Exception) {
                                        messages.add(ChatMessage("KAIRO: " + (error.message ?: "Something went wrong. Please try again."), true))
                                        conversationStore.save(messages.map { ConversationEntry(it.text, it.fromKairo) })
                                    } finally {
                                        isThinking = false
                                    }
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
                        providerConfigs.isNotEmpty() -> "VOICE INPUT IS USER-INITIATED • MULTI-PROVIDER CLOUD AI"
                        else -> "VOICE INPUT IS USER-INITIATED • AI NOT CONFIGURED"
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
                    Text("KAIRO saves explicit memories and a limited recent chat history. Both are encrypted on this device. Recent chat context may be sent to whichever cloud provider answers your request.", fontSize = 11.sp)
                    TextButton(onClick = { showClearChatDialog = true; showSettings = false }) { Text("CLEAR CONVERSATION HISTORY", color = Color(0xFFFFB4AB)) }
                    TextButton(onClick = { memoryItems = memoryStore.all(); showSettings = false; showMemoryManager = true }) { Text("MANAGE MEMORIES", color = Color(0xFF70E9FF)) }
                    Text("LIVE WEB RESEARCH", color = Color(0xFF70E9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Optional Tavily search key. KAIRO uses it for current-information questions; search results are sent to your selected AI provider for synthesis.", fontSize = 11.sp)
                    OutlinedTextField(value = researchApiKey, onValueChange = { researchApiKey = it }, label = { Text("Tavily API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Text("CLOUD AI", color = Color(0xFF70E9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("KAIRO rotates across configured providers and temporarily skips rate-limited or unhealthy endpoints. Add an OmniRoute instance you control as an OpenAI-compatible gateway; the gateway must already be hosted and connected to provider accounts. Routing does not create free quota or bypass provider limits.", fontSize = 12.sp)
                    Text("PRIMARY • DEEPSEEK", color = Color(0xFF9FB2D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = providerUrl, onValueChange = { providerUrl = it; settingsError = "" }, label = { Text("Base URL") }, placeholder = { Text("https://api.deepseek.com") }, singleLine = true)
                    OutlinedTextField(value = providerModel, onValueChange = { providerModel = it; settingsError = "" }, label = { Text("Model name") }, placeholder = { Text("deepseek-flash") }, singleLine = true)
                    OutlinedTextField(value = providerKey, onValueChange = { providerKey = it; settingsError = "" }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    TextButton(
                        enabled = !isTestingProvider,
                        onClick = {
                            if (!providerUrl.trim().startsWith("https://") || providerModel.isBlank() || providerKey.isBlank()) {
                                providerTestResult = "Add the DeepSeek HTTPS base URL, model ID, and API key first."
                            } else {
                                isTestingProvider = true
                                providerTestResult = "Connecting directly to DeepSeek…"
                                scope.launch {
                                    try {
                                        val testConfig = AIProviderConfig(providerUrl.trim(), providerModel.trim(), providerKey.trim())
                                                                                val answer = StringBuilder()
                                        StreamingProviderEngine(testConfig).stream(
                                            listOf(ChatTurn("user", "Reply with exactly: KAIRO connected to DeepSeek.")),
                                            ""
                                        ).collect { chunk -> answer.append(chunk) }
                                        providerTestResult = "SUCCESS - DeepSeek streaming is working: ${answer.toString().take(180)}"
                                    } catch (error: Exception) {
                                        providerTestResult = "FAILED — ${error.message ?: "Unknown connection error"}"
                                    } finally {
                                        isTestingProvider = false
                                    }
                                }
                            }
                        }
                    ) { Text(if (isTestingProvider) "TESTING DEEPSEEK…" else "TEST DEEPSEEK CONNECTION", color = Color(0xFF70E9FF)) }
                    if (providerTestResult.isNotBlank()) Text(providerTestResult, color = if (providerTestResult.startsWith("SUCCESS")) Color(0xFF72E6A5) else Color(0xFFFFD27A), fontSize = 11.sp)
                    Text("BACKUP 1 • GROK / xAI (OPTIONAL)", color = Color(0xFF9FB2D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = provider2Url, onValueChange = { provider2Url = it; settingsError = "" }, label = { Text("Base URL") }, placeholder = { Text("https://api.x.ai/v1") }, singleLine = true)
                    OutlinedTextField(value = provider2Model, onValueChange = { provider2Model = it; settingsError = "" }, label = { Text("Model name") }, singleLine = true)
                    OutlinedTextField(value = provider2Key, onValueChange = { provider2Key = it; settingsError = "" }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Text("BACKUP 2 • CLAUDE / ANTHROPIC (OPTIONAL)", color = Color(0xFF9FB2D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = provider3Url, onValueChange = { provider3Url = it; settingsError = "" }, label = { Text("Base URL") }, placeholder = { Text("https://api.anthropic.com") }, singleLine = true)
                    OutlinedTextField(value = provider3Model, onValueChange = { provider3Model = it; settingsError = "" }, label = { Text("Model name") }, singleLine = true)
                    OutlinedTextField(value = provider3Key, onValueChange = { provider3Key = it; settingsError = "" }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Text("BACKUP 3 • OMNIROUTE / CUSTOM GATEWAY (OPTIONAL)", color = Color(0xFF9FB2D0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Use the HTTPS URL, model ID and gateway token from your own OmniRoute deployment. The URL should be its OpenAI-compatible API base (usually ending in /v1). KAIRO connects to it; it does not host the gateway.", fontSize = 11.sp)
                    OutlinedTextField(value = provider4Url, onValueChange = { provider4Url = it; settingsError = "" }, label = { Text("Gateway base URL") }, placeholder = { Text("https://your-server.example/v1") }, singleLine = true)
                    OutlinedTextField(value = provider4Model, onValueChange = { provider4Model = it; settingsError = "" }, label = { Text("Gateway model ID") }, placeholder = { Text("provider/model") }, singleLine = true)
                    OutlinedTextField(value = provider4Key, onValueChange = { provider4Key = it; settingsError = "" }, label = { Text("Gateway API token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (settingsError.isNotBlank()) Text(settingsError, color = Color(0xFFFFB4AB), fontSize = 12.sp)
                    Text("The key is encrypted on this device using Android Keystore. Never paste it into GitHub or share it in chat.", fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("ON-DEVICE AI", color = Color(0xFF70E9FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("AVAILABLE MODEL", color = Color(0xFF8195B5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Qwen3-0.6B-INT4-No-Think", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("600 million parameters • approximately 347 MB • INT4 quantized • no API key", color = Color(0xFF9FB2D0), fontSize = 11.sp)
                    Text(
                        when {
                            localModelReady && localAiEnabled -> "SELECTED: Qwen3-0.6B-INT4-No-Think • ACTIVE"
                            localModelReady -> "DOWNLOADED: Qwen3-0.6B-INT4-No-Think • NOT ACTIVE"
                            else -> "SELECTED MODEL: Qwen3-0.6B-INT4-No-Think • NOT DOWNLOADED"
                        },
                        color = if (localModelReady && localAiEnabled) Color(0xFF72E6A5) else Color(0xFFFFD27A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Fast, direct-answer local model. No-think mode skips lengthy reasoning for quicker replies. Actual speed depends on device load. Keep KAIRO open and use Wi-Fi while downloading.", fontSize = 11.sp)
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
                                    aiModeStore.save(AIExecutionMode.LOCAL)
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
                                else -> "DOWNLOAD FAST LOCAL MODEL (347 MB)"
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
                        TextButton(onClick = {
                            localAiEnabled = true
                            aiModeStore.save(AIExecutionMode.LOCAL)
                            showSettings = false
                        }) {
                            Text("USE LOCAL AI", color = Color(0xFF70E9FF))
                        }
                    }
                    if (providerConfigs.isNotEmpty()) {
                        TextButton(onClick = {
                            localAiEnabled = false
                            aiModeStore.save(AIExecutionMode.CLOUD)
                            showSettings = false
                        }) {
                            Text("USE CLOUD AI", color = Color(0xFF70E9FF))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val entered = listOf(
                        Triple(providerUrl, providerModel, providerKey),
                        Triple(provider2Url, provider2Model, provider2Key),
                        Triple(provider3Url, provider3Model, provider3Key),
                        Triple(provider4Url, provider4Model, provider4Key)
                    )
                    val incomplete = entered.any { (url, model, key) ->
                        (url.isNotBlank() || model.isNotBlank() || key.isNotBlank()) &&
                            (url.isBlank() || model.isBlank() || key.isBlank())
                    }
                    val configs = entered.filter { it.first.isNotBlank() && it.second.isNotBlank() && it.third.isNotBlank() }
                        .map { entry ->
                            val url = entry.first.trim()
                            val protocol = if (url.contains("api.anthropic.com", ignoreCase = true)) "anthropic" else "openai"
                            AIProviderConfig(url, entry.second.trim(), entry.third.trim(), protocol)
                        }
                    if (incomplete) settingsError = "Complete all three fields for each provider you use."
                    else if (configs.any { !it.baseUrl.startsWith("https://") }) settingsError = "Every provider URL must start with https://"
                    else if (configs.isEmpty()) settingsError = "Enter at least one provider."
                    else runCatching { configStore.saveAll(configs) }
                        .onSuccess {
                            researchClient.saveApiKey(researchApiKey)
                            providerConfigs = configs
                            providerConfig = configs.firstOrNull()
                            localAiEnabled = false
                            aiModeStore.save(AIExecutionMode.CLOUD)
                            showSettings = false
                            settingsError = ""
                        }
                        .onFailure { settingsError = "Could not save securely: " + (it.message ?: "Unknown error") }
                }) { Text("SAVE SECURELY") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        configStore.clear()
                        providerConfig = null
                        providerConfigs = emptyList()
                        localAiEnabled = localModelReady
                        aiModeStore.save(if (localModelReady) AIExecutionMode.LOCAL else AIExecutionMode.CLOUD)
                        providerUrl = ""
                        providerModel = ""
                        providerKey = ""
                        provider2Url = ""
                        provider2Model = ""
                        provider2Key = ""
                        provider3Url = ""
                        provider3Model = ""
                        provider3Key = ""
                        provider4Url = ""
                        provider4Model = ""
                        provider4Key = ""
                        settingsError = ""
                        showSettings = false
                    }) { Text("DISCONNECT CLOUD AI") }
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
                        val result = if (KairoActionPolicy.mayExecute(KairoAction.OPEN_INSTALLED_APP, true)) AppLauncher.open(context, requested) else "KAIRO blocked an unapproved action."
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

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("CLEAR CONVERSATION?") },
            text = { Text("This deletes KAIRO's locally saved chat history on this device. Saved memories are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    messages.clear()
                    messages.add(ChatMessage("KAIRO core initialized. I'm ready when you are.", true))
                    conversationStore.clear()
                    scope.launch { runCatching { localEngine.resetConversation() } }
                    showClearChatDialog = false
                }) { Text("CLEAR HISTORY") }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("CANCEL") }
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
            val uriHandler = LocalUriHandler.current
            val linkedText = remember(message.text) {
                buildAnnotatedString {
                    val pattern = Regex("https://[^\\s)\\]}>]+")
                    var cursor = 0
                    pattern.findAll(message.text).forEach { match ->
                        append(message.text.substring(cursor, match.range.first))
                        val url = match.value.trimEnd('.', ',', ';')
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(color = Color(0xFF70E9FF), textDecoration = TextDecoration.Underline)) {
                            append(match.value)
                        }
                        pop()
                        cursor = match.range.last + 1
                    }
                    append(message.text.substring(cursor))
                }
            }
            ClickableText(
                text = linkedText,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFEAF2FF), fontSize = 14.sp),
                onClick = { offset ->
                    linkedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        runCatching { uriHandler.openUri(it.item) }
                    }
                }
            )
        }
    }
}
