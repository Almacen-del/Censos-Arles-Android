package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TreeClassification(sexo: String?, muerto: Boolean, enabled: Boolean,
    onSexo: (String?) -> Unit, onMuerto: (Boolean) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Macho", "Hembra", "Pendiente").forEach { option ->
                val value=if(option=="Pendiente") null else option
                FilterChip(selected=sexo==value, enabled=enabled, onClick={onSexo(value)}, label={Text(option,style=MaterialTheme.typography.labelMedium)},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp))
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=muerto,enabled=enabled,onClick={confirm=true},label={Text("Muerto")},modifier=Modifier.heightIn(min=48.dp))
            Text(if(muerto) "Sin conteos. Conserva código y sexo." else if(sexo==null) "Sexo pendiente de verificar" else "Sexo: $sexo",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},
        title={Text(if(muerto) "Quitar marca de muerto" else "Marcar árbol muerto")},
        text={Text(if(muerto) "Confirma que esta marca fue un error. El árbol seguirá en su misma posición." else "Se quitarán los conteos de esta tarjeta y se conservará la identificación del árbol. El cambio se aplica al guardar.")},
        confirmButton={TextButton(onClick={confirm=false; onMuerto(!muerto)}) {Text("Confirmar")}},
        dismissButton={TextButton(onClick={confirm=false}) {Text("Cancelar")}})
}
