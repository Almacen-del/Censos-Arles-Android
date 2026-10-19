package com.example.conteoinventarioplantacionarlessas
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreeInsertionTest {
 @Test fun prependAndAppendPreserveCapturesDraftsAndHistoricalCensus() = runBlocking {
  val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
  try {
   val d=db.inventarioDao()
   d.insertLotes(listOf(Lote("lot","Lote prueba")))
   d.insertLineas(listOf(Linea("line","lot","L001",3),Linea("other","lot","L002",1)))
   d.insertPlantas((1..3).map {Planta("line", "P00$it",it,"2018",sexo="Hembra")}+Planta("other","P001",1,null))
   val historical=d.insertCenso(Censo(tipo="FLORACION")).toInt()
   val old=d.prepararFloracion(historical,"line")
   val census=d.insertCenso(Censo(tipo="FLORACION")).toInt()
   val rows=d.prepararFloracion(census,"line")
   val saved=rows[0].copy(capturado=true,estado="COMPLETO",inflorescencias=12,botones=3,verificadoEn=100)
   d.guardarFloracionConAvance(saved,1)
   d.guardarBorrador(BorradorFicha("FLORACION",census,"line",1,"Hembra",false,"13","4","","nota",200))
   d.guardarBorrador(BorradorFicha("FLORACION",census,"line",2,"Macho",false,"21","8","","otro",300))
   assertEquals(1,d.agregarArbolFloracion(census,"line",true))
   val inserted=d.listarFloracion(census,"line")
   assertEquals(4,inserted.size)
   assertEquals(saved.copy(arbolCodigo="P002",posicion=2),inserted[1])
   assertEquals("13",d.leerBorrador("FLORACION",census,"line",2)?.a)
   assertEquals("21",d.leerBorrador("FLORACION",census,"line",3)?.a)
   assertNull(d.leerBorrador("FLORACION",census,"line",1))
   assertFalse(inserted[0].capturado);assertNull(inserted[0].sexo);assertNull(inserted[0].siembra)
   assertEquals(old,d.listarFloracion(historical,"line"))
   assertEquals(4,d.getLineaById("line")?.cantidadArboles)
   assertEquals(1,d.getPlantas("other").size)
   assertEquals(5,d.agregarArbolFloracion(census,"line",false))
   assertEquals(inserted,d.listarFloracion(census,"line").take(4))
   assertEquals("P005",d.listarFloracion(census,"line").last().arbolCodigo)
   assertEquals(5,d.getPlantas("line").size)
  } finally {db.close()}
 }
 @Test fun invalidCodesAbortWithoutPartialChanges() = runBlocking {
  val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
  try {
   val d=db.inventarioDao();d.insertLotes(listOf(Lote("l","test")));d.insertLineas(listOf(Linea("s","l","L001",1)))
   d.insertPlantas(listOf(Planta("s","SIN-CODIGO",1,null)))
   val c=d.insertCenso(Censo(tipo="FLORACION")).toInt()
   try {d.agregarArbolFloracion(c,"s",true);fail("Should reject invalid code")} catch(_:IllegalArgumentException) {}
   assertTrue(d.listarFloracion(c,"s").isEmpty())
   assertEquals("SIN-CODIGO",d.getPlantas("s").single().codigo)
   assertEquals(1,d.getLineaById("s")?.cantidadArboles)
  } finally {db.close()}
 }
 @Test fun deleteCreatedTreeProtectsCapturedAndOriginalTrees() = runBlocking {
  val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
  try {
   val d=db.inventarioDao();d.insertLotes(listOf(Lote("l","test")));d.insertLineas(listOf(Linea("s","l","L001",1)))
   d.insertPlantas(listOf(Planta("s","P001",1,"2018",filasOrigen="6")))
   val c=d.insertCenso(Censo(tipo="FLORACION")).toInt()
   val original=d.prepararFloracion(c,"s").single()
   try {d.eliminarArbolCreado(c,"s",1);fail("Original must be protected")} catch(_:IllegalArgumentException) {}
   val pos=d.agregarArbolFloracion(c,"s",false)
   d.guardarBorrador(BorradorFicha("FLORACION",c,"s",pos,null,false,"2","3","","",1))
   d.eliminarArbolCreado(c,"s",pos)
   assertEquals(listOf(original),d.listarFloracion(c,"s"));assertEquals(1,d.getLineaById("s")?.cantidadArboles)
   assertNull(d.leerBorrador("FLORACION",c,"s",pos))
   val next=d.agregarArbolFloracion(c,"s",false)
   val saved=d.getRegistroFloracion(c,"s",next)!!.copy(capturado=true)
   d.insertRegistroFloracion(saved)
   try {d.eliminarArbolCreado(c,"s",next);fail("Capture must be protected")} catch(_:IllegalArgumentException) {}
   assertEquals(saved,d.getRegistroFloracion(c,"s",next));assertEquals(2,d.getPlantas("s").size)
  } finally {db.close()}
 }
 @Test fun deletingPrependedTreeRestoresNumbersCountsAndDrafts() = runBlocking {
  val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
  try {
   val d=db.inventarioDao();d.insertLotes(listOf(Lote("l","test")));d.insertLineas(listOf(Linea("s","l","L001",2)))
   d.insertPlantas(listOf(Planta("s","P001",1,"2018",filasOrigen="6"),Planta("s","P004",2,"2018",filasOrigen="7")))
   val c=d.insertCenso(Censo(tipo="FLORACION")).toInt()
   d.prepararFloracion(c,"s")
   val saved=d.getRegistroFloracion(c,"s",1)!!.copy(capturado=true,estado="COMPLETO",sexo="Hembra",inflorescencias=8,botones=9)
   d.insertRegistroFloracion(saved)
   val before=d.listarFloracion(c,"s")
   val draft=BorradorFicha("FLORACION",c,"s",2,"Hembra",false,"12","13","","",99)
   d.guardarBorrador(draft)
   d.agregarArbolFloracion(c,"s",true)
   d.eliminarArbolCreado(c,"s",1)
   assertEquals(before,d.listarFloracion(c,"s"))
   assertEquals(draft,d.leerBorrador("FLORACION",c,"s",2))
   assertNull(d.leerBorrador("FLORACION",c,"s",3))
   assertEquals(listOf("P001","P004"),d.getPlantas("s").map {it.codigo})
   assertEquals(3,d.agregarArbolFloracion(c,"s",false))
   assertEquals("P005",d.listarFloracion(c,"s").last().arbolCodigo)
  } finally {db.close()}
 }
}
