package com.josuedev.neomessage

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * =========================================================================
 * 1. MODELOS DE DATOS: COTIZACIONES & MEDIDAS TÉCNICAS
 * =========================================================================
 */
data class NeoQuote(
    val id: String = "COT-" + (1000 + Random().nextInt(9000)),
    val folio: String = "FOLIO #" + System.currentTimeMillis().toString().takeLast(6),
    val oficio: String = "General", // Carpintería, Mecánica, Albañilería, Herrería, etc.
    val concept: String = "",
    val materialsCost: Double = 0.0,
    val laborCost: Double = 0.0,
    val discount: Double = 0.0,
    val advancePayment: Double = 0.0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDIENTE" // PENDIENTE, ACEPTADA, RECHAZADA
) {
    val subtotal: Double get() = (materialsCost + laborCost)
    val total: Double get() = (subtotal - discount).coerceAtLeast(0.0)
    val balanceRemaining: Double get() = (total - advancePayment).coerceAtLeast(0.0)

    fun toPayloadString(): String {
        return "[NEO_QUOTE]|${id}|${folio}|${oficio}|${concept.replace("|", "/")}|${materialsCost}|${laborCost}|${discount}|${advancePayment}|${notes.replace("|", "/")}|${timestamp}|${status}"
    }

    companion object {
        fun fromPayload(payload: String): NeoQuote? {
            return try {
                if (!payload.startsWith("[NEO_QUOTE]|")) return null
                val p = payload.substringAfter("[NEO_QUOTE]|").split("|")
                NeoQuote(
                    id = p.getOrElse(0) { "" },
                    folio = p.getOrElse(1) { "" },
                    oficio = p.getOrElse(2) { "General" },
                    concept = p.getOrElse(3) { "" },
                    materialsCost = p.getOrElse(4) { "0" }.toDoubleOrNull() ?: 0.0,
                    laborCost = p.getOrElse(5) { "0" }.toDoubleOrNull() ?: 0.0,
                    discount = p.getOrElse(6) { "0" }.toDoubleOrNull() ?: 0.0,
                    advancePayment = p.getOrElse(7) { "0" }.toDoubleOrNull() ?: 0.0,
                    notes = p.getOrElse(8) { "" },
                    timestamp = p.getOrElse(9) { "0" }.toLongOrNull() ?: System.currentTimeMillis(),
                    status = p.getOrElse(10) { "PENDIENTE" }
                )
            } catch (_: Exception) { null }
        }
    }
}

