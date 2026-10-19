package com.example.conteoinventarioplantacionarlessas
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class ObservationTest {
 @Test fun notesSurviveReopenAndRenumberAndAreIncludedInSyncWithoutCompletingCensus() = runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val scope="observation-test-"+System.nanoTime()
  val db=AppDatabase.getDatabase(context,scope)
  val d=db.inventarioDao()
  d.insertLotes(listOf(Lote("test-lot","Prueba aislada")));d.insertLineas(listOf(Linea("test-line","test-lot","L001",1)))
  d.insertPlantas(listOf(Planta("test-line","P001",1,"2018")))
  val c=d.insertCenso(Censo(tipo="FLORACION")).toInt()
  val original=d.prepararFloracion(c,"test-line").single()
  val draft=BorradorFicha("FLORACION",c,"test-line",1,null,false,"","","","Rama rota\nRevisar raíz",123)
  val queue=DraftQueue(d)
  assertTrue(queue.save(draft).await());assertTrue(queue.flush())
  val saved=d.getRegistroFloracion(c,"test-line",1)!!
  assertEquals(original.id,saved.id);assertEquals("BORRADOR",saved.estado);assertNull(saved.inflorescencias)
  assertEquals(draft.observacion,saved.observacion)
  val cloud=CloudSync.rows(context,scope).single()
  assertEquals(draft.observacion,(cloud.data["datos"] as Map<*,*>)["observacion"])
  d.agregarArbolFloracion(c,"test-line",true)
  assertEquals(draft.observacion,d.getRegistroFloracion(c,"test-line",2)?.observacion)
  d.eliminarArbolCreado(c,"test-line",1)
  assertEquals(saved,d.getRegistroFloracion(c,"test-line",1))
  assertEquals(cloud.id,CloudSync.rows(context,scope).single().id)
  assertTrue(queue.save(draft.copy(observacion="")).await())
  assertEquals("",(CloudSync.rows(context,scope).single().data["datos"] as Map<*,*>)["observacion"])
  // Reopen an independent connection to the same file to prove disk persistence.
  assertTrue(queue.save(draft).await())
  val reopened=Room.databaseBuilder(context,AppDatabase::class.java,db.openHelper.databaseName!!).addMigrations(AppDatabase.MIGRATION_5_6).build()
  try {assertEquals(draft.observacion,reopened.inventarioDao().getRegistroFloracion(c,"test-line",1)?.observacion)} finally {reopened.close()}
 }
 @Test fun migrationFromFivePreservesExistingCounts() = runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val name="migration-observations-"+System.nanoTime()+".db"
  val raw=context.openOrCreateDatabase(name,0,null)
  val schema=JSONArray(instrumentation.context.assets.open("schema-v5.json").bufferedReader().readText())
  for(i in 0 until schema.length()) raw.execSQL(schema.getString(i))
  raw.execSQL("INSERT INTO registro_floracion(id,censoId,loteId,siembra,lineaId,arbolCodigo,posicion,sexo,inflorescencias,botones,estado,muerto,capturado,verificadoEn) VALUES(7,1,'l','2018','s','P001',1,'Hembra',73,4,'COMPLETO',0,1,123)")
  raw.version=5;raw.close()
  val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_5_6).build()
  try {
   val saved=db.inventarioDao().getRegistroFloracion(1,"s",1)!!
   assertEquals(7,saved.id);assertEquals(73,saved.inflorescencias);assertEquals(4,saved.botones);assertEquals("COMPLETO",saved.estado);assertEquals("",saved.observacion)
  } finally {db.close();context.deleteDatabase(name)}
 }
}
