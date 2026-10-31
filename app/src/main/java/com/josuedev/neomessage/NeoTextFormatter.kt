package com.josuedev.neomessage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object NeoTextFormatter {

    // Regex para URLs (http, https, www o dominios populares como youtube.com, facebook.com, drive.google.com, etc.)
    private val URL_REGEX = Regex(
        """(https?://[^\s]+|www\.[^\s]+|[a-zA-Z0-9.-]+\.(?:com|org|net|edu|gov|mx|io|app|dev|me|tv|info|online|site|store|shop|xyz|tech|live)\b(?:/[^\s]*)?)""",
        RegexOption.IGNORE_CASE
    )

    // Regex para números telefónicos de 10 dígitos o con código de país (+52 o similar)
    private val PHONE_REGEX = Regex("""(?:\+?52\s*)?(?:\(?\d{3}\)?[- ]?\d{3}[- ]?\d{4}|\b\d{10}\b)""")

    // Regex combinado para buscar URLs y Teléfonos
    private val LINK_OR_PHONE_REGEX = Regex(
        """(https?://[^\s]+|www\.[^\s]+|[a-zA-Z0-9.-]+\.(?:com|org|net|edu|gov|mx|io|app|dev|me|tv|info|online|site|store|shop|xyz|tech|live)\b(?:/[^\s]*)?|(?:\+?52\s*)?(?:\(?\d{3}\)?[- ]?\d{3}[- ]?\d{4}|\b\d{10}\b))""",
        RegexOption.IGNORE_CASE
    )

    // Regex para estilos Markdown clásicos: código ```...```, negrita *...*, cursiva _..._, tachado ~...~
    private val MARKDOWN_REGEX = Regex("""(```(?:[\s\S]*?)```|\*([^*\n]+)\*|_([^_\n]+)_|~([^~\n]+)~)""")

    fun format(text: String, defaultColor: Color = Color.White): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            var currentIndex = 0
            val matches = MARKDOWN_REGEX.findAll(text)

            for (match in matches) {
                val matchRange = match.range

                // Añadir texto previo procesando enlaces y teléfonos
                if (matchRange.first > currentIndex) {
                    appendWithLinksAndPhones(text.substring(currentIndex, matchRange.first))
                }

                val fullMatch = match.value
                when {
                    fullMatch.startsWith("```") && fullMatch.endsWith("```") && fullMatch.length >= 6 -> {
                        val inner = fullMatch.substring(3, fullMatch.length - 3)
                        val start = length
                        append(inner)
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF1E293B).copy(alpha = 0.8f),
                                color = Color(0xFF00FF7F)
                            ),
                            start,
                            length
                        )
                    }
                    fullMatch.startsWith("*") && fullMatch.endsWith("*") && fullMatch.length >= 2 -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        val start = length
                        appendWithLinksAndPhones(inner)
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start,
                            length
                        )
                    }
                    fullMatch.startsWith("_") && fullMatch.endsWith("_") && fullMatch.length >= 2 -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        val start = length
                        appendWithLinksAndPhones(inner)
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start,
                            length
                        )
                    }
                    fullMatch.startsWith("~") && fullMatch.endsWith("~") && fullMatch.length >= 2 -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        val start = length
                        appendWithLinksAndPhones(inner)
                        addStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough),
                            start,
                            length
                        )
                    }
                    else -> appendWithLinksAndPhones(fullMatch)
                }

                currentIndex = matchRange.last + 1
            }

            if (currentIndex < text.length) {
                appendWithLinksAndPhones(text.substring(currentIndex))
            }
        }
    }

    private fun AnnotatedString.Builder.appendWithLinksAndPhones(chunk: String) {
        if (chunk.isEmpty()) return

        val matches = LINK_OR_PHONE_REGEX.findAll(chunk)
        var idx = 0

        for (m in matches) {
            if (m.range.first > idx) {
                append(chunk.substring(idx, m.range.first))
            }

            val rawVal = m.value
            val isUrl = rawVal.contains(".") && !rawVal.all { it.isDigit() || it == '-' || it == ' ' || it == '+' || it == '(' || it == ')' }

            val start = length
            append(rawVal)

            if (isUrl) {
                val cleanUrl = if (rawVal.startsWith("http://", ignoreCase = true) || rawVal.startsWith("https://", ignoreCase = true)) {
                    rawVal
                } else {
                    "https://$rawVal"
                }
                addStringAnnotation(tag = "URL", annotation = cleanUrl, start = start, end = length)
                addStyle(
                    SpanStyle(
                        color = Color(0xFF38BDF8),
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start,
                    length
                )
            } else {
                val cleanDigits = rawVal.filter { it.isDigit() }.takeLast(10)
                if (cleanDigits.length == 10) {
                    addStringAnnotation(tag = "PHONE", annotation = cleanDigits, start = start, end = length)
                    addStyle(
                        SpanStyle(
                            color = Color(0xFF00FF7F),
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        ),
                        start,
                        length
                    )
                }
            }

            idx = m.range.last + 1
        }

        if (idx < chunk.length) {
            append(chunk.substring(idx))
        }
    }
}
