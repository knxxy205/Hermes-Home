package com.example.data.ssh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object AnsiTerminalParser {

    private val ColorDefault = Color(0xFFE2E8F0)
    private val ColorGreen = Color(0xFF39FF14)
    private val ColorCyan = Color(0xFF00F0FF)
    private val ColorYellow = Color(0xFFFFD166)
    private val ColorRed = Color(0xFFFF5964)
    private val ColorBlue = Color(0xFF60A5FA)
    private val ColorMagenta = Color(0xFFE879F9)
    private val ColorGray = Color(0xFF94A3B8)
    private val ColorDarkGray = Color(0xFF64748B)

    fun parse(rawText: String): AnnotatedString {
        if (!rawText.contains("\u001B[")) {
            return AnnotatedString(rawText)
        }

        return buildAnnotatedString {
            var currentColor = ColorDefault
            var currentBold = false

            // Regex matches ESC[<codes>m
            val regex = Regex("\u001B\\[([0-9;]*)m")
            var lastIndex = 0

            for (match in regex.findAll(rawText)) {
                val textBefore = rawText.substring(lastIndex, match.range.first)
                if (textBefore.isNotEmpty()) {
                    // Filter out cursor/screen clear control codes like \u001b[?25h, \u001b[K, etc.
                    val cleanedText = textBefore.replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]"), "")
                    if (cleanedText.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                color = currentColor,
                                fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal
                            )
                        ) {
                            append(cleanedText)
                        }
                    }
                }

                val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
                if (codes.isEmpty() || codes.contains(0)) {
                    currentColor = ColorDefault
                    currentBold = false
                }

                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = ColorDefault
                            currentBold = false
                        }
                        1 -> currentBold = true
                        2, 22 -> currentBold = false
                        30 -> currentColor = ColorDarkGray
                        31, 91 -> currentColor = ColorRed
                        32, 92 -> currentColor = ColorGreen
                        33, 93 -> currentColor = ColorYellow
                        34, 94 -> currentColor = ColorBlue
                        35, 95 -> currentColor = ColorMagenta
                        36, 96 -> currentColor = ColorCyan
                        37, 97 -> currentColor = ColorDefault
                        39 -> currentColor = ColorDefault
                        90 -> currentColor = ColorGray
                    }
                }

                lastIndex = match.range.last + 1
            }

            if (lastIndex < rawText.length) {
                val remaining = rawText.substring(lastIndex)
                    .replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]"), "")
                if (remaining.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            color = currentColor,
                            fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal
                        )
                    ) {
                        append(remaining)
                    }
                }
            }
        }
    }
}
