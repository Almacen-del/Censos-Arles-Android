package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ProduccionScreen(viewModel: ProduccionViewModel,onBack: ()->Unit) {
    val lote by viewModel.loteNombre.collectAsState()
    val linea by viewModel.linea.collectAsState()
    val registro by viewModel.registro.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val editor=rememberDraftEditor(com.example.conteoinventarioplantacionarlessas.data.BorradorFicha("PRODUCCION",viewModel.censoId,viewModel.lineaId,if(registro==null) -1 else 0,
        null,false,registro?.vivos?.toString() ?: "",registro?.muertos?.toString() ?: "",registro?.resiembras?.toString() ?: "",registro?.observacion ?: "",0),viewModel.repository)
    val vivos=editor.row.a
    val muertos=editor.row.b
    val resiembras=editor.row.c
    val observacion=editor.row.observacion
    val dirty=registro!=null && (vivos!=(registro?.vivos?.toString() ?: "") || muertos!=(registro?.muertos?.toString() ?: "") || resiembras!=(registro?.resiembras?.toString() ?: "") || observacion!=(registro?.observacion ?: ""))
    val navigate=rememberFichaNavigation(onBack,!busy && editor.ready,dirty,editor::flush)
    val valid=listOf(vivos,muertos,resiembras).all {it.toIntOrNull()?.let {n->n>=0}==true}
    CompactFicha("Inventario de producción",onBack={navigate(onBack)},actions={
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={navigate(onBack)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {Text("Anterior")}
            Button(onClick={viewModel.save(vivos.toIntOrNull(),muertos.toIntOrNull(),resiembras.toIntOrNull(),observacion.takeIf {it.isNotBlank()},onBack)},
                enabled=valid && registro!=null && editor.ready && !busy,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("guardar")) {Text(if(busy) "Guardando…" else "Guardar y salir")}
        }
    }) {dense ->
        Text(editor.message,style=MaterialTheme.typography.labelSmall)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        FichaLocation(lote,linea?.numero.orEmpty(),dense)
        if(!dense) Text("${linea?.cantidadArboles ?: 0} árboles en el catálogo",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompactNumberField(vivos,{v->editor.change {it.copy(a=v)}},"Vivos",Modifier.weight(1f),!busy && editor.ready)
            CompactNumberField(muertos,{v->editor.change {it.copy(b=v)}},"Muertos",Modifier.weight(1f),!busy && editor.ready)
            CompactNumberField(resiembras,{v->editor.change {it.copy(c=v)}},"Resiembras",Modifier.weight(1f),!busy && editor.ready)
        }
        Text("Vivos + resiembras: " + if(vivos.toLongOrNull()!=null && resiembras.toLongOrNull()!=null) "${vivos.toLong()+resiembras.toLong()}" else "pendiente",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(observacion,{v->editor.change {it.copy(observacion=v)}},label={Text("Observación del surco")},enabled=!busy && editor.ready,
            modifier=Modifier.fillMaxWidth(),singleLine=dense,minLines=if(dense) 1 else 2,maxLines=if(dense) 1 else 3,
            textStyle=MaterialTheme.typography.bodyMedium)
        if(!dense) Text("Escribe 0 cuando no haya árboles en una categoría.",style=MaterialTheme.typography.bodySmall)
    }
}
