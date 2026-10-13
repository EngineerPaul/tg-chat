package ru.daystream.calc.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
    val tape = remember { mutableStateListOf<String>().apply { addAll(history) } }

    /**
     * Добавить символ в выражение.
     * @param token цифра или оператор
     */
    fun append(token: String) {
        expression.value += token
    }

    /**
     * Посчитать и дописать в историю.
     */
    fun equals() {
        val src = expression.value
        if (src.isBlank()) return
        val result = engine.evaluate(src)
        tape.add(0, "$src = $result")
        onHistoryChange(tape.toList())
        expression.value = result
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
                Text(line, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(4.dp))
            }
        }
        Text(
            expression.value.ifBlank { "0" },
            fontSize = 36.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Pad(
            dimmed = listOf("log", "ln", "sin", "cos", "tan"),
            onDimmed = { onActivate.onActivationRequested() },
            onKey = { key ->
                when (key) {
                    "C" -> expression.value = ""
                    "⌫" -> if (expression.value.isNotEmpty()) expression.value = expression.value.dropLast(1)
                    "=" -> equals()
                    "√" -> append("√")
                    "x^y" -> append("^")
                    else -> append(key)
                }
            },
        )
    }
}

/**
 * Клавиатура калькулятора: рабочие и тусклые кнопки.
 *
 * @param dimmed подписи нерабочих функций
 * @param onDimmed нажатие тусклой кнопки
 * @param onKey нажатие рабочей кнопки
 */
@Composable
private fun Pad(dimmed: List<String>, onDimmed: () -> Unit, onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("log", "ln", "sin", "cos", "tan"),
        listOf("(", ")", "√", "x^y", "C"),
        listOf("7", "8", "9", "÷", "⌫"),
        listOf("4", "5", "6", "×", ""),
        listOf("1", "2", "3", "-", ""),
        listOf("0", ".", "=", "+", ""),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Box(Modifier.weight(1f))
                    } else {
                        val dead = label in dimmed
                        Button(
                            onClick = { if (dead) onDimmed() else onKey(label) },
                            modifier = Modifier.weight(1f).alpha(if (dead) 0.35f else 1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (label == "=") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) { Text(label, fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}
