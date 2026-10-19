package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.CloudSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SyncPanel(uid: String, email: String, flushDrafts: suspend () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var message by remember(uid) { mutableStateOf(CloudSync.status(context, uid)) }
    var sharedIssues by remember { mutableStateOf<List<String>?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var records by remember(uid) { mutableStateOf<List<CloudSync.LocalRecord>?>(null) }
    var selected by remember(uid) { mutableStateOf<CloudSync.LocalRecord?>(null) }
    var deleteError by remember(uid) { mutableStateOf<String?>(null) }
    fun label(item: CloudSync.LocalRecord): String {
        val data = item.row.data["datos"] as Map<*, *>
        val date = (data["fechaCenso"] as? Number)?.toLong()?.let {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: ""
        return "${data["loteNombre"]} · ${data["lineaNumero"]} · ${data["arbolCodigo"] ?: "Surco"}\n$date · ${item.row.data["tipo"]}${if(item.scope == CloudSync.GUEST) " · Invitado" else ""}"
    }
    LaunchedEffect(uid) {
        while(true) { message = CloudSync.status(context, uid); delay(2000) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222222), contentColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if(uid == CloudSync.GUEST) {
                Text("Modo invitado · guardado local", style = MaterialTheme.typography.titleSmall)
                Text("Registros guardados en este celular. Sal e ingresa con tu cuenta para vincularlos y enviarlos.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(message, style = MaterialTheme.typography.bodySmall)
                Button(modifier = Modifier.fillMaxWidth().heightIn(min=48.dp), colors = ButtonDefaults.buttonColors(containerColor=Color(0xFF555555),contentColor=Color.White), onClick = { CloudSync.setStatus(context, uid, "En espera de conexión para sincronizar…"); CloudSync.schedule(context, uid) }) { Text("Sincronizar ahora", color = Color.White) }
                TextButton(onClick = { scope.launch { sharedIssues = withContext(Dispatchers.IO) { com.example.conteoinventarioplantacionarlessas.data.SharedSync.issues(context,uid) } } }) { Text("Revisar diferencias", color = Color.White) }
                Text("Sube tus registros y recibe los avances del censo compartido. Los conteos recibidos conservan su autor.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Vincular registros de invitado", color = Color.White) }
                if (CloudSync.canDeleteLocal(context, uid)) TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            flushDrafts()
                            records = withContext(Dispatchers.IO) { CloudSync.localRecords(context, uid) }
                        } catch (_: Exception) { deleteError = "No se pudieron cargar los registros locales." }
                        finally { busy = false }
                    }
                }) { Text("Eliminar registro local", color = Color(0xFFFFB4AB)) }
            }
        }
    }
    sharedIssues?.let { issues -> AlertDialog(onDismissRequest={sharedIssues=null},title={Text("Diferencias entre celulares")},
        text={LazyColumn(Modifier.heightIn(max=360.dp)) { if(issues.isEmpty()) item {Text("No hay diferencias pendientes.")} else items(issues) {Text(it,modifier=Modifier.padding(vertical=8.dp))} }},
        confirmButton={TextButton(onClick={sharedIssues=null}) {Text("Cerrar")}}) }
    if (records != null && selected == null) AlertDialog(
        onDismissRequest = { records = null }, title = { Text("Registros de este celular") },
        text = {
            if (records!!.isEmpty()) Text("No hay registros guardados disponibles para eliminar.")
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(records!!, key = { it.scope + it.row.id }) { item ->
                    TextButton(onClick = { selected = item }) { Text(label(item)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { records = null }) { Text("Cerrar") } })
    selected?.let { item -> AlertDialog(
        onDismissRequest = { if (!busy) selected = null },
        title = { Text("¿Eliminar este registro local?") },
        text = { Text("${label(item)}\n\nSe borrará el conteo y su borrador del celular. El árbol conservará su número y quedará pendiente. No se elimina lo enviado a Supabase. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    flushDrafts()
                    CloudSync.deleteLocal(context, uid, item)
                    message = CloudSync.status(context, uid)
                    selected = null; records = null
                } catch (_: Exception) {
                    selected = null; records = null
                    deleteError = "No se eliminó el registro. Puede haber cambiado o la sesión ya no tiene permiso. Vuelve a seleccionarlo."
                } finally { busy = false }
            }
        }) { Text(if (busy) "Eliminando…" else "Eliminar del celular") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Cancelar") } }) }
    deleteError?.let { error -> AlertDialog(onDismissRequest = { deleteError = null },
        title = { Text("Eliminación local") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = { deleteError = null }) { Text("Aceptar") } }) }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Vincular a $email") },
        text = { Text("Los registros reales de invitado que aún no tengan dueño quedarán atribuidos a esta cuenta. Los de demostración no se envían. Confirma que estos registros te corresponden.") },
        confirmButton = { TextButton(onClick = {
            confirm = false; busy = true
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) { CloudSync.claimGuest(context, uid) }
                    CloudSync.setStatus(context, uid, "$count registros de invitado vinculados. Esperando conexión.")
                    CloudSync.schedule(context, uid)
                } catch (_: Exception) { CloudSync.setStatus(context, uid, "No se pudo vincular. Tus registros siguen guardados localmente.") }
                finally { busy = false }
            }
        }) { Text("Vincular a mi cuenta") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}
