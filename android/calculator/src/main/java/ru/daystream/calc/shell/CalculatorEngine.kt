package ru.daystream.calc.shell

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

internal const val MaxIntegerDigits = 12
internal const val MaxFractionDigits = 6
private const val RoundScale = 12

/**
 * Рабочий калькулятор: арифметика, скобки, корень, степень.
 */
class CalculatorEngine {
    /**
     * Посчитать выражение. Поддерживает + - * / , скобки, √x и x^y.
     * @param raw строка с выражением
     * @return текст результата или сообщение об ошибке
     */
    /**
     * @return результат, «Ошибка» если число не умещается, null если выражение разобрать нельзя
     */
    fun evaluate(raw: String): String? {
        return try {
            val value = Parser(raw.replace("×", "*").replace("÷", "/").replace("√", "s").removeSuffix("...")).parse()
            formatCalcNumber(value)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Целое как есть (не больше 12 цифр); иначе не больше 6 знаков после запятой, «...» если дальше ещё есть цифры.
     */
    internal fun formatCalcNumber(value: Double): String {
        if (!value.isFinite()) return "Ошибка"
        val plain = BigDecimal(value.toString())
            .setScale(RoundScale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        val abs = plain.removePrefix("-")
        val intPart = abs.substringBefore('.')
        if (intPart.length > MaxIntegerDigits) return "Ошибка"
        val dot = plain.indexOf('.')
        if (dot < 0) return plain
        val fracLen = plain.length - dot - 1
        if (fracLen <= MaxFractionDigits) return plain
        return plain.substring(0, dot + 1 + MaxFractionDigits) + "..."
    }

    /**
     * Рекурсивный разбор выражения.
     */
    private class Parser(private val src: String) {
        private var i = 0

        /**
         * Разобрать всё выражение до конца строки.
         */
        fun parse(): Double {
            val v = expr()
            skip()
            if (i < src.length) error("хвост")
            return v
        }

        /**
         * Слагаемые.
         */
        private fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                v = when (peek()) {
                    '+' -> { i++; v + term() }
                    '-' -> { i++; v - term() }
                    else -> return v
                }
            }
        }

        /**
         * Множители.
         */
        private fun term(): Double {
            var v = power()
            while (true) {
                skip()
                v = when (peek()) {
                    '*' -> { i++; v * power() }
                    '/' -> { i++; v / power() }
                    else -> return v
                }
            }
        }

        /**
         * Степень справа налево.
         */
        private fun power(): Double {
            var v = unary()
            skip()
            if (peek() == '^') {
                i++
                v = v.pow(power())
            }
            return v
        }

        /**
         * Унарный минус и корень.
         */
        private fun unary(): Double {
            skip()
            return when (peek()) {
                '-' -> { i++; -unary() }
                '+' -> { i++; unary() }
                's' -> { i++; sqrt(unary()) }
                else -> primary()
            }
        }

        /**
         * Число или скобки.
         */
        private fun primary(): Double {
            skip()
            if (peek() == '(') {
                i++
                val v = expr()
                skip()
                if (peek() == ')') i++
                return v
            }
            val start = i
            while (peek().isDigit() || peek() == '.') i++
            if (start == i) error("число")
            return src.substring(start, i).toDouble()
        }

        /**
         * Пропустить пробелы.
         */
        private fun skip() {
            while (peek() == ' ') i++
        }

        /**
         * Символ под курсором или конец.
         */
        private fun peek(): Char = if (i < src.length) src[i] else 0.toChar()
    }
}
