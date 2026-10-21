package ru.daystream.calc.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3390EC),
    onPrimary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE6EBEE),
    onSurfaceVariant = Color(0xFF707579),
    outline = Color(0xFFDFE2E5),
    error = Color(0xFFE53935),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5288C1),
    onPrimary = Color.White,
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF17212B),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF182533),
    onSurfaceVariant = Color(0xFF8B9AA8),
    outline = Color(0xFF2B3A48),
    error = Color(0xFFE57373),
)

val BubbleOutLight = Color(0xFFEEFFDE)
val BubbleInLight = Color(0xFFFFFFFF)
val BubbleOutDark = Color(0xFF2B5278)
val BubbleInDark = Color(0xFF182533)
val ChatBgLight = Color(0xFFE6EBEE)
val ChatBgDark = Color(0xFF0E1621)
val TickRead = Color(0xFF4FAEEF)

/**
 * Тема приложения: светлая / тёмная / как в системе.
 */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    /** system | light | dark */
    var mode: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) {
            prefs.edit().putString("theme", value).apply()
        }
}

/**
 * Общая тема калькулятора, активации и чата.
 *
 * @param mode system / light / dark
 */
@Composable
fun AppTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background, content = content)
    }
}

/**
 * Цвета полей ввода: текст всегда контрастен фону поля.
 */
@Composable
fun readableFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface.copy(alpha = 0.6f),
        cursorColor = scheme.primary,
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
    )
}

/**
 * Last seen по-русски, как в п. 6.2.
 */
fun lastSeenText(iso: String?, online: Boolean): String {
    if (online) return "в сети"
    if (iso.isNullOrBlank()) return ""
    val then = parseIso(iso) ?: return ""
    val diff = System.currentTimeMillis() - then
    if (diff < 60_000) return "был(а) только что"
    if (diff < 5 * 60_000) return "был(а) 5 минут назад"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = then }
    val now = java.util.Calendar.getInstance()
    val hh = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    if (sameDay(cal, now)) return "был(а) в $hh"
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (sameDay(cal, now)) return "был(а) вчера в $hh"
    val date = "%02d.%02d.%04d".format(
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.YEAR),
    )
    return "был(а) $date в $hh"
}

/**
 * Короткое время пузыря.
 */
fun shortTime(iso: String?): String {
    val then = parseIso(iso) ?: return ""
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = then }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

private val MonthsRu = arrayOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

/**
 * Подпись смены дня в ленте, как в Telegram.
 */
fun dayChipLabel(iso: String?): String {
    val then = parseIso(iso) ?: return ""
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = then }
    val now = java.util.Calendar.getInstance()
    if (sameDay(cal, now)) return "сегодня"
    val yest = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(cal, yest)) return "вчера"
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val month = MonthsRu[cal.get(java.util.Calendar.MONTH)]
    return if (cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) {
        "$day $month"
    } else {
        "$day $month ${cal.get(java.util.Calendar.YEAR)}"
    }
}

/**
 * Один календарный день у двух ISO-меток.
 */
fun sameMessageDay(a: String?, b: String?): Boolean {
    val ta = parseIso(a) ?: return false
    val tb = parseIso(b) ?: return false
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = ta }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = tb }
    return sameDay(ca, cb)
}

/**
 * Цвет круга-заглушки аватара.
 */
fun avatarColor(login: String): Color {
    val colors = listOf(
        Color(0xFFE17076), Color(0xFF7BC862), Color(0xFFE5CA77),
        Color(0xFF65AADD), Color(0xFFA695E7), Color(0xFFEE7AAE),
    )
    var h = 0
    login.forEachIndexed { i, c -> h = (h + c.code * (i + 1)) % colors.size }
    return colors[h]
}

private fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean {
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun parseIso(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        java.time.Instant.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" }).toEpochMilli()
    } catch (_: Exception) {
        null
    }
}
