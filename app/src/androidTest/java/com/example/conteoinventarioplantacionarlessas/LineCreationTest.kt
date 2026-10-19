package com.example.conteoinventarioplantacionarlessas

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LineCreationTest {
    @Test fun newLineSurvivesReopeningAndStartsWithUncountedTrees() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val name = "new-line-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            var dao = db.inventarioDao()
            dao.insertLotes(listOf(Lote("a", "A"), Lote("b", "B")))
            dao.insertLineas(listOf(Linea("original", "a", "L001", 1)))
            dao.insertPlantas(listOf(Planta("original", "P001", 1, "2018", sexo = "Hembra")))
            val census = dao.insertCenso(Censo(tipo = "FLORACION")).toInt()
            val original = dao.prepararFloracion(census, "original").single().copy(
                capturado = true, estado = "COMPLETO", inflorescencias = 12, botones = 3)
            dao.insertRegistroFloracion(original)
            val line = dao.agregarLinea("a", "2", 25)
            assertEquals("L002", line.numero)
            assertEquals(25, dao.getPlantas(line.id).size)
            assertTrue(dao.listarFloracion(census, line.id).isEmpty())
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            dao = db.inventarioDao()
            assertEquals(line, dao.getLineaById(line.id))
            val rows = dao.prepararFloracion(census, line.id)
            assertEquals((1..25).map { "P" + it.toString().padStart(3, '0') }, rows.map { it.arbolCodigo })
            assertTrue(rows.all { !it.capturado && it.estado == "BORRADOR" && it.sexo == null && it.siembra == null && it.inflorescencias == null && it.botones == null })
            assertEquals(rows, dao.prepararFloracion(census, line.id))
            assertEquals(original, dao.listarFloracion(census, "original").single())
            assertEquals(0, dao.avanceLineas(census, "a", "FLORACION").first().single { it.lineaId == line.id }.completos)
            assertNotEquals(line.id, dao.agregarLinea("b", "L002", 3).id)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun duplicateAndInvalidLinesNeverReplaceDataOrLeavePartialPlants() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.inventarioDao()
            dao.insertLotes(listOf(Lote("a", "A")))
            dao.agregarLinea("a", "1", 2)
            for ((number, count) in listOf("l001" to 4, "0001" to 4, "0" to 2, "2" to 0, "2" to -1, "2" to 10000, "abc" to 2)) {
                try { dao.agregarLinea("a", number, count); fail("Must reject invalid or duplicate line") }
                catch (_: IllegalArgumentException) { }
            }
            try { dao.agregarLinea("missing", "2", 2); fail("Must reject missing lot") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, dao.listarLineasDelLote("a").size)
            assertEquals(2, dao.getPlantas("a_L001").size)
            assertTrue(dao.getPlantas("a_L002").isEmpty())
            val results = coroutineScope {
                listOf(async { runCatching { dao.agregarLinea("a", "2", 3) }.isSuccess },
                    async { runCatching { dao.agregarLinea("a", "002", 5) }.isSuccess }).map { it.await() }
            }
            assertEquals(1, results.count { it })
            val second = dao.getLineaById("a_L002")!!
            assertEquals(second.cantidadArboles, dao.getPlantas(second.id).size)
        } finally { db.close() }
    }
}
