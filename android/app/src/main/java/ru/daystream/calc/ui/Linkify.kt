package ru.daystream.calc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

private val UrlRegex = Regex("""\b(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)

internal const val UrlTag = "URL"

/**
 * Разметить http(s) и www. в тексте сообщения.
 */
fun linkifiedText(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        UrlRegex.findAll(text).forEach { match ->
            val cut = trimUrl(match.value)
            val href = hrefOf(cut) ?: return@forEach
            if (cut.length < 4) return@forEach
            val start = match.range.first
            val end = start + cut.length
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
            addStringAnnotation(UrlTag, href, start, end)
        }
    }
}

private fun trimUrl(raw: String): String {
    var s = raw
    while (s.isNotEmpty() && s.last() in ".,;:!?") s = s.dropLast(1)
    while (s.endsWith(")") && s.count { it == '(' } < s.count { it == ')' }) s = s.dropLast(1)
    return s
}

private fun hrefOf(cut: String): String? {
    val t = cut.trim()
    if (t.length < 4) return null
    val href = if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    val lower = href.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    return href
}
