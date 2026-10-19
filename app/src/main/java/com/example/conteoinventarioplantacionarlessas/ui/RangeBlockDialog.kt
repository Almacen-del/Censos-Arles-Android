package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.Linea
import kotlinx.coroutines.launch

@Composable
fun RangeBlockDialog(lines: List<Linea>, lote: String, onDismiss: () -> Unit,
    onBlock: suspend (String,String,String?) -> Unit) {
    if(lines.isEmpty()) return
    var start by remember { mutableIntStateOf(0) }
    var end by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy) onDismiss()}, title={Text("Bloquear rango · $lote")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Selecciona las líneas que va a contar otra persona. El bloqueo queda solo en este celular y censo.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                RangeEndpoint("Desde", lines, start, !busy, Modifier.weight(1f)) { start=it; if(end<it) end=it }
                RangeEndpoint("Hasta", lines, end, !busy, Modifier.weight(1f)) { end=it }
            }
            OutlinedTextField(name,{name=it},label={Text("Persona (opcional)")},singleLine=true,enabled=!busy)
            if(end>=start) Text("${end-start+1} líneas: ${lines[start].numero} a ${lines[end].numero}")
            else Text("La línea final debe ir después de la inicial.",color=MaterialTheme.colorScheme.error)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }},
        confirmButton={TextButton(enabled=!busy && end>=start,onClick={
            busy=true
            scope.launch {
                try { onBlock(lines[start].id,lines[end].id,name); onDismiss() }
                catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { error=e.message ?: "No se pudo guardar el bloqueo." }
                finally { busy=false }
            }
        }) {Text(if(busy) "Guardando…" else "Confirmar bloqueo")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}})
}

@Composable
private fun RangeEndpoint(label: String, lines: List<Linea>, selected: Int, enabled: Boolean,
    modifier: Modifier, onSelect: (Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Text("$label ${lines[selected].numero}")}
        DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=260.dp)) {
            lines.forEachIndexed { index,line ->
                DropdownMenuItem(text={Text(line.numero)},onClick={onSelect(index);expanded=false})
            }
        }
    }
}
