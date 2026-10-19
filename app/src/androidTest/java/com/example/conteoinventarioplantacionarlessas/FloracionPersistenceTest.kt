package com.example.conteoinventarioplantacionarlessas

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FloracionPersistenceTest {
    @Test fun catalogPersistenceCorrectionsAndCensusSeparation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-floracion-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
        try {
            var dao = db.inventarioDao()
            dao.insertLotes(listOf(Lote("test", "Solo prueba")))
            dao.insertLineas(listOf(Linea("line", "test", "L007", 2)))
            dao.insertPlantas(listOf(Planta("line", "P003", 1, "2018"), Planta("line", "P009", 2, "2019")))
            val censo = dao.insertCenso(Censo(tipo = "FLORACION")).toInt()
            val rows = dao.prepararFloracion(censo, "line")
            assertEquals(listOf("P003", "P009"), rows.map { it.arbolCodigo })
            assertEquals(listOf("2018", "2019"), rows.map { it.siembra })
            assertEquals(rows, dao.prepararFloracion(censo, "line"))
            val saved = rows[0].copy(sexo = "Hembra", inflorescencias = 20, botones = 4, estado = "COMPLETO")
            dao.guardarFloracionConAvance(saved, 2)
            dao.guardarFloracionConAvance(saved.copy(botones = 5), 2)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
            dao = db.inventarioDao()
            assertEquals(2, dao.listarFloracion(censo, "line").size)
            assertEquals(5, dao.listarFloracion(censo, "line")[0].botones)
            assertEquals(2, dao.getAvanceFloracion(censo, "line")!!.posicion)
            val nuevo = dao.insertCenso(Censo(tipo = "FLORACION")).toInt()
            assertTrue(dao.prepararFloracion(nuevo, "line").all { it.inflorescencias == null })
            assertEquals(20, dao.listarFloracion(censo, "line")[0].inflorescencias)
            dao.insertLineas(listOf(Linea("missing", "test", "L008", null)))
            try { dao.prepararFloracion(censo, "missing"); fail("No debe inventar árboles") }
            catch (_: IllegalArgumentException) { }
            assertTrue(dao.listarFloracion(censo, "missing").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun additiveMigrationPreservesOldRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-migration-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val id = db.inventarioDao().insertCenso(Censo(tipo = "FLORACION")).toInt()
            val record = RegistroFloracion(censoId = id, loteId = "old", siembra = null, lineaId = "old-line",
                arbolCodigo = "A1", posicion = 1, sexo = "Hembra", inflorescencias = 12, botones = 3)
            db.inventarioDao().insertRegistroFloracion(record)
            db.close()
            context.openOrCreateDatabase(name, 0, null).use { sql ->
                // Reproduce the v1 schema; the new tables did not exist in v1.
                sql.execSQL("ALTER TABLE lineas_omitidas DROP COLUMN responsable")
                for (table in listOf("registro_frutos", "registro_floracion", "registro_produccion", "lineas_omitidas")) sql.execSQL("DROP INDEX IF EXISTS index_${table}_censoId_lineaId")
                sql.execSQL("DROP TABLE catalogo_importado")
                sql.execSQL("DROP TABLE borradores_ficha")
                for (table in listOf("registro_frutos", "registro_floracion")) {
                    for (column in listOf("muerto", "capturado", "verificadoEn")) sql.execSQL("ALTER TABLE $table DROP COLUMN $column")
                }
                sql.execSQL("DROP TABLE plantas")
                sql.execSQL("DROP TABLE avance_floracion")
                sql.execSQL("DROP TABLE room_master_table")
                sql.version = 1
            }
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
            val old = db.inventarioDao().listarFloracion(id, "old-line").single()
            assertEquals("A1", old.arbolCodigo)
            assertEquals(12, old.inflorescencias)
            assertTrue(db.inventarioDao().getPlantas("old-line").isEmpty())
            db.inventarioDao().guardarAvanceFloracion(AvanceFloracion(id, "old-line", 1))
            assertEquals(1, db.inventarioDao().getAvanceFloracion(id, "old-line")!!.posicion)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
