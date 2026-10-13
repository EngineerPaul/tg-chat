package ru.daystream.calc.shell

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Рабочий калькулятор: арифметика, скобки, корень, степень.
 */
class CalculatorEngine {
    /**
     * Посчитать выражение. Поддерживает + - * / , скобки, √x и x^y.
     * @param raw строка с выражением
     * @return текст результата или сообщение об ошибке
     */
    fun evaluate(raw: String): String {
        return try {
            val value = Parser(raw.replace("×", "*").replace("÷", "/").replace("√", "s")).parse()
            if (value % 1.0 == 0.0 && value.isFinite()) value.toLong().toString()
            else value.toString()
        } catch (_: Exception) {
            "Ошибка"
        }
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
