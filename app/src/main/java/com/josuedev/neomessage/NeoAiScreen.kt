package com.josuedev.neomessage

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoAiScreen(
    onBack: () -> Unit,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>(
        "¡Hola! 👋 Soy Neo AI, tu asistente inteligente para tareas, estudios, ciencia e investigaciones.\n\nPuedes preguntarme sobre cualquier tema (ej: 'Fotosíntesis', 'Albert Einstein', 'Historia de México') o pedirme que redacte un texto. ¿En qué te ayudo hoy?" to false
    ) }
    var isTyping by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF00BFFF), Color(0xFF00FF7F))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Neo AI", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            val hasKey = NeoAiEngine.getGeminiKey(context).isNotEmpty()
                            Text(
                                if (hasKey) "✨ Gemini 1.5 Flash Activo" else "📚 Enciclopedia Libre Activa",
                                color = if (hasKey) Color(0xFF00BFFF) else Color(0xFF00FF7F),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Configurar Clave IA", tint = Color(0xFF00BFFF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                reverseLayout = false,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg ->
                    AiMessageBubble(text = msg.first, isUser = msg.second, textColor = textColor)
                }
                if (isTyping) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF00FF7F),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Neo AI está investigando...", color = Color(0xFF00FF7F), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Barra de entrada inferior
            Surface(
                color = surfaceColor,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe una tarea o pregunta...", color = textSubColor, fontSize = 14.sp) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = Color(0xFF00FF7F),
                            unfocusedBorderColor = textSubColor.copy(0.3f)
                        ),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isTyping) {
                                val userPrompt = inputText.trim()
                                messages.add(userPrompt to true)
                                inputText = ""
                                isTyping = true

                                coroutineScope.launch {
                                    val answer = NeoAiEngine.queryAi(context, userPrompt)
                                    messages.add(answer to false)
                                    isTyping = false
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF00FF7F)),
                        enabled = !isTyping && inputText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black)
                    }
                }
            }
        }
    }

    // Diálogo opcional para poner clave gratuita de Gemini
    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf(NeoAiEngine.getGeminiKey(context)) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, tint = Color(0xFF00BFFF))
                    Spacer(Modifier.width(8.dp))
                    Text("Motor Gemini AI (Gratis)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Por defecto Neo AI usa la Enciclopedia Libre sin costo ni registros.\n\nSi deseas potenciarlo como ChatGPT con razonamiento avanzado, puedes ingresar una clave gratuita de Google AI Studio (aistudio.google.com).",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Clave Gemini API (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00BFFF)
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        NeoAiEngine.setGeminiKey(context, keyInput)
                        showApiKeyDialog = false
                        Toast.makeText(context, if (keyInput.isBlank()) "Modo Enciclopedia Libre activado" else "✨ Clave Gemini guardada con éxito", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cerrar", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun AiMessageBubble(text: String, isUser: Boolean, textColor: Color) {
    val bubbleColor = if (isUser) Color(0xFF1E293B) else Color(0xFF0F172A)
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) 
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) 
    else 
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            border = if (!isUser) BorderStroke(1.dp, Color(0xFF8B5CF6).copy(0.4f)) else null
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF00BFFF))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Neo AI", color = Color(0xFF00BFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
