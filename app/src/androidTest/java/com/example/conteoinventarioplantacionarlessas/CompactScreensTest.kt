package com.example.conteoinventarioplantacionarlessas

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import com.example.conteoinventarioplantacionarlessas.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompactScreensTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(tipo: String): Triple<AppDatabase,InventarioRepository,Int> = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val dao=db.inventarioDao()
        dao.insertLotes(listOf(Lote("test","Lote de prueba")))
        dao.insertLineas(listOf(Linea("line","test","L001",2)))
        dao.insertPlantas(listOf(Planta("line","P001",1,"2020",sexo="Hembra"),Planta("line","P002",2,"2020",sexo="Hembra")))
        Triple(db,InventarioRepository(dao),dao.insertCenso(Censo(tipo=tipo)).toInt())
    }
    private fun waitTree() {compose.waitUntil(10000) {compose.onAllNodesWithText("Árbol P001 · 1/2").fetchSemanticsNodes().isNotEmpty()}}
    private fun screenshot(name: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val image=compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null),"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun floweringFitsSmallScreenAndPreviousRequiresConfirmation() {
        val (db,repo,id)=fixture("FLORACION")
        var exits=0
        compose.setContent {Box(Modifier.requiredSize(360.dp,640.dp).testTag("preview")) {
            val vm=remember {FloracionViewModel(repo,id,"line")}
            FloracionScreen(vm) {exits++}
        }}
        try {
            waitTree()
            compose.onNodeWithText("Inflorescencias").assertIsDisplayed()
            compose.onNodeWithText("Botones").assertIsDisplayed()
            compose.onNodeWithTag("guardar").assertIsDisplayed()
            screenshot("floracion-360x640")
            compose.onNodeWithText("Atrás").performClick()
            compose.onNodeWithText("¿Retroceder de esta ficha?").assertIsDisplayed()
            assertEquals(0,exits)
            compose.onNodeWithText("Seguir aquí").performClick()
            compose.onNodeWithText("Inflorescencias").performTextInput("0")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("guardar").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Árbol P002 · 2/2").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Anterior").performClick()
            compose.onNodeWithText("¿Retroceder de esta ficha?").assertIsDisplayed()
            compose.onNodeWithText("Seguir aquí").performClick()
            compose.onNodeWithText("Árbol P002 · 2/2").assertExists()
            Espresso.pressBack()
            compose.onNodeWithText("¿Retroceder de esta ficha?").assertIsDisplayed()
            assertEquals(0,exits)
        } finally {db.close()}
    }
    @Test fun backKeepsDraftAndGridShowsCompletedTree() {
        val (db,repo,id)=fixture("FRUTOS")
        val opened=mutableStateOf<Int?>(1)
        compose.setContent {Box(Modifier.requiredSize(360.dp,640.dp).testTag("preview")) {
            val position=opened.value
            if(position==null) TreeGridScreen(repo,"FRUTOS",id,"line",{opened.value=it},{})
            else {
                val vm=remember(position) {FrutosViewModel(repo,id,"line",position)}
                FrutosScreen(vm) {opened.value=null}
            }
        }}
        try {
            waitTree()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Guardado automático activo").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Cantidad de frutos").performTextInput("123")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Atrás").performClick()
            compose.onNodeWithText("Sí, retroceder").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Continuar borrador · P001").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("0/2 actualizados").assertExists()
            compose.onNodeWithText("Continuar borrador · P001").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Borrador recuperado").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("123").assertExists()
            compose.onNodeWithTag("guardar").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Árbol P002 · 2/2").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Atrás").performClick()
            compose.onNodeWithText("Sí, retroceder").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("1/2 actualizados").fetchSemanticsNodes().isNotEmpty()}
            screenshot("arboles-360x640")
            compose.onNodeWithText("P001").performClick()
            waitTree()
            compose.onNodeWithText("123").assertExists()
        } finally {runBlocking {repo.draftQueue.flush()};db.close()}
    }
    @Test fun fruitsFitsSmallScreenAndDoesNotExitAccidentally() {
        val (db,repo,id)=fixture("FRUTOS")
        var exits=0
        compose.setContent {Box(Modifier.requiredSize(360.dp,640.dp).testTag("preview")) {
            val vm=remember {FrutosViewModel(repo,id,"line")}
            FrutosScreen(vm) {exits++}
        }}
        try {
            waitTree()
            compose.onNodeWithText("Cantidad de frutos").assertIsDisplayed()
            compose.onNodeWithTag("guardar").assertIsDisplayed()
            screenshot("frutos-360x640")
            compose.onNodeWithText("Atrás").performClick()
            compose.onNodeWithText("Seguir aquí").performClick()
            assertEquals(0,exits)
            compose.onNodeWithText("Árbol P001 · 1/2").assertExists()
        } finally {db.close()}
    }
    @Test fun productionFitsAndWaitsForValidSave() {
        val (db,repo,id)=fixture("PRODUCCION")
        var exits=0
        compose.setContent {Box(Modifier.requiredSize(360.dp,640.dp).testTag("preview")) {
            val vm=remember {ProduccionViewModel(repo,id,"line")}
            ProduccionScreen(vm) {exits++}
        }}
        try {
            compose.waitUntil(10000) {compose.onAllNodesWithText("Lote de prueba").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("guardar").assertIsNotEnabled().assertIsDisplayed()
            compose.onNodeWithText("Observación del surco").assertIsDisplayed()

            compose.onNodeWithText("Anterior").performClick()
            compose.onNodeWithText("Seguir aquí").performClick()
            assertEquals(0,exits)
            listOf("Vivos","Muertos","Resiembras").forEach {compose.onNodeWithText(it).performTextInput("0")}
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Lote de prueba").assertIsDisplayed()
            screenshot("produccion-360x640")
            compose.onNodeWithTag("guardar").performClick()
            compose.waitUntil(10000) {exits==1}
            runBlocking {assertEquals("COMPLETO",db.inventarioDao().getRegistroProduccion(id,"line")!!.estado)}
        } finally {db.close()}
    }
}
