package ru.daystream.calc.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val HISTORY_LIMIT = 20
private val ErrorTokens = listOf("Infinity", "-Infinity", "NaN", "Ошибка")
private val BinaryOps = setOf("+", "-", "×", "÷", "*", "/", "^")
private val BinaryOpChars = setOf('+', '-', '×', '÷', '*', '/', '^')

/**
 * Публичный экран калькулятора. Не знает про чат.
 *
 * @param history история последнего входа
 * @param onHistoryChange сохранить ленту
 * @param onActivate открыть страницу активации
 */
@Composable
fun CalculatorScreen(
    history: List<String>,
    onHistoryChange: (List<String>) -> Unit,
    onActivate: ActivationCallback,
) {
    val engine = remember { CalculatorEngine() }
    val expression = remember { mutableStateOf("") }
    val justEvaluated = remember { mutableStateOf(false) }
    val tape = remember { mutableStateListOf<String>().apply { addAll(history.take(HISTORY_LIMIT)) } }

    /**
     * После «=» цифра начинает новый пример; оператор продолжает результат.
     */
    fun startFreshNumber() {
        if (!justEvaluated.value) return
        expression.value = ""
        justEvaluated.value = false
    }

    /**
     * Цифра: 12 знаков до запятой, 6 после; без лишнего нуля в начале числа.
     */
    fun appendDigit(token: String) {
        startFreshNumber()
        val s = expression.value
        val n = currentNumber(s)
        if (n.contains('.')) {
            if (fractionDigits(n) >= MaxFractionDigits) return
        } else if (n == "0") {
            if (token == "0") return
            expression.value = s.dropLast(1) + token
            return
        } else if (integerDigits(n) >= MaxIntegerDigits) {
            return
        }
        if (token == "0") {
            if (canAppendZero(s)) expression.value += "0"
            return
        }
        expression.value = s + token
    }

    /**
     * Добавить токен в выражение: нули, 6 знаков после запятой, смена знака.
     */
    fun append(token: String) {
        var s = expression.value.removeSuffix("...")
        if (isErrorToken(s)) {
            s = ""
            justEvaluated.value = false
        }
        expression.value = s
        when {
            token in BinaryOps -> {
                justEvaluated.value = false
                if (s.isEmpty()) {
                    if (token == "-") expression.value = "-"
                    return
                }
                expression.value = if (s.last() in BinaryOpChars) s.dropLast(1) + token else s + token
            }
            token == "000" -> repeat(3) { appendDigit("0") }
            token == "0" -> appendDigit("0")
            token == "." -> {
                startFreshNumber()
                val n = currentNumber(expression.value)
                if (n.contains('.')) return
                expression.value += if (n.isEmpty()) "0." else "."
            }
            token.length == 1 && token[0] in '1'..'9' -> appendDigit(token)
            else -> {
                if (token != ")") startFreshNumber()
                expression.value += token
            }
        }
    }

    /**
     * Стереть символ; многоточие / Infinity / Ошибка — целиком.
     */
    fun backspace() {
        val s = expression.value
        if (s.isEmpty()) return
        if (s.endsWith("...")) {
            expression.value = s.dropLast(3)
            justEvaluated.value = false
            return
        }
        val hit = ErrorTokens.filter { s.endsWith(it) }.maxByOrNull { it.length }
        expression.value = if (hit != null) s.dropLast(hit.length) else s.dropLast(1)
        justEvaluated.value = false
    }

    /**
     * Посчитать полное выражение и дописать в историю (не больше 20).
     * Неполное («1», «1-») равно не трогает.
     */
    fun equals() {
        val src = expression.value.removeSuffix("...").trim()
        if (src.isBlank() || isErrorToken(src) || isBareNumber(src)) return
        val result = engine.evaluate(src) ?: return
        if (result == "Ошибка") {
            expression.value = "Ошибка"
            justEvaluated.value = false
            return
        }
        tape.add(0, "$src = $result")
        while (tape.size > HISTORY_LIMIT) tape.removeAt(tape.lastIndex)
        onHistoryChange(tape.toList())
        expression.value = result
        justEvaluated.value = true
    }

    /**
     * Подставить результат истории: заменить текущее число, как смену знака.
     * Повтор того же значения ничего не меняет.
     */
    fun insertHistory(line: String) {
        val result = line.substringAfterLast(" = ").trim()
        if (result.isEmpty() || isErrorToken(result)) return
        var s = expression.value.removeSuffix("...")
        if (s.isBlank() || isErrorToken(s) || justEvaluated.value || isBareNumber(s)) {
            if (!s.isBlank() && !isErrorToken(s) && sameCalcNumber(s, result)) {
                justEvaluated.value = false
                return
            }
            expression.value = result
            justEvaluated.value = false
            return
        }
        val n = currentNumber(s)
        if (sameCalcNumber(n, result)) return
        expression.value = s.dropLast(n.length) + result
        justEvaluated.value = false
    }

    /**
     * Стереть ленту вычислений.
     */
    fun clearHistory() {
        tape.clear()
        onHistoryChange(emptyList())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Калькулятор", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onActivate.onActivationRequested() }) { Text("Ввести ключ") }
        }
        LazyColumn(Modifier.weight(0.7f).fillMaxWidth(), reverseLayout = true) {
            items(tape) { line ->
                Text(
                    line,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { insertHistory(line) }
                        .padding(4.dp),
                )
            }
        }
        Text(
            expression.value.ifBlank { "0" },
            fontSize = 36.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Pad(
            modifier = Modifier.weight(1.3f).fillMaxWidth(),
            dimmed = listOf("log", "ln", "sin", "cos", "tan"),
            onDimmed = { onActivate.onActivationRequested() },
            onKey = { key ->
                when (key) {
                    "hist" -> clearHistory()
                    "C" -> {
                        expression.value = ""
                        justEvaluated.value = false
                    }
                    "⌫" -> backspace()
                    "=" -> equals()
                    "x^y" -> append("^")
                    else -> append(key)
                }
            },
        )
    }
}

