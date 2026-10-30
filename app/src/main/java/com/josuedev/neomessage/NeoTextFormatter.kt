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

    /**
     * Parsea texto enriquecido estilo WhatsApp / Markdown ligero:
     * - *negrita* -> FontWeight.Bold
     * - _cursiva_ -> FontStyle.Italic
     * - ~tachado~ -> TextDecoration.LineThrough
     * - ```código``` -> FontFamily.Monospace con fondo tenue
     */
    fun format(text: String, defaultColor: Color = Color.White): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        // Regex para capturar código ```...```, negrita *...*, cursiva _..._, tachado ~...~
        val pattern = "(```(?:[\\s\\S]*?)```|\\*([^*\\n]+)\\*|_([^_\\n]+)_|~([^~\\n]+)~)"
        val regex = Regex(pattern)

        return buildAnnotatedString {
            var currentIndex = 0
            val matches = regex.findAll(text)

            for (match in matches) {
                val matchRange = match.range

                // Añadir texto previo sin formato
                if (matchRange.first > currentIndex) {
                    append(text.substring(currentIndex, matchRange.first))
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
                        append(inner)
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start,
                            length
                        )
                    }
                    fullMatch.startsWith("_") && fullMatch.endsWith("_") && fullMatch.length >= 2 -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        val start = length
                        append(inner)
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start,
                            length
                        )
                    }
                    fullMatch.startsWith("~") && fullMatch.endsWith("~") && fullMatch.length >= 2 -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        val start = length
                        append(inner)
                        addStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough),
                            start,
                            length
                        )
                    }
                    else -> append(fullMatch)
                }

                currentIndex = matchRange.last + 1
            }

            // Añadir el resto de texto después de la última coincidencia
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }
}
