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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val engine = remember { DemoAIEngine() }
    val scope = rememberCoroutineScope()
    var isThinking by remember { mutableStateOf(false) }
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
                    Text("● ONLINE", color = Color(0xFF6BE7B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    val answer = runCatching { engine.reply(prompt) }
                                        .getOrElse { "Something went wrong. Please try again." }
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
                Text("VOICE INPUT IS USER-INITIATED • DEMO AI MODE", color = Color(0xFF60728D), fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
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
