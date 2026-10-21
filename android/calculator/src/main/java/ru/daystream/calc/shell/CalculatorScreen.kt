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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val tape = remember { mutableStateListOf<String>().apply { addAll(history.take(HISTORY_LIMIT)) } }

    /**
     * Добавить токен в выражение с правилами нулей.
     */
    fun append(token: String) {
        if (isErrorToken(expression.value)) expression.value = ""
        when {
            token == "000" -> repeat(3) { if (canAppendZero(expression.value)) expression.value += "0" }
            token == "0" -> if (canAppendZero(expression.value)) expression.value += "0"
            token == "." -> {
                val n = currentNumber(expression.value)
                if (n.contains('.')) return
                expression.value += if (n.isEmpty()) "0." else "."
            }
            token.length == 1 && token[0] in '1'..'9' -> {
                val n = currentNumber(expression.value)
                expression.value = if (n == "0") expression.value.dropLast(1) + token else expression.value + token
            }
            else -> expression.value += token
        }
    }

    /**
     * Стереть символ; Infinity / Ошибка — целиком.
     */
    fun backspace() {
        val s = expression.value
        if (s.isEmpty()) return
        val hit = ErrorTokens.filter { s.endsWith(it) }.maxByOrNull { it.length }
        expression.value = if (hit != null) s.dropLast(hit.length) else s.dropLast(1)
    }

    /**
     * Посчитать и дописать в историю (не больше 20).
     */
    fun equals() {
        val src = expression.value
        if (src.isBlank()) return
        val result = engine.evaluate(src)
        tape.add(0, "$src = $result")
        while (tape.size > HISTORY_LIMIT) tape.removeAt(tape.lastIndex)
        onHistoryChange(tape.toList())
        expression.value = result
    }

    /**
     * Подставить результат строки истории в пример.
     */
    fun insertHistory(line: String) {
        val result = line.substringAfterLast(" = ").trim()
        if (result.isEmpty()) return
        val cur = expression.value
        expression.value = if (cur.isBlank() || isErrorToken(cur)) result else cur + result
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
                    "C" -> expression.value = ""
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
                CalcKey("Очистить\nисторию", Modifier.weight(1f).fillMaxHeight(), small = true) { onKey("hist") }
                CalcKey("Очистить\nпример", Modifier.weight(1f).fillMaxHeight(), small = true) { onKey("C") }
                CalcKey("Стереть\nэлемент", Modifier.weight(1f).fillMaxHeight(), small = true) { onKey("⌫") }
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
    small: Boolean = false,
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
        Text(label, fontSize = if (small) 11.sp else 16.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

/**
 * Текущее число в конце выражения (после оператора или скобки).
 */
private fun currentNumber(expr: String): String {
    val ops = setOf('+', '-', '×', '÷', '*', '/', '^', '(', ')', '√')
    var i = expr.length - 1
    while (i >= 0 && expr[i] !in ops) i--
    return expr.substring(i + 1)
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
