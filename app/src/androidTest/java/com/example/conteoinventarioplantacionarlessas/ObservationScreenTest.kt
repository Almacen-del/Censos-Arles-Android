package com.example.conteoinventarioplantacionarlessas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
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
class ObservationScreenTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun noteDialogSavesAndDoesNotLeakToNextTree() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  check(context.packageName.endsWith(".verification"))
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  val repo=InventarioRepository(db.inventarioDao())
  val c=runBlocking {
   val d=db.inventarioDao();d.insertLotes(listOf(Lote("l","Lote prueba")));d.insertLineas(listOf(Linea("s","l","L001",2)))
   d.insertPlantas((1..2).map {Planta("s","P00$it",it,"2018",sexo="Hembra")})
   d.insertCenso(Censo(tipo="FLORACION")).toInt()
  }
  compose.activity.runOnUiThread {
   compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
   if(android.os.Build.VERSION.SDK_INT>=27) {compose.activity.setTurnScreenOn(true);compose.activity.setShowWhenLocked(true)}
  }
  compose.setContent {Box(Modifier.requiredSize(360.dp,640.dp)) {val vm=remember {FloracionViewModel(repo,c,"s",1)};FloracionScreen(vm) {}}}
  try {
   compose.waitUntil(10000) {runCatching {compose.onAllNodesWithText("Guardado automático activo").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
   compose.onNodeWithText("Agregar observación").assertIsDisplayed().performClick()
   compose.onNodeWithTag("observacion").performTextInput("Rama rota")
   Espresso.closeSoftKeyboard()
   compose.onNodeWithText("Listo").performClick()
   runBlocking {assertTrue(repo.draftQueue.flush());assertEquals("Rama rota",db.inventarioDao().getRegistroFloracion(c,"s",1)?.observacion)}
   compose.onNodeWithText("Inflorescencias").performTextInput("0")
   Espresso.closeSoftKeyboard()
   compose.onNodeWithTag("guardar").performClick()
   compose.waitUntil(10000) {compose.onAllNodesWithText("Árbol P002",substring=true).fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Rama rota").assertDoesNotExist()
   runBlocking {val row=db.inventarioDao().getRegistroFloracion(c,"s",1)!!;assertEquals("Rama rota",row.observacion);assertEquals("COMPLETO",row.estado)}
  } finally {runBlocking {repo.draftQueue.flush()};db.close()}
 }
}