/**
 * Клавиатура: справа история / очистка / стереть / равно на две клетки.
 */
@Composable
private fun Pad(
    modifier: Modifier = Modifier,
    dimmed: List<String>,
    onDimmed: () -> Unit,
    onKey: (String) -> Unit,
) {
    val left = listOf(
        listOf("(", ")", "√", "x^y"),
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "-"),
        listOf("0", "000", ".", "+"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("log", "ln", "sin", "cos", "tan").forEach { label ->
                CalcKey(label, Modifier.weight(1f), dimmed = true) { onDimmed() }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                left.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { id ->
                            CalcKey(if (id == "x^y") "x^y" else id, Modifier.weight(1f).fillMaxHeight()) { onKey(id) }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CalcKey("hist", Modifier.weight(1f).fillMaxHeight(), icon = true) { onKey("hist") }
                CalcKey("C", Modifier.weight(1f).fillMaxHeight()) { onKey("C") }
                CalcKey("⌫", Modifier.weight(1f).fillMaxHeight()) { onKey("⌫") }
                CalcKey("=", Modifier.weight(2f).fillMaxHeight(), primary = true) { onKey("=") }
            }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier,
    dimmed: Boolean = false,
    primary: Boolean = false,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.alpha(if (dimmed) 0.35f else 1f),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (icon && label == "hist") {
            Icon(Icons.Outlined.History, contentDescription = "История")
        } else {
            Text(label, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Текущее число в конце выражения (после оператора или скобки).
 */
private fun currentNumber(expr: String): String {
    val s = expr.removeSuffix("...")
    val ops = setOf('+', '-', '×', '÷', '*', '/', '^', '(', ')', '√')
    var i = s.length - 1
    while (i >= 0 && s[i] !in ops) i--
    return s.substring(i + 1)
}

/**
 * Сколько цифр до запятой у текущего числа.
 */
private fun integerDigits(number: String): Int = number.substringBefore('.').length

/**
 * Сколько цифр уже стоит после запятой у текущего числа.
 */
private fun fractionDigits(number: String): Int {
    val dot = number.indexOf('.')
    if (dot < 0) return 0
    return number.length - dot - 1
}

/**
 * Только число (знаки ± и внешние скобки не считаются выражением).
 */
private fun isBareNumber(expr: String): Boolean {
    var s = expr.removeSuffix("...").trim()
    while (s.length >= 2 && s.first() == '(' && s.last() == ')' && wrapped(s)) {
        s = s.substring(1, s.lastIndex).trim()
    }
    if (s.startsWith('+') || s.startsWith('-')) s = s.drop(1)
    if (s.isEmpty()) return true
    var seenDot = false
    var seenDigit = false
    for (c in s) {
        when {
            c.isDigit() -> seenDigit = true
            c == '.' && !seenDot -> seenDot = true
            else -> return false
        }
    }
    return seenDigit
}

/**
 * Внешняя пара скобок охватывает всю строку.
 */
private fun wrapped(s: String): Boolean {
    var depth = 0
    for (i in s.indices) {
        when (s[i]) {
            '(' -> depth++
            ')' -> depth--
        }
        if (depth < 0) return false
        if (depth == 0 && i != s.lastIndex) return false
    }
    return depth == 0
}

/**
 * Можно ли дописать 0: не 00 в начале числа, после точки — да.
 */
private fun canAppendZero(expr: String): Boolean {
    val n = currentNumber(expr)
    if (n.contains('.')) return true
    if (n.isEmpty()) return true
    return n != "0"
}

private fun isErrorToken(value: String): Boolean = value in ErrorTokens

/**
 * Одно и то же число на экране и из истории (без «...»).
 */
private fun sameCalcNumber(left: String, right: String): Boolean =
    left.removeSuffix("...") == right.removeSuffix("...")
