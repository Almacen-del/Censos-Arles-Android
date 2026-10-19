package com.example.conteoinventarioplantacionarlessas.ui

import android.content.Context

object CountDirection {
    private fun key(censo: Int, linea: String) = "FLORACION:$censo:$linea"
    fun reversed(context: Context, censo: Int, linea: String) =
        context.getSharedPreferences("count_direction", Context.MODE_PRIVATE).getBoolean(key(censo,linea),false)
    fun save(context: Context, censo: Int, linea: String, reversed: Boolean) {
        context.getSharedPreferences("count_direction", Context.MODE_PRIVATE).edit().putBoolean(key(censo,linea),reversed).apply()
    }
}

fun <T> countingOrder(items: List<T>, reversed: Boolean, position: (T)->Int): List<T> =
    if(reversed) items.sortedByDescending(position) else items.sortedBy(position)
