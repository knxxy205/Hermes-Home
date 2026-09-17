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
        if (!rawText.contains("\u001B")) {
            return AnnotatedString(rawText)
        }

        // Clean out VT100 non-SGR sequences first:
        // ESC ( B, ESC ) 0, ESC =, ESC >, ESC 7, ESC 8, ESC M, etc.
        val preCleaned = rawText
            .replace(Regex("\u001B[()][0-9A-Za-z]"), "")
            .replace(Regex("\u001B[=>78Mc]"), "")
            .replace(Regex("\u001B\\][^\u0007\u001B]*[\u0007\u001B\\\\]"), "")

        return buildAnnotatedString {
            var currentColor = ColorDefault
            var currentBold = false
            var currentInverse = false

            // Regex matches ESC[<codes>m
            val regex = Regex("\u001B\\[([0-9;]*)m")
            var lastIndex = 0

            for (match in regex.findAll(preCleaned)) {
                val textBefore = preCleaned.substring(lastIndex, match.range.first)
                if (textBefore.isNotEmpty()) {
                    // Filter out cursor/screen clear control codes like \u001b[?25h, \u001b[K, \u001b[2J, etc.
                    val cleanedText = textBefore.replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]"), "")
                    if (cleanedText.isNotEmpty()) {
                        val effectiveFg = if (currentInverse) Color(0xFF05080E) else currentColor
                        val effectiveBg = if (currentInverse) Color(0xFFCBD5E1) else Color.Transparent
                        withStyle(
                            SpanStyle(
                                color = effectiveFg,
                                background = effectiveBg,
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
                    currentInverse = false
                }

                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = ColorDefault
                            currentBold = false
                            currentInverse = false
                        }
                        1 -> currentBold = true
                        2, 22 -> currentBold = false
                        7 -> currentInverse = true
                        27 -> currentInverse = false
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

            if (lastIndex < preCleaned.length) {
                val remaining = preCleaned.substring(lastIndex)
                    .replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]"), "")
                if (remaining.isNotEmpty()) {
                    val effectiveFg = if (currentInverse) Color(0xFF05080E) else currentColor
                    val effectiveBg = if (currentInverse) Color(0xFFCBD5E1) else Color.Transparent
                    withStyle(
                        SpanStyle(
                            color = effectiveFg,
                            background = effectiveBg,
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
