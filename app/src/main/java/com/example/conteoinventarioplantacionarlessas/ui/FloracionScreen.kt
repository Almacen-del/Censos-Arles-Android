package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FloracionScreen(viewModel: FloracionViewModel, onBack: ()->Unit) {
    DisposableEffect(viewModel.lineaId) {
        com.example.conteoinventarioplantacionarlessas.data.SharedSync.beginEditing(viewModel.lineaId)
        onDispose { com.example.conteoinventarioplantacionarlessas.data.SharedSync.endEditing(viewModel.lineaId) }
    }
    val state by viewModel.state.collectAsState()
    val registro=state.actual
    if (registro?.compartidoPor != null && !state.resumen) {
        CompactFicha("Conteo compartido", onBack=onBack, actions={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={viewModel.irA(state.index-1)},enabled=state.index>0) {Text("Anterior")}
                TextButton(onClick={if(state.index<state.registros.lastIndex) viewModel.irA(state.index+1) else onBack()}) {Text("Siguiente")}
            }
        }) { dense ->
            FichaLocation(state.lote,state.linea?.numero.orEmpty(),dense)
            Text("Árbol ${registro.arbolCodigo}",style=MaterialTheme.typography.titleLarge)
            Text("Recibido de otro celular · ${registro.estado}")
            Text(if(registro.muerto) "Planta muerta" else "Sexo: ${registro.sexo ?: "Pendiente"}")
            Text("Inflorescencias: ${registro.inflorescencias ?: "—"}")
            Text("Botones: ${registro.botones ?: "—"}")
            Text(registro.observacion.ifBlank { "Sin observación" })
            Text("El autor conserva la edición de este conteo. No necesitas volver a registrarlo.",style=MaterialTheme.typography.bodySmall)
        }
        return
    }
    val editor=rememberDraftEditor(com.example.conteoinventarioplantacionarlessas.data.BorradorFicha("FLORACION",viewModel.censoId,viewModel.lineaId,registro?.posicion ?: -1,
        registro?.sexo,registro?.muerto ?: false,registro?.inflorescencias?.toString() ?: "",registro?.botones?.toString() ?: "","",registro?.observacion ?: "",0),viewModel.repository,registro?.verificadoEn ?: 0)
    val inf=editor.row.a
    val botones=editor.row.b
    val sexo=editor.row.sexo
    val muerto=editor.row.muerto
    var showObservation by remember {mutableStateOf(false)}
    var confirmDelete by remember {mutableStateOf(false)}
    var canDelete by remember(registro?.id) {mutableStateOf(false)}
    LaunchedEffect(registro?.id,registro?.capturado) {
        canDelete=false
        if(registro!=null && !registro.capturado) {
            val plant=viewModel.repository.getPlanta(viewModel.lineaId,registro.arbolCodigo)
            canDelete=plant!=null && plant.filasOrigen.isNullOrBlank() && plant.siembra==null
        }
    }
    var confirmZero by remember {mutableStateOf(false)}
    val dirty=registro!=null && (editor.row.observacion!=registro.observacion || sexo!=registro.sexo || muerto!=registro.muerto || inf!=(registro.inflorescencias?.toString() ?: "") || botones!=(registro.botones?.toString() ?: ""))
    val navigate=rememberFichaNavigation(onBack,!state.busy && editor.ready,dirty && !state.resumen,editor::flush)
    CompactFicha("Censo de floración",onBack={navigate(onBack)},actions={
        if(state.resumen) {
            Button(onClick=onBack,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Volver a los árboles")}
            TextButton(onClick={viewModel.irA(state.registros.indexOfFirst {it.estado!="COMPLETO"}.coerceAtLeast(0))},modifier=Modifier.fillMaxWidth()) {Text("Revisar registros")}
        } else {
            Button(onClick={viewModel.guardar(sexo,inf,botones,true,muerto,observacion=editor.row.observacion)},
                enabled=registro!=null && editor.ready && !state.busy && (muerto || (FloracionValidation.validNumber(inf) && FloracionValidation.validNumber(botones) && (sexo==null || FloracionValidation.complete(sexo,inf.toIntOrNull(),if(inf.toIntOrNull()==0) null else botones.toIntOrNull())))),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("guardar")) {
                Text(if(state.busy) "Guardando…" else if(state.index==state.registros.lastIndex) "Guardar y finalizar" else "Guardar y seguir")
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={navigate {viewModel.irA(state.index-1)}},enabled=state.index>0 && !state.busy) {Text("Anterior")}
                TextButton(onClick={viewModel.guardar(sexo,inf,botones,false,muerto,observacion=editor.row.observacion)},enabled=registro!=null && editor.ready && !state.busy) {Text("Guardar borrador")}
            }
        }
    }) {dense ->
        Text(editor.message,style=MaterialTheme.typography.labelSmall)
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        if(state.loading) {CircularProgressIndicator();Text("Abriendo la línea…")}
        else if(registro==null) {Button(onClick=viewModel::cargar) {Text("Reintentar")}}
        else if(state.resumen) {
            Text(if(state.completos==state.registros.size) "Línea completada" else "Resumen de la línea",style=MaterialTheme.typography.titleLarge)
            Text("Completos: ${state.completos}/${state.registros.size}")
            Text("Árboles pendientes: ${state.registros.size-state.completos}")
            Text("Muertos: ${state.registros.count {it.muerto}}")
        } else {
            FichaLocation(state.lote,state.linea?.numero.orEmpty(),dense)
            Text("Árbol ${registro.arbolCodigo} · ${state.index+1}/${state.registros.size} ${if(viewModel.reversed) "↓" else "↑"}",style=MaterialTheme.typography.titleMedium)
            if(canDelete) TextButton(onClick={confirmDelete=true},enabled=editor.ready && !state.busy,
                colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Eliminar árbol")}
            if(!dense) Text("Siembra ${registro.siembra ?: "sin dato"} · ${state.completos} completos",style=MaterialTheme.typography.bodySmall)
            TreeClassification(sexo,muerto,!state.busy && editor.ready,onSexo={v->editor.change {it.copy(sexo=v)}},onMuerto={v->editor.change {it.copy(muerto=v,a=if(v) "" else it.a,b=if(v) "" else it.b)}})
            if(!muerto) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CompactNumberField(inf,{if(it.toIntOrNull()==0 && botones.isNotBlank()) confirmZero=true else editor.change {row->row.copy(a=it)}},"Inflorescencias",Modifier.weight(1f),!state.busy && editor.ready,next=inf.toIntOrNull()!=0)
                    CompactNumberField(botones,{v->editor.change {it.copy(b=v)}},"Botones",Modifier.weight(1f),!state.busy && editor.ready && inf.toIntOrNull()!=0)
                }
                if(!dense) Text(if(inf.toIntOrNull()==0) "Sin inflorescencias: no aplica muestra de botones." else "Botones de UNA inflorescencia elegida al azar.",style=MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick={showObservation=true},enabled=editor.ready && !state.busy,modifier=Modifier.fillMaxWidth()) {
                Text(if(editor.row.observacion.isBlank()) "Agregar observación" else "Editar observación",maxLines=1)
            }
            if(editor.row.observacion.isNotBlank()) Text(editor.row.observacion,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
            if(!dense) state.message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
    if(showObservation) AlertDialog(onDismissRequest={showObservation=false},
        title={Text("Observación · ${registro?.arbolCodigo.orEmpty()}")},
        text={Column {
            OutlinedTextField(value=editor.row.observacion,onValueChange={v->if(v.length<=2000) editor.change {it.copy(observacion=v)}},
                label={Text("Daño o inconveniente (opcional)")},modifier=Modifier.fillMaxWidth().testTag("observacion"),minLines=3,maxLines=6,
                supportingText={Text("${editor.row.observacion.length}/2000 · ${editor.message}")})
        }},
        confirmButton={TextButton(onClick={showObservation=false}) {Text("Listo")}})
    if(confirmDelete) AlertDialog(onDismissRequest={if(!state.busy) confirmDelete=false},
        title={Text("¿Eliminar ${registro?.arbolCodigo.orEmpty()}?")},
        text={Text("Se quitará este árbol agregado por error y su borrador. Los árboles siguientes retrocederán un número, conservando sus conteos y borradores. Esta acción no se puede deshacer.")},
        confirmButton={TextButton(enabled=!state.busy,onClick={confirmDelete=false;viewModel.eliminarArbol(onBack)}) {Text("Eliminar árbol")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmDelete=false}) {Text("Cancelar")}})
    if(confirmZero) AlertDialog(onDismissRequest={confirmZero=false},title={Text("Sin inflorescencias")},
        text={Text("Al indicar 0 se quitará el conteo de botones de esta ficha.")},
        confirmButton={TextButton(onClick={editor.change {it.copy(a="0",b="")};confirmZero=false}) {Text("Confirmar")}},
        dismissButton={TextButton(onClick={confirmZero=false}) {Text("Cancelar")}})
}