/**
 * =========================================================================
 * 2. DIÁLOGO CREAR COTIZACIÓN / PRESUPUESTO PROFESIONAL EN 1 TOQUE
 * =========================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQuoteDialog(
    onDismiss: () -> Unit,
    onSendQuote: (NeoQuote) -> Unit
) {
    var oficio by remember { mutableStateOf("Mecánica") }
    var concept by remember { mutableStateOf("") }
    var materialsStr by remember { mutableStateOf("") }
    var laborStr by remember { mutableStateOf("") }
    var advanceStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val conceptFocus = remember { FocusRequester() }
    val materialsFocus = remember { FocusRequester() }
    val laborFocus = remember { FocusRequester() }
    val advanceFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }

    val oficiosList = listOf("Mecánica", "Carpintería", "Herrería", "Albañilería / Obra", "Electricidad", "Plomería", "Abarrotes / Venta", "Otro Oficio")

    val mat = materialsStr.toDoubleOrNull() ?: 0.0
    val lab = laborStr.toDoubleOrNull() ?: 0.0
    val adv = advanceStr.toDoubleOrNull() ?: 0.0
    val total = mat + lab
    val balance = (total - adv).coerceAtLeast(0.0)

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.5.dp, NeoTheme.Green)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ReceiptLong, null, tint = NeoTheme.Green, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nueva Cotización / Presupuesto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Selector de Oficio
                Text("Especialidad / Tipo de Trabajo:", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    oficiosList.forEach { of ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (oficio == of) NeoTheme.Green.copy(0.2f) else Color(0xFF1E293B),
                            border = if (oficio == of) BorderStroke(1.dp, NeoTheme.Green) else null,
                            modifier = Modifier.clickable { oficio = of }
                        ) {
                            Text(of, color = if (oficio == of) NeoTheme.Green else Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 1. Concepto / Detalles del trabajo (Enter pasa a Materiales)
                OutlinedTextField(
                    value = concept,
                    onValueChange = { concept = it },
                    label = { Text("Descripción o detalles del mueble / trabajo") },
                    modifier = Modifier.fillMaxWidth().focusRequester(conceptFocus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { materialsFocus.requestFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeoTheme.Green, unfocusedBorderColor = Color(0xFF334155), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(Modifier.height(10.dp))

                // 2 y 3. Materiales y Mano de Obra (Enter pasa de uno a otro)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = materialsStr,
                        onValueChange = { materialsStr = it },
                        label = { Text("Materiales ($)") },
                        modifier = Modifier.weight(1f).focusRequester(materialsFocus),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { laborFocus.requestFocus() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeoTheme.Green, unfocusedBorderColor = Color(0xFF334155), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = laborStr,
                        onValueChange = { laborStr = it },
                        label = { Text("Mano de Obra ($)") },
                        modifier = Modifier.weight(1f).focusRequester(laborFocus),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { advanceFocus.requestFocus() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeoTheme.Green, unfocusedBorderColor = Color(0xFF334155), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 4. Anticipo recibido (Enter pasa a Garantía)
                OutlinedTextField(
                    value = advanceStr,
                    onValueChange = { advanceStr = it },
                    label = { Text("Anticipo recibido / inicial ($)") },
                    modifier = Modifier.fillMaxWidth().focusRequester(advanceFocus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { notesFocus.requestFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeoTheme.Green, unfocusedBorderColor = Color(0xFF334155), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(Modifier.height(10.dp))

                // 5. Garantía / Condiciones (Enter termina y oculta teclado)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Garantía / Condiciones (Ej: 30 días de garantía)") },
                    modifier = Modifier.fillMaxWidth().focusRequester(notesFocus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeoTheme.Green, unfocusedBorderColor = Color(0xFF334155), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(Modifier.height(16.dp))

                // Resumen Calculado en Pesos con formato profesional
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Presupuestado:", color = Color.LightGray, fontSize = 14.sp)
                            Text(currencyFormat.format(total), color = NeoTheme.Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        if (adv > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Saldo Restante por Liquidar:", color = Color(0xFFFFD700), fontSize = 13.sp)
                                Text(currencyFormat.format(balance), color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = Color.LightGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (concept.isBlank() && total <= 0) {
                                return@Button
                            }
                            val q = NeoQuote(
                                oficio = oficio,
                                concept = concept.ifBlank { "Trabajo / Servicio $oficio" },
                                materialsCost = mat,
                                laborCost = lab,
                                advancePayment = adv,
                                notes = notes
                            )
                            onSendQuote(q)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Send, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ENVIAR AL CHAT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * =========================================================================
 * 3. COMPONENTE TARJETA DE COTIZACIÓN PROFESIONAL DENTRO DEL CHAT
 * =========================================================================
 */
