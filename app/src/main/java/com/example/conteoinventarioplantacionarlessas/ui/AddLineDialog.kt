package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.Linea
import com.example.conteoinventarioplantacionarlessas.data.LineCreation
import com.example.conteoinventarioplantacionarlessas.data.Lote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AddLineDialog(lote: Lote, lineas: List<Linea>, onDismiss: () -> Unit,
    onAdd: suspend (String, String, Int) -> Linea, onCreated: (Linea) -> Unit) {
    var numero by rememberSaveable(lote.id) {
        mutableStateOf(((lineas.mapNotNull { LineCreation.number(it.numero) }.maxOrNull() ?: 0) + 1).toString())
    }
    var cantidad by rememberSaveable(lote.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Agregar línea · ${lote.nombre}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Indica el número de la nueva línea y cuántos árboles tiene. Se guardará en este celular sin conexión.")
            OutlinedTextField(value = numero, onValueChange = { numero = it; error = null },
                label = { Text("Número de línea") }, placeholder = { Text("Ejemplo: 140") },
                enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cantidad, onValueChange = { cantidad = it; error = null },
                label = { Text("Cantidad de árboles") }, enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text("Los árboles se numerarán desde P001 y quedarán pendientes de diligenciar, con sexo y siembra por verificar.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && numero.isNotBlank() && cantidad.isNotBlank(), onClick = {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val count = cantidad.trim().toIntOrNull()
                        require(count != null) { "Escribe una cantidad de árboles válida." }
                        onCreated(onAdd(lote.id, numero, count))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "No se pudo crear la línea. Inténtalo nuevamente." }
                    finally { busy = false }
                }
            }
        }) { Text(if (busy) "Guardando…" else "Crear línea") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}
