package com.example.conteoinventarioplantacionarlessas

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DraftPersistenceTest {
    @Test fun queuedEditsSurviveDatabaseReopeningAndKeepCensusesSeparate() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val name="draft-test-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            val repo=InventarioRepository(db.inventarioDao())
            val row=BorradorFicha("FRUTOS",1,"line",1,"Hembra",false,"","","","",1)
            (1..30).forEach {repo.draftQueue.save(row.copy(a=it.toString(),actualizadoEn=it.toLong()))}
            repo.draftQueue.save(row.copy(censoId=2,a="otro",actualizadoEn=31))
            assertTrue(repo.draftQueue.flush())
            db.close()
            db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
            assertEquals("30",db.inventarioDao().leerBorrador("FRUTOS",1,"line",1)!!.a)
            assertEquals("otro",db.inventarioDao().leerBorrador("FRUTOS",2,"line",1)!!.a)
            assertNull(db.inventarioDao().leerBorrador("FLORACION",1,"line",1))
        } finally {db.close();context.deleteDatabase(name)}
    }
}