@Composable
fun QuoteMessageCard(
    quote: NeoQuote,
    isMine: Boolean,
    onAccept: () -> Unit = {}
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(quote.timestamp))

    Surface(
        modifier = Modifier.widthIn(min = 260.dp, max = 320.dp).padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isMine) Color(0xFF00382B) else Color(0xFF1E293B),
        border = BorderStroke(1.5.dp, if (quote.status == "ACEPTADA") NeoTheme.Green else Color(0xFFFFD700))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera Folio + Tipo de Oficio
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFD700).copy(0.2f)
                ) {
                    Text(quote.oficio.uppercase(), color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(quote.folio, color = Color.LightGray, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }

            Spacer(Modifier.height(8.dp))

            // Concepto
            Text(quote.concept, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(0.1f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Desglose de Costos
            if (quote.materialsCost > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Materiales:", color = Color.LightGray, fontSize = 12.sp)
                    Text(currencyFormat.format(quote.materialsCost), color = Color.White, fontSize = 12.sp)
                }
            }
            if (quote.laborCost > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mano de obra:", color = Color.LightGray, fontSize = 12.sp)
                    Text(currencyFormat.format(quote.laborCost), color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Total
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(currencyFormat.format(quote.total), color = NeoTheme.Green, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            if (quote.advancePayment > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Anticipo pagado:", color = Color(0xFFFFD700), fontSize = 12.sp)
                    Text(currencyFormat.format(quote.advancePayment), color = Color(0xFFFFD700), fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo a liquidar:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(currencyFormat.format(quote.balanceRemaining), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            if (quote.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Nota: ${quote.notes}", color = Color.Gray, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }

            Spacer(Modifier.height(10.dp))

            // Estado / Botón de Aprobación
            if (quote.status == "ACEPTADA") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeoTheme.Green.copy(0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = NeoTheme.Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PRESUPUESTO ACEPTADO", color = NeoTheme.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else if (!isMine) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) {
                    Icon(Icons.Filled.ThumbUp, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ACEPTAR PRESUPUESTO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text("⏳ Esperando aprobación del cliente", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            val context = LocalContext.current
            val fullText = """
📋 *COTIZACIÓN PROFESIONAL NEOMESSAGE*
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📌 *Folio:* ${quote.folio} | *${quote.oficio.uppercase()}*
📝 *Trabajo:* ${quote.concept}

💰 *DESGLOSE:*
${if (quote.materialsCost > 0) " • Materiales: ${currencyFormat.format(quote.materialsCost)}\n" else ""}${if (quote.laborCost > 0) " • Mano de Obra: ${currencyFormat.format(quote.laborCost)}\n" else ""}────────────────────────────
💵 *TOTAL:* ${currencyFormat.format(quote.total)}
${if (quote.advancePayment > 0) "💸 *Anticipo:* ${currencyFormat.format(quote.advancePayment)}\n⚠️ *Saldo a liquidar:* ${currencyFormat.format(quote.balanceRemaining)}\n" else ""}${if (quote.notes.isNotBlank()) "🛡️ *Garantía / Condiciones:* ${quote.notes}\n" else ""}━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Generado con NeoMessage
""".trimIndent()

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Cotización", fullText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "📋 Cotización copiada completa al portapapeles.", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00FF7F).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("COPIAR TODO", color = Color(0xFF00FF7F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        try {
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, fullText)
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Enviar Cotización"))
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Share, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("COMPARTIR", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(dateStr, color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

/**
 * =========================================================================
 * 4. ANOTADOR DE MEDIDAS TÉCNICAS Y COTAS SOBRE FOTOS (HD 2048px)
 * =========================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalMeasureAnnotatorDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onSaveAnnotatedImage: (Uri) -> Unit
) {
    val context = LocalContext.current
    val originalBitmap = remember(imageUri) {
        try {
            val stream = context.contentResolver.openInputStream(imageUri)
            BitmapFactory.decodeStream(stream)
        } catch (_: Exception) { null }
    }

    if (originalBitmap == null) {
        Toast.makeText(context, "No se pudo cargar la imagen para acotación", Toast.LENGTH_SHORT).show()
        onDismiss()
        return
    }

    var lines by remember { mutableStateOf(listOf<MeasureLine>()) }
    var currentStart by remember { mutableStateOf<Offset?>(null) }
    var currentEnd by remember { mutableStateOf<Offset?>(null) }
    var activeMeasureText by remember { mutableStateOf("2.40 m") }
    var showTextPrompt by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TopBar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, null, tint = Color.White)
                    }
                    Text("📐 Trazar Medidas / Cotas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = {
                        // Renderizar líneas directamente sobre el Bitmap en alta calidad y exportar
                        val outputBmp = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(outputBmp)
                        val paint = Paint().apply {
                            color = android.graphics.Color.YELLOW
                            strokeWidth = 14f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        val textPaint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 58f
                            typeface = Typeface.DEFAULT_BOLD
                            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                        }

                        // Guardar medidas
                        lines.forEach { ml ->
                            val sx = ml.start.x * outputBmp.width
                            val sy = ml.start.y * outputBmp.height
                            val ex = ml.end.x * outputBmp.width
                            val ey = ml.end.y * outputBmp.height
                            canvas.drawLine(sx, sy, ex, ey, paint)
                            // Flechas en los extremos
                            canvas.drawCircle(sx, sy, 16f, paint)
                            canvas.drawCircle(ex, ey, 16f, paint)
                            // Texto centrado
                            val mx = (sx + ex) / 2
                            val my = (sy + ey) / 2 - 14f
                            canvas.drawText(ml.label, mx, my, textPaint)
                        }

                        // Guardar a archivo temporal
                        val file = java.io.File(context.cacheDir, "annotated_${System.currentTimeMillis()}.jpg")
                        val fos = java.io.FileOutputStream(file)
                        outputBmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        fos.flush()
                        fos.close()

                        val newUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        onSaveAnnotatedImage(newUri)
                    }) {
                        Icon(Icons.Filled.Check, null, tint = NeoTheme.Green, modifier = Modifier.size(30.dp))
                    }
                }

                // Área de dibujo interactiva con el dedo
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = "Foto para acotación",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val nx = offset.x / size.width
                                    val ny = offset.y / size.height
                                    currentStart = Offset(nx, ny)
                                    currentEnd = Offset(nx, ny)
                                },
                                onDrag = { change, _ ->
                                    val nx = change.position.x / size.width
                                    val ny = change.position.y / size.height
                                    currentEnd = Offset(nx, ny)
                                },
                                onDragEnd = {
                                    if (currentStart != null && currentEnd != null) {
                                        showTextPrompt = true
                                    }
                                }
                            )
                        }
                    )

                    // Overlay Canvas en tiempo real
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        lines.forEach { ml ->
                            val s = Offset(ml.start.x * size.width, ml.start.y * size.height)
                            val e = Offset(ml.end.x * size.width, ml.end.y * size.height)
                            drawLine(Color.Yellow, s, e, strokeWidth = 5f)
                            drawCircle(Color.Yellow, radius = 8f, center = s)
                            drawCircle(Color.Yellow, radius = 8f, center = e)
                        }
                        if (currentStart != null && currentEnd != null) {
                            val s = Offset(currentStart!!.x * size.width, currentStart!!.y * size.height)
                            val e = Offset(currentEnd!!.x * size.width, currentEnd!!.y * size.height)
                            drawLine(Color.Green, s, e, strokeWidth = 6f)
                        }
                    }
                }

                // Barra inferior de atajos rápidos de medidas
                Surface(color = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Medidas rápidas:", color = Color.Gray, fontSize = 11.sp)
                        listOf("1.00 m", "1.20 m", "2.40 m", "50 cm", "15 cm", "3/4\"", "1/2\"", "90°").forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (activeMeasureText == m) NeoTheme.Green else Color(0xFF334155),
                                modifier = Modifier.clickable { activeMeasureText = m }
                            ) {
                                Text(m, color = if (activeMeasureText == m) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTextPrompt) {
        AlertDialog(
            onDismissRequest = { showTextPrompt = false },
            title = { Text("Escribe la medida de esta cota", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = activeMeasureText,
                    onValueChange = { activeMeasureText = it },
                    label = { Text("Ej: 2.40 m, 15 cm, 1 pulgada") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (currentStart != null && currentEnd != null) {
                        lines = lines + MeasureLine(currentStart!!, currentEnd!!, activeMeasureText)
                        currentStart = null
                        currentEnd = null
                    }
                    showTextPrompt = false
                }) {
                    Text("GUARDAR COTA")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    currentStart = null
                    currentEnd = null
                    showTextPrompt = false
                }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

data class MeasureLine(val start: Offset, val end: Offset, val label: String)
