package com.example.conteoinventarioplantacionarlessas

import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CatalogImportTest {
    @Test fun offlineCatalogCountsCorrectionsAndDeadTreePersist() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val scope = "catalog-test-${UUID.randomUUID()}"
        val db = AppDatabase.getDatabase(context,scope)
        CatalogImporter.ensureLoaded(context,db)
        val dao=db.inventarioDao()
        fun count(sql: String)=db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(22,count("SELECT count(*) FROM lotes"))
        assertEquals(1383,count("SELECT count(*) FROM lineas"))
        assertEquals(27672,count("SELECT count(*) FROM plantas"))
        assertEquals(15326,count("SELECT count(*) FROM plantas WHERE sexo='Hembra'"))
        assertEquals(1303,count("SELECT count(*) FROM plantas WHERE sexo='Macho'"))
        assertEquals(11043,count("SELECT count(*) FROM plantas WHERE sexo IS NULL"))
        assertEquals(0,count("SELECT count(*) FROM plantas WHERE muerto=1"))
        assertEquals(22,count("SELECT count(*) FROM plantas WHERE filasOrigen LIKE '%,%'"))
        assertEquals(0,count("SELECT count(*) FROM lineas l WHERE cantidadArboles != (SELECT count(*) FROM plantas p WHERE p.lineaId=l.id)"))
        val line="CAT-LOTE-01_L001"
        val catalog = dao.getPlantas(line)
        val censo=dao.insertCenso(Censo(tipo="FRUTOS")).toInt()
        val rows=dao.prepararFrutos(censo,line)
        assertEquals(catalog.map { it.codigo },rows.map { it.arbolCodigo })
        assertEquals(catalog.map { it.sexo },rows.map { it.sexo })
        assertTrue(CloudSync.rows(context,scope).isEmpty()) // Opening cards is not a field observation.
        val record=rows.first().copy(muerto=true,capturado=true,estado="COMPLETO",verificadoEn=123L)
        dao.guardarFrutosYPlanta(record)
        val pending=CloudSync.rows(context,scope).single()
        assertEquals(true,(pending.data["datos"] as Map<*,*>)["muerto"])
        assertTrue(dao.getPlanta(line,record.arbolCodigo)!!.muerto)
        CatalogImporter.ensureLoaded(context,db)
        assertTrue(dao.getPlanta(line,record.arbolCodigo)!!.muerto)
        assertEquals(27672,count("SELECT count(*) FROM plantas"))
        val floracion=dao.insertCenso(Censo(tipo="FLORACION")).toInt()
        assertTrue(dao.prepararFloracion(floracion,line).first().muerto)
        assertEquals(1,CloudSync.rows(context,scope).size)
        dao.guardarFrutosYPlanta(record.copy(muerto=false,sexo="Hembra",frutos=0))
        assertFalse(dao.getPlanta(line,record.arbolCodigo)!!.muerto)
        assertEquals("Hembra",dao.getPlanta(line,record.arbolCodigo)!!.sexo)
    }
}
