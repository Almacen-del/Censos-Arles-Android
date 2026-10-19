package com.example.conteoinventarioplantacionarlessas

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import com.example.conteoinventarioplantacionarlessas.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AddLineScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun dialogCreatesOnlyTheRequestedLineAndReportsDuplicates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.inventarioDao()
        val lot = Lote("a", "Lote prueba")
        runBlocking { dao.insertLotes(listOf(lot)); dao.agregarLinea(lot.id, "1", 2) }
        compose.activity.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 27) { compose.activity.setTurnScreenOn(true); compose.activity.setShowWhenLocked(true) }
        }
        var created: Linea? = null
        compose.setContent {
            var show by remember { mutableStateOf(true) }
            if (show) AddLineDialog(lot, emptyList(), onDismiss = { show = false },
                onAdd = dao::agregarLinea, onCreated = { created = it; show = false })
        }
        try {
            compose.onNodeWithText("Cantidad de árboles").performTextInput("4")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Crear línea").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("La línea L001 ya existe", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Número de línea").performTextReplacement("2")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Crear línea").performClick()
            compose.waitUntil(10000) { created != null }
            runBlocking {
                assertEquals("L002", created!!.numero)
                assertEquals(4, dao.getPlantas(created!!.id).size)
                assertEquals(2, dao.getPlantas("a_L001").size)
            }
        } finally { db.close() }
    }
}
