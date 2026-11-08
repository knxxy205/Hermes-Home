package com.example.ui.hermes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

fun isShellCode(language: String): Boolean = language.trim().lowercase() in setOf("bash", "sh", "shell", "zsh", "ksh", "fish")

fun highlightCode(code: String, language: String, dark: Boolean = false): AnnotatedString {
    val lang = language.trim().lowercase()
    val markup = lang in setOf("html", "xml", "svg", "jsx", "tsx")
    val shellOrPython = isShellCode(lang) || lang in setOf("py", "python")
    val supported = markup || shellOrPython || lang in setOf("js", "javascript", "ts", "typescript", "json", "kotlin", "kt", "java", "css", "sql")
    if (!supported || code.length > 100_000) return AnnotatedString(code)
    val strings = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
    val pattern = if (markup) Regex("<!--[\\s\\S]*?-->|</?[A-Za-z][\\w:-]*|/?>|$strings|\\b[A-Za-z_:][\\w:.-]*(?=\\s*=)")
        else Regex("$strings|${if (shellOrPython) "#[^\\n]*" else "//[^\\n]*|/\\*[\\s\\S]*?\\*/"}|\\b(?:const|let|var|fun|function|class|return|if|else|for|while|def|import|from|true|false|null|None|True|False|sudo|echo|export|select|SELECT|FROM|WHERE)\\b|\\b\\d+(?:\\.\\d+)?\\b")
    return buildAnnotatedString {
        append(code)
        pattern.findAll(code).forEach { match ->
            val token = match.value
            val color = when {
                token.startsWith("<!--") || token.startsWith("#") || token.startsWith("//") || token.startsWith("/*") -> if (dark) Color(0xFFB3C0D2) else Color(0xFF59636F)
                token.startsWith('"') || token.startsWith('\'') -> if (dark) Color(0xFF8ED8DF) else Color(0xFF146579)
                markup && !token.startsWith('<') && token != ">" && token != "/>" -> if (dark) Color(0xFFC2D68E) else Color(0xFF596315)
                token.firstOrNull()?.isDigit() == true -> if (dark) Color(0xFFE8C68C) else Color(0xFF795500)
                else -> if (dark) Color(0xFFF0ACDC) else Color(0xFF8C2869)
            }
            addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
        }
    }
}
