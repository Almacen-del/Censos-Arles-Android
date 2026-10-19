package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FrutosScreen(viewModel: FrutosViewModel, onBack: () -> Unit) {
    val lote by viewModel.loteNombre.collectAsState()
    val linea by viewModel.linea.collectAsState()
    val index by viewModel.currentIndex.collectAsState()
    val registros by viewModel.registros.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val registro=registros.getOrNull(index)
    val editor=rememberDraftEditor(com.example.conteoinventarioplantacionarlessas.data.BorradorFicha("FRUTOS",viewModel.censoId,viewModel.lineaId,registro?.posicion ?: -1,
        registro?.sexo,registro?.muerto ?: false,registro?.frutos?.toString() ?: "","","","",0),viewModel.repository,registro?.verificadoEn ?: 0)
    val muerto=editor.row.muerto
    val sexo=editor.row.sexo
    val frutos=editor.row.a
    val dirty=registro!=null && (muerto!=registro.muerto || sexo!=registro.sexo || frutos!=(registro.frutos?.toString() ?: ""))
    val navigate=rememberFichaNavigation(onBack,!busy && editor.ready,dirty,editor::flush)
    CompactFicha("Conteo de frutos",onBack={navigate(onBack)},actions={
        if(finished) Button(onClick=onBack,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Volver a los árboles")}
        else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={navigate(viewModel::goBack)},enabled=index>0 && !busy,modifier=Modifier.heightIn(min=48.dp)) {Text("Anterior")}
            Button(onClick={viewModel.saveAndNext(sexo,frutos.toIntOrNull(),muerto)},
                enabled=registro!=null && editor.ready && !busy && (muerto || sexo!="Hembra" || frutos.toIntOrNull()?.let { it>=0 }==true),
                modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("guardar")) {
                Text(if(busy) "Guardando…" else if(index==registros.lastIndex) "Guardar y finalizar" else "Guardar y seguir")
            }
        }
    }) { dense ->
        Text(editor.message,style=MaterialTheme.typography.labelSmall)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        if(finished) {
            Text("Línea guardada",style=MaterialTheme.typography.titleLarge)
            Text("Muertos: ${registros.count { it.muerto }}")
            Text("Pendientes de sexo: ${registros.count { it.sexo==null && !it.muerto }}")
        } else if(registro!=null) {
            FichaLocation(lote,linea?.numero.orEmpty(),dense)
            Text("Árbol ${registro.arbolCodigo} · ${index+1}/${registros.size}",style=MaterialTheme.typography.titleMedium)
            TreeClassification(sexo,muerto,!busy && editor.ready,onSexo={v->editor.change {it.copy(sexo=v)}},onMuerto={v->editor.change {it.copy(muerto=v,a=if(v) "" else it.a)}})
            if(!muerto && sexo=="Hembra") CompactNumberField(frutos,{v->editor.change {it.copy(a=v)}},"Cantidad de frutos",Modifier.fillMaxWidth(),!busy && editor.ready)
            else if(!dense) Text(if(muerto) "Este árbol queda identificado como muerto." else if(sexo==null) "Puedes guardar y continuar con el sexo pendiente." else "Macho: no requiere conteo de frutos.",style=MaterialTheme.typography.bodySmall)
        } else {CircularProgressIndicator();Text("Abriendo la línea…")}
    }
}
