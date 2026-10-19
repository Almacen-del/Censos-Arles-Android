package com.example.conteoinventarioplantacionarlessas

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LineWorkflowTest {
    @Test fun rangePersistsAndIsScopedAndIdempotent() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val name="range-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            var dao=db.inventarioDao()
            dao.insertLotes(listOf(Lote("a","A"),Lote("b","B")))
            dao.insertLineas((1..5).map {Linea("a$it","a","L00$it",2)}+Linea("b1","b","L001",2))
            val censo=dao.insertCenso(Censo(tipo="FRUTOS")).toInt()
            dao.bloquearLineas(censo,"a",listOf("a2","a3","a4"),"Juan")
            dao.bloquearLineas(censo,"a",listOf("a3","a4"),"Juan")
            assertEquals(3,dao.getLineasOmitidas(censo).first().size)
            try {dao.bloquearLineas(censo,"a",listOf("a5","b1"),"Pedro");fail("Debe rechazar otro lote")}
            catch(_: IllegalArgumentException) {}
            assertNull(dao.bloqueo(censo,"a5"))
            db.close();db=Room.databaseBuilder(context,AppDatabase::class.java,name).build();dao=db.inventarioDao()
            assertEquals("Juan",dao.bloqueo(censo,"a3")!!.responsable)
            assertNull(dao.bloqueo(censo,"a1"))
            assertTrue(dao.getLineasOmitidas(censo+1).first().isEmpty())
            dao.deleteLineaOmitida(censo,"a3")
            assertEquals(2,dao.getLineasOmitidas(censo).first().size)
            val repo=InventarioRepository(dao)
            assertEquals(censo,repo.createOrGetCenso("FRUTOS").id)
            val prod=repo.createOrGetCenso("PRODUCCION")
            assertEquals(prod.id,repo.createOrGetCenso("PRODUCCION").id)
        } finally {db.close();context.deleteDatabase(name)}
    }

    @Test fun greenRequiresEveryValidTreeAndUpdatesAfterCorrection() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.inventarioDao()
            dao.insertLotes(listOf(Lote("a","A")))
            dao.insertLineas(listOf(Linea("l","a","L001",2)))
            dao.insertPlantas(listOf(Planta("l","P001",1,null),Planta("l","P002",2,null)))
            val c=dao.insertCenso(Censo(tipo="FRUTOS")).toInt()
            val rows=dao.prepararFrutos(c,"l")
            assertFalse(dao.avanceLineas(c,"a","FRUTOS").first().single().terminada)
            dao.insertRegistroFrutos(rows[0].copy(sexo="Hembra",frutos=0,estado="COMPLETO",capturado=true))
            dao.insertRegistroFrutos(rows[1].copy(estado="PENDIENTE_SEXO",capturado=true))
            assertEquals(1,dao.avanceLineas(c,"a","FRUTOS").first().single().completos)
            dao.insertRegistroFrutos(rows[1].copy(muerto=true,estado="COMPLETO",capturado=true))
            assertTrue(dao.avanceLineas(c,"a","FRUTOS").first().single().terminada)
            dao.insertRegistroFrutos(rows[0].copy(sexo="Hembra",frutos=null,estado="COMPLETO",capturado=true))
            assertFalse(dao.avanceLineas(c,"a","FRUTOS").first().single().terminada)
            val f=dao.insertCenso(Censo(tipo="FLORACION")).toInt()
            dao.prepararFloracion(f,"l").forEach {dao.insertRegistroFloracion(it.copy(muerto=true,capturado=true,estado="COMPLETO"))}
            assertTrue(dao.avanceLineas(f,"a","FLORACION").first().single().terminada)
            val p=dao.insertCenso(Censo(tipo="PRODUCCION")).toInt()
            val record=RegistroProduccion(censoId=p,loteId="a",lineaId="l",vivos=0,muertos=0,resiembras=null,observacion=null,estado="COMPLETO")
            val id=dao.insertRegistroProduccion(record).toInt()
            assertFalse(dao.avanceLineas(p,"a","PRODUCCION").first().single().terminada)
            dao.insertRegistroProduccion(record.copy(id=id,resiembras=0))
            assertTrue(dao.avanceLineas(p,"a","PRODUCCION").first().single().terminada)
        } finally {db.close()}
    }
}
