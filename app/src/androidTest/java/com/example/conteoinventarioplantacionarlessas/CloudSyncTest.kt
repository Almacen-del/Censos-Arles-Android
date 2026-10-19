package com.example.conteoinventarioplantacionarlessas

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.example.conteoinventarioplantacionarlessas.data.*
import com.example.conteoinventarioplantacionarlessas.ui.AuthViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudSyncTest {
    @Test fun durableSnapshotsRetryCorrectionsAndGuestOwnership() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val scope = "test-${UUID.randomUUID()}"
        val dao = AppDatabase.getDatabase(context, scope).inventarioDao()
        dao.insertLotes(listOf(Lote("test-only", "Test local"), Lote("L001", "Demo")))
        dao.insertLineas(listOf(Linea("line", "test-only", "01", 1), Linea("demo", "L001", "01", 1)))
        val censo = dao.insertCenso(Censo(tipo = "FRUTOS")).toInt()
        val record = RegistroFrutos(censoId = censo, loteId = "test-only", lineaId = "line", arbolCodigo = "P1", posicion = 1, sexo = "Hembra", frutos = 0, capturado = true)
        val id = dao.insertRegistroFrutos(record).toInt()
        dao.insertRegistroFrutos(record.copy(loteId = "L001", lineaId = "demo"))
        val first = CloudSync.rows(context, scope).single()
        assertEquals(first, CloudSync.rows(context, scope).single())
        assertFalse(CloudSync.acknowledged(context, scope, first))
        CloudSync.acknowledge(context, scope, first)
        assertTrue(CloudSync.acknowledged(context, scope, first))
        dao.insertRegistroFrutos(record.copy(id = id, frutos = 9))
        val changed = CloudSync.rows(context, scope).single()
        assertEquals(first.id, changed.id)
        assertFalse(CloudSync.acknowledged(context, scope, changed))
        // A stale server acknowledgment must not acknowledge a newer edit.
        CloudSync.acknowledge(context, scope, first)
        assertFalse(CloudSync.acknowledged(context, scope, changed))
        val guest = AppDatabase.getDatabase(context, CloudSync.GUEST).inventarioDao()
        guest.insertLotes(listOf(Lote(scope, "Test invitado")))
        guest.insertLineas(listOf(Linea(scope, scope, "01", 1)))
        val guestCenso = guest.insertCenso(Censo(tipo = "FRUTOS")).toInt()
        guest.insertRegistroFrutos(record.copy(censoId = guestCenso, loteId = scope, lineaId = scope))
        assertTrue(CloudSync.claimGuest(context, scope) >= 1)
        val row = CloudSync.rows(context, CloudSync.GUEST).first { it.data["censoId"].toString().endsWith("_$guestCenso") }
        assertEquals(scope, CloudSync.owner(context, row))
        CloudSync.claimGuest(context, "other-user")
        assertEquals(scope, CloudSync.owner(context, row))
        assertTrue(CloudSync.rows(context, "other-user").isEmpty())
    }

    @Test fun guestRestoresWithoutFirebaseOrNetwork() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        check(app.packageName.endsWith(".verification"))
        val first = AuthViewModel(app)
        first.enterGuest()
        assertEquals(CloudSync.GUEST, first.state.value.uid)
        val reopened = AuthViewModel(app)
        assertEquals(CloudSync.GUEST, reopened.state.value.uid)
        reopened.signOut()
        assertNull(reopened.state.value.uid)
    }
}
