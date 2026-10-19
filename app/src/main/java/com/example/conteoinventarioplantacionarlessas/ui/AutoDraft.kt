package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.launch

class DraftEditor(initial:BorradorFicha,private val repo:InventarioRepository) {
    var row by mutableStateOf(initial)
    var ready by mutableStateOf(false)
    var message by mutableStateOf("Recuperando ficha…")
    private var revision=0
    fun change(transform:(BorradorFicha)->BorradorFicha) {
        if(!ready) return
        row=transform(row).copy(actualizadoEn=System.currentTimeMillis())
        message="Guardando…"
        revision++
        pending=repo.draftQueue.save(row)
    }
    var pending by mutableStateOf<kotlinx.coroutines.Deferred<Boolean>?>(null)
        private set
    suspend fun flush():Boolean {
        val ok=repo.draftQueue.flush()
        message=if(ok) "Guardado automático en el celular" else "No se guardó. Reintenta antes de salir."
        return ok
    }
}

@Composable
fun rememberDraftEditor(initial:BorradorFicha,repo:InventarioRepository,committedAt:Long=0):DraftEditor {
    val editor=remember(initial.tipo,initial.censoId,initial.lineaId,initial.posicion) {DraftEditor(initial,repo)}
    LaunchedEffect(editor) {
        try {
            repo.draftQueue.flush()
            val saved=repo.leerBorrador(initial.tipo,initial.censoId,initial.lineaId,initial.posicion)
            if(saved!=null && saved.actualizadoEn>committedAt) editor.row=saved
            editor.ready=true
            editor.message=if(saved!=null && saved.actualizadoEn>committedAt) "Borrador recuperado" else "Guardado automático activo"
        } catch(e:kotlinx.coroutines.CancellationException) {throw e}
        catch(e:Exception) {editor.message="No se pudo recuperar la ficha. Vuelve a abrirla."}
    }
    val pending=editor.pending
    LaunchedEffect(pending) {if(pending!=null) editor.message=if(pending.await()) "Guardado automático en el celular" else "Error al guardar. Reintenta antes de salir."}
    return editor
}

@Composable
fun FichaLocation(lote:String,linea:String,dense:Boolean) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(lote,modifier=Modifier.weight(1f),style=if(dense) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2)
        Spacer(Modifier.width(8.dp))
        Text(linea,style=if(dense) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    }
}
