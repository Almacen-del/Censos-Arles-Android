package com.example.conteoinventarioplantacionarlessas.data

object LineCreation {
    fun number(value: String): Int? = value.trim().uppercase().removePrefix("L")
        .takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toIntOrNull()

    fun code(value: String): String {
        val number = number(value)
        require(number != null && number in 1..999) { "Indica un número de línea entre 1 y 999." }
        return "L" + number.toString().padStart(3, '0')
    }

    fun validateCount(count: Int) {
        require(count in 1..9999) { "Indica una cantidad de árboles entre 1 y 9999." }
    }
}
