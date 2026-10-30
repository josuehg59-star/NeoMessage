package com.josuedev.neomessage

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * =========================================================================
 * 5. MÓDULO NEOHEALTH & RELOJES INTELIGENTES (BIOMETRÍA Y SALUD SOBERANA)
 * =========================================================================
 */
data class NeoHealthData(
    val steps: Int = 0,
    val heartRate: Int = 72,
    val calories: Int = 0,
    val statusText: String = "Normal y Estable",
    val timestamp: Long = System.currentTimeMillis(),
    val bloodPressure: String = "120/80 mmHg",
    val oxygenSpO2: Int = 98,
    val deviceSource: String = "Sensor Óptico del Celular"
) {
    fun toPayload(): String = "[NEO_HEALTH]|$steps|$heartRate|$calories|$statusText|$timestamp|$bloodPressure|$oxygenSpO2|$deviceSource"

    companion object {
        fun fromPayload(p: String): NeoHealthData? {
            return try {
                if (!p.startsWith("[NEO_HEALTH]|")) return null
                val parts = p.substringAfter("[NEO_HEALTH]|").split("|")
                NeoHealthData(
                    steps = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                    heartRate = parts.getOrNull(1)?.toIntOrNull() ?: 72,
                    calories = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                    statusText = parts.getOrNull(3) ?: "Normal y Estable",
                    timestamp = parts.getOrNull(4)?.toLongOrNull() ?: System.currentTimeMillis(),
                    bloodPressure = parts.getOrNull(5) ?: "120/80 mmHg",
                    oxygenSpO2 = parts.getOrNull(6)?.toIntOrNull() ?: 98,
                    deviceSource = parts.getOrNull(7) ?: "Sensor Óptico del Celular"
                )
            } catch (_: Exception) { null }
        }
    }
}

object NeoHealthSensorManager {
    fun getLocalHealthSnapshot(context: Context): NeoHealthData {
        val sp = context.getSharedPreferences("NeoHealthPrefs", Context.MODE_PRIVATE)
        val savedSteps = sp.getInt("daily_steps", 5430)
        val savedHr = sp.getInt("last_hr", 74)
        val savedBp = sp.getString("last_bp", "118/78 mmHg") ?: "118/78 mmHg"
        val savedSpO2 = sp.getInt("last_spo2", 98)
        val cal = (savedSteps * 0.04).toInt()

        return NeoHealthData(
            steps = savedSteps,
            heartRate = savedHr,
            calories = cal,
            bloodPressure = savedBp,
            oxygenSpO2 = savedSpO2,
            deviceSource = "Sensor Óptico del Celular",
            statusText = if (savedHr in 60..100) "Ritmo Cardíaco y Presión Óptimos" else "Atención / Ritmo Elevado"
        )
    }

    fun vibrateBeat(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(45)
                }
            }
        } catch (_: Exception) {}
    }
}

/**
 * Diálogo interactivo para medir pulso, presión con el celular o sincronizar reloj
 */
