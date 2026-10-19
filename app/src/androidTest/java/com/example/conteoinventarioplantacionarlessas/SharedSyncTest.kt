package com.example.conteoinventarioplantacionarlessas

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SharedSyncTest {
    @Test fun emptyLegacySlotDoesNotBlockLineButRealUnverifiedCountOnlyBlocksItsTree()=runBlocking {
        val room=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=room.inventarioDao();dao.insertLotes(listOf(Lote("lot","Lote")));dao.agregarLinea("lot","1",5)
            val db=room.openHelper.writableDatabase;val c=SharedSync.bind(db,"campaign")
            val valid=(1..5).map { n -> remote("P00$n",0,"valid-$n").put("source_id","updated").also {
                it.getJSONObject("datos").put("catalogSignature","sig").put("botones",JSONObject.NULL).put("observacion","")
            } }
            valid.last().getJSONObject("datos").put("muerto",true).put("inflorescencias",JSONObject.NULL).put("sexo",JSONObject.NULL)
            val legacy=remote(id="old").put("source_id","legacy")
            legacy.getJSONObject("datos").put("sexo",JSONObject.NULL).put("inflorescencias",JSONObject.NULL).put("botones",JSONObject.NULL).put("observacion","").put("estado","PENDIENTE_SEXO")
            val issues=mutableListOf<String>()
            val selected=SharedSync.eligibleRecords(valid+legacy,emptySet(),mapOf("updated/lot_L001" to "sig"),issues::add)
            assertEquals(5,selected.size);assertTrue(issues.isEmpty())
            assertEquals(5,SharedSync.applyRecords(db,c,selected,emptySet()))
            assertEquals(5,dao.avanceLineas(c,"lot","FLORACION").first().single().completos)
            assertTrue(dao.listarFloracion(c,"lot_L001").last().muerto)
            legacy.getJSONObject("datos").put("observacion","Rama dañada")
            val protected=SharedSync.eligibleRecords(valid+legacy,emptySet(),mapOf("updated/lot_L001" to "sig"),issues::add)
            assertEquals(4,protected.size);assertTrue(protected.none {it.getJSONObject("datos").getString("arbolCodigo")=="P001"})
            assertEquals(1,issues.size)
        } finally {room.close()}
    }
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".verification")) }
    private fun remote(code:String="P001",amount:Int=12,id:String="remote-1")=JSONObject().put("id",id).put("worker_uid","other")
        .put("datos",JSONObject().put("loteId","lot").put("lineaId","lot_L001").put("arbolCodigo",code).put("posicion",code.removePrefix("P").toInt())
            .put("siembra",JSONObject.NULL).put("sexo","Hembra").put("inflorescencias",amount).put("botones",2)
            .put("estado","COMPLETO").put("muerto",false).put("observacion","Rama dañada").put("verificadoEn",100L))

    @Test fun twoDevicesReceiveCatalogAndRecordsWithoutEchoAndKeepPendingSlots()=runBlocking {
        val a=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val scope="sync-test-${UUID.randomUUID()}"
        val b=AppDatabase.getDatabase(context,scope)
        try {
            val dao=a.inventarioDao();dao.insertLotes(listOf(Lote("lot","Lote de prueba")))
            val line=dao.agregarLinea("lot","1",4)
            val target=b.openHelper.writableDatabase;SharedSync.setup(target)
            val censo=SharedSync.bind(target,"campaign-a")
            val catalog=SharedSync.snapshot(a.openHelper.writableDatabase,line.id)!!
            assertTrue(SharedSync.applyCatalog(target,catalog,null))
            assertEquals(1,SharedSync.applyRecords(target,censo,listOf(remote()),emptySet()))
            val first=b.inventarioDao().listarFloracion(censo,line.id)
            assertEquals(4,first.size)
            assertEquals(1,first.count {it.capturado})
            assertEquals("remote-1",first.first().compartidoPor)
            assertEquals(1,b.inventarioDao().avanceLineas(censo,"lot","FLORACION").first().single().completos)
            assertTrue(CloudSync.rows(context,scope).isEmpty())
            SharedSync.applyRecords(target,censo,listOf(remote()),emptySet())
            assertEquals(first,b.inventarioDao().listarFloracion(censo,line.id))
            SharedSync.applyRecords(target,censo,listOf(remote(amount=20)),emptySet())
            assertEquals(20,b.inventarioDao().listarFloracion(censo,line.id).first().inflorescencias)
            assertEquals(first.first().id,b.inventarioDao().listarFloracion(censo,line.id).first().id)
            try { b.inventarioDao().guardarFloracionConAvance(first.first().copy(inflorescencias=99),1);fail("Shared count is read only") } catch(_:IllegalArgumentException) {}
            val second=b.inventarioDao().listarFloracion(censo,line.id)[1].copy(capturado=true,sexo="Hembra",inflorescencias=7,botones=2,estado="COMPLETO")
            b.inventarioDao().guardarFloracionConAvance(second,3)
            assertEquals(1,CloudSync.rows(context,scope).size)
            assertEquals(censo,SharedSync.bind(target,"campaign-a"))
            val newer=b.inventarioDao().insertCenso(Censo(tipo="FLORACION")).toInt()
            assertNotEquals(newer,SharedSync.bind(target,"campaign-a"))
        } finally {a.close();b.close()}
    }

    @Test fun incomingCountsNeverReplaceLocalCapturesDraftsOrConflictingVersions()=runBlocking {
        val room=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=room.inventarioDao();dao.insertLotes(listOf(Lote("lot","Lote")));dao.agregarLinea("lot","1",4)
            val db=room.openHelper.writableDatabase;SharedSync.setup(db);val c=SharedSync.bind(db,"same")
            val rows=dao.prepararFloracion(c,"lot_L001")
            dao.insertRegistroFloracion(rows[0].copy(capturado=true,inflorescencias=99))
            dao.guardarBorrador(BorradorFicha("FLORACION",c,"lot_L001",2,"Hembra",false,"18","3","","",123))
            assertEquals(0,SharedSync.applyRecords(db,c,listOf(remote(),remote("P002"),remote("P003",1,"a"),remote("P003",2,"b")),emptySet()))
            assertEquals(99,dao.listarFloracion(c,"lot_L001").first().inflorescencias)
            assertEquals("18",dao.leerBorrador("FLORACION",c,"lot_L001",2)!!.a)
            assertEquals(3,SharedSync.query(db,"SELECT * FROM shared_issues").size)
            val original=SharedSync.snapshot(db,"lot_L001")!!
            val changed=JSONObject(original.toString());changed.getJSONArray("plantas").remove(3)
            assertFalse(SharedSync.applyCatalog(db,changed,SharedSync.canonical(original)))
            assertEquals(4,dao.getPlantas("lot_L001").size)
        } finally {room.close()}
    }

    @Test fun versionSixMigrationPreservesExistingRows()=runBlocking {
        val name="migration-${UUID.randomUUID()}.db"
        val db=context.openOrCreateDatabase(name,0,null)
        db.execSQL("CREATE TABLE registro_floracion(id INTEGER PRIMARY KEY, observacion TEXT)")
        db.execSQL("INSERT INTO registro_floracion VALUES(42,'No perder')")
        db.close()
        val factory=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val helper=factory.create(androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {}
            }).build())
        try {
            val sql=helper.writableDatabase;AppDatabase.MIGRATION_6_7.migrate(sql)
            val row=SharedSync.query(sql,"SELECT * FROM registro_floracion").single()
            assertEquals("No perder",row.getString("observacion"));assertTrue(row.isNull("compartidoPor"))
        } finally {helper.close();context.deleteDatabase(name)}
    }
}
