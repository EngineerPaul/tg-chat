package ru.daystream.calc.shell

/**
 * Единственный выход из модуля калькулятора в приложение.
 * Чат этот модуль не импортирует.
 */
fun interface ActivationCallback {
    /**
     * Человек нажал «Ввести ключ» или тусклую функцию.
     */
    fun onActivationRequested()
}