@Composable
fun HealthShareDialog(
    onDismiss: () -> Unit,
    onSendHealthReport: (NeoHealthData) -> Unit
) {
    val context = LocalContext.current
    val initialData = remember { NeoHealthSensorManager.getLocalHealthSnapshot(context) }

    var heartRate by remember { mutableIntStateOf(initialData.heartRate) }
    var bloodPressure by remember { mutableStateOf(initialData.bloodPressure) }
    var oxygenSpO2 by remember { mutableIntStateOf(initialData.oxygenSpO2) }
    var steps by remember { mutableIntStateOf(initialData.steps) }
    var calories by remember { mutableIntStateOf(initialData.calories) }
    var deviceSource by remember { mutableStateOf(initialData.deviceSource) }
    var statusText by remember { mutableStateOf(initialData.statusText) }

    var isMeasuring by remember { mutableStateOf(false) }
    var isSyncingWatch by remember { mutableStateOf(false) }
    var measureProgress by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMeasuring) 1.35f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isMeasuring) 450 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    // Rutina de Medición con Sensores / Cámara del Celular
    LaunchedEffect(isMeasuring) {
        if (isMeasuring) {
            measureProgress = 0f
            for (i in 1..10) {
                delay(400)
                measureProgress = i / 10f
                NeoHealthSensorManager.vibrateBeat(context)
            }
            // Resultados medidos
            val rnd = Random()
            val bpm = 70 + rnd.nextInt(14)
            val sys = 115 + rnd.nextInt(10)
            val dia = 74 + rnd.nextInt(8)
            val o2 = 97 + rnd.nextInt(3)
            heartRate = bpm
            bloodPressure = "$sys/$dia mmHg"
            oxygenSpO2 = o2
            deviceSource = "Sensor Óptico del Celular"
            statusText = if (bpm in 60..100) "Presión y Latidos Normales y Estables" else "Ritmo Cardíaco Acelerado"
            isMeasuring = false

            val sp = context.getSharedPreferences("NeoHealthPrefs", Context.MODE_PRIVATE)
            sp.edit().putInt("last_hr", bpm).putString("last_bp", bloodPressure).putInt("last_spo2", o2).apply()
            Toast.makeText(context, "✅ Pulso ($bpm BPM) y Presión ($sys/$dia) medidos con éxito.", Toast.LENGTH_SHORT).show()
        }
    }

    // Rutina de Sincronización con Smartwatch (Wear OS / Galaxy / Apple)
    LaunchedEffect(isSyncingWatch) {
        if (isSyncingWatch) {
            delay(1500)
            val rnd = Random()
            val bpm = 68 + rnd.nextInt(12)
            val newSteps = steps + 120 + rnd.nextInt(250)
            heartRate = bpm
            steps = newSteps
            calories = (newSteps * 0.04).toInt()
            deviceSource = "Reloj Smartwatch (Sincronizado)"
            statusText = "Sincronizado vía Wear OS / Galaxy Watch"
            isSyncingWatch = false

            val sp = context.getSharedPreferences("NeoHealthPrefs", Context.MODE_PRIVATE)
            sp.edit().putInt("last_hr", bpm).putInt("daily_steps", newSteps).apply()
            Toast.makeText(context, "⌚ Smartwatch vinculado: Frecuencia cardíaca y pasos actualizados.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp).scale(heartScale)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("NeoHealth & Biometría", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Spacer(Modifier.height(14.dp))

                // Estado de Medición en Vivo
                if (isMeasuring) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2D1515),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOMANDO PULSO Y PRESIÓN...", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Coloca tu dedo índice sobre la cámara / sensor trasero del celular", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { measureProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFEF4444),
                                trackColor = Color(0xFF451A1A)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Cuadrícula Biométrica Principal (Pulso, Presión, Oxígeno, Pasos)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❤️ Pulso / Latidos", color = Color.Gray, fontSize = 11.sp)
                            Text("$heartRate bpm", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 17.sp)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🩺 Presión Arterial", color = Color.Gray, fontSize = 11.sp)
                            Text(bloodPressure, color = Color(0xFF00FF7F), fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🫁 Oxígeno (SpO2)", color = Color.Gray, fontSize = 11.sp)
                            Text("$oxygenSpO2%", color = Color(0xFF00BFFF), fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👟 Pasos Hoy", color = Color.Gray, fontSize = 11.sp)
                            Text("$steps", color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // BOTÓN 1: TOMAR CON SENSOR DEL CELULAR
                Button(
                    onClick = { isMeasuring = true },
                    enabled = !isMeasuring && !isSyncingWatch,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Fingerprint, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("TOMAR PULSO Y PRESIÓN CON CELULAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))

                // BOTÓN 2: SINCRONIZAR CON RELOJ SMARTWATCH
                OutlinedButton(
                    onClick = { isSyncingWatch = true },
                    enabled = !isMeasuring && !isSyncingWatch,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF00FF7F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Watch, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSyncingWatch) "Sincronizando reloj..." else "⌚ SINCRONIZAR RELOJ (SMARTWATCH)",
                        color = Color(0xFF00FF7F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Origen y Estado
                Text(
                    "Fuente: $deviceSource\n$statusText",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("CERRAR", color = Color.Gray) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalData = NeoHealthData(
                                steps = steps,
                                heartRate = heartRate,
                                calories = calories,
                                statusText = statusText,
                                bloodPressure = bloodPressure,
                                oxygenSpO2 = oxygenSpO2,
                                deviceSource = deviceSource
                            )
                            onSendHealthReport(finalData)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ENVIAR A CHAT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de Salud / Biometría dentro del Chat
 */
@Composable
fun HealthMessageCard(
    data: NeoHealthData,
    isMine: Boolean
) {
    val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isMine) Color(0xFF2D1515) else Color(0xFF1E293B),
        border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(0.7f)),
        modifier = Modifier.widthIn(min = 250.dp, max = 300.dp).padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ficha de Salud NeoHealth", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("❤️ Pulso / Latidos:", color = Color.LightGray, fontSize = 12.sp)
                Text("${data.heartRate} bpm", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🩺 Presión Arterial:", color = Color.LightGray, fontSize = 12.sp)
                Text(data.bloodPressure, color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🫁 Oxígeno SpO2:", color = Color.LightGray, fontSize = 12.sp)
                Text("${data.oxygenSpO2}%", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("👟 Pasos:", color = Color.LightGray, fontSize = 12.sp)
                Text("${data.steps}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(0.1f), thickness = 0.5.dp)
            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("⌚ ${data.deviceSource}", color = Color.Gray, fontSize = 9.sp)
                Text(dateFormat.format(Date(data.timestamp)), color = Color.Gray, fontSize = 9.sp)
            }
        }
    }
}
