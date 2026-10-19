package com.example.conteoinventarioplantacionarlessas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.CatalogImporter
import com.example.conteoinventarioplantacionarlessas.data.CloudSync
import com.example.conteoinventarioplantacionarlessas.data.AppDatabase
import com.example.conteoinventarioplantacionarlessas.data.InventarioRepository
import com.example.conteoinventarioplantacionarlessas.ui.*
import com.example.conteoinventarioplantacionarlessas.ui.theme.ConteoInventarioPlantacionArlesSASTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val auth: AuthViewModel = viewModel()
            val session by auth.state.collectAsState()
            ConteoInventarioPlantacionArlesSASTheme {
                val uid = session.uid
                if (uid == null) {
                    LoginScreen(session, auth::signIn, auth::enterGuest)
                } else {
                    key(uid) {
                        var catalogReady by remember { mutableStateOf(false) }
                        var catalogError by remember { mutableStateOf<String?>(null) }
                        var retry by remember { mutableIntStateOf(0) }
                        LaunchedEffect(uid,retry) {
                            try {
                                CatalogImporter.ensureLoaded(applicationContext, AppDatabase.getDatabase(applicationContext,uid))
                                catalogReady=true
                                CloudSync.schedule(applicationContext, uid, periodic=true)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { catalogError="No se pudo preparar el catálogo local. Tus censos siguen guardados." }
                        }
                        val repository = remember(uid) {
                            InventarioRepository(AppDatabase.getDatabase(applicationContext, uid).inventarioDao()) {
                                CloudSync.setStatus(applicationContext, uid, "Guardado local; pendiente de revisar sincronización.")
                                CloudSync.schedule(applicationContext, uid)
                            }
                        }
                        if (!catalogReady) {
                            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                                Text(catalogError ?: "Preparando lotes y árboles para trabajar sin internet…")
                                if(catalogError==null) CircularProgressIndicator()
                                else Button(onClick={catalogError=null; retry++}) { Text("Reintentar") }
                            }
                        } else AppNavGraph(repository = repository, sessionUid = uid,
                            accountEmail = session.email ?: "Trabajador", onSignOut = auth::signOut)
                    }
                }
            }
        }
    }
}
