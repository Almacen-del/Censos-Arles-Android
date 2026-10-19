package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.conteoinventarioplantacionarlessas.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.Lote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: InventarioViewModel,
    onNavigateToCenso: (String) -> Unit,
    accountEmail: String,
    sessionUid: String,
    onSignOut: () -> Unit
) {
    LaunchedEffect(Unit) {
        // El catálogo real se prepara antes de abrir la navegación.
    }

    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Cerrar sesión") },
        text = { Text(if (sessionUid == com.example.conteoinventarioplantacionarlessas.data.CloudSync.GUEST) "Los registros seguirán guardados. Puedes volver al invitado sin internet o entrar con tu cuenta cuando tengas señal." else "Los censos de $accountEmail seguirán guardados en este celular. Necesitarás internet para volver a ingresar con tu cuenta; el invitado funciona sin conexión.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } }
    )
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.logo_arles_sas), "Arles SAS", modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit)
                    Column { Text("Censos Arles SAS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(accountEmail, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                } },
                actions = { TextButton(onClick = { confirmSignOut = true }) { Text("Salir", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SyncPanel(sessionUid, accountEmail, viewModel::flushLocalDrafts)
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.widthIn(max = 260.dp).fillMaxWidth()) {
                        InventarioHomeCard(
                            title = "Censo de\nfloración",
                            background = Color(0xFF353535),
                            foreground = Color.White,
                            onClick = { onNavigateToCenso("FLORACION") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventarioHomeCard(
    title: String,
    background: Color,
    foreground: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) background else Color(0xFF252525),
            contentColor = if (enabled) foreground else Color(0xFF929292)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (enabled) title else "$title\n\nInactivo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CensoLotesScreen(
    tipo: String,
    viewModel: InventarioViewModel,
    onLineSelected: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    val lotes by viewModel.lotes.collectAsState()
    val todasLineas by viewModel.lineasPorLote.collectAsState()
    val censoId by viewModel.selectedCensoId.collectAsState()

    var showLoteMenu by remember { mutableStateOf(false) }
    val selectedLote by viewModel.selectedLote.collectAsState()
    val lineas = todasLineas.filter { it.loteId == selectedLote?.id }

    var showOmitirDialog by remember { mutableStateOf<String?>(null) }
    val omitidas by viewModel.lineasOmitidas.collectAsState()
    val avances by remember(tipo,censoId,selectedLote?.id) { viewModel.avanceLineas(tipo) }.collectAsState(initial=emptyList())
    var rangeDialog by remember { mutableStateOf(false) }
    var addLineDialog by remember { mutableStateOf(false) }
    var addedLineMessage by remember(selectedLote?.id) { mutableStateOf<String?>(null) }
    val censos by remember(tipo) { viewModel.getCensos(tipo) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var nuevoDialog by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }
    var censoMenu by remember { mutableStateOf(false) }
    var censoBusy by remember { mutableStateOf(false) }
    var censoError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tipo) {
        censoBusy = true
        try { viewModel.createOrGetCenso(tipo) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { censoError = "No se pudo abrir el censo. Regresa e inténtalo nuevamente." }
        finally { censoBusy = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(getTituloCenso(tipo),style=MaterialTheme.typography.titleMedium,maxLines=1)
                    censos.firstOrNull {it.id==censoId}?.let { active ->
                        Text("Censo #${active.id} · "+SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(Date(active.fecha)),style=MaterialTheme.typography.labelSmall)
                    }
                } },
                actions = {
                    Box {
                        TextButton(enabled=!censoBusy,onClick={optionsMenu=true}) {Text("Opciones")}
                        DropdownMenu(expanded=optionsMenu,onDismissRequest={optionsMenu=false}) {
                            DropdownMenuItem(text={Text("Agregar línea")},enabled=selectedLote!=null,onClick={optionsMenu=false;addLineDialog=true})
                            DropdownMenuItem(text={Text("Bloquear líneas por rango")},enabled=lineas.isNotEmpty(),onClick={optionsMenu=false;rangeDialog=true})
                            HorizontalDivider()
                            DropdownMenuItem(text={Text("Cambiar censo")},onClick={optionsMenu=false;censoMenu=true})
                            DropdownMenuItem(text={Text("Iniciar un nuevo censo")},onClick={optionsMenu=false;nuevoDialog=true})
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal=12.dp)) {
            run {
                if(censoMenu) AlertDialog(onDismissRequest={censoMenu=false},title={Text("Seleccionar censo")},
                    text={LazyColumn(Modifier.heightIn(max=320.dp)) {items(censos,key={it.id}) {censo ->
                        TextButton(onClick={viewModel.selectCenso(censo.id);censoMenu=false}) {
                            Text("Censo #${censo.id} · "+SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date(censo.fecha)))
                        }
                    }}},confirmButton={TextButton(onClick={censoMenu=false}) {Text("Cerrar")}})
                censoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (nuevoDialog) AlertDialog(onDismissRequest = { if (!censoBusy) nuevoDialog = false },
                    title = { Text("Nuevo censo") },
                    text = { Text("Se creará un censo con la fecha actual. Los anteriores se conservarán.") },
                    confirmButton = { TextButton(enabled = !censoBusy, onClick = {
                        censoBusy = true
                        scope.launch {
                            try { viewModel.nuevoCenso(tipo); nuevoDialog = false; censoError = null }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { censoError = "No se pudo crear el censo."; nuevoDialog = false }
                            finally { censoBusy = false }
                        }
                    }) { Text("Crear censo") } },
                    dismissButton = { TextButton(enabled = !censoBusy, onClick = { nuevoDialog = false }) { Text("Cancelar") } })
            }

            // Lote selector
            ExposedDropdownMenuBox(
                expanded = showLoteMenu,
                onExpandedChange = { showLoteMenu = !showLoteMenu }
            ) {
                OutlinedTextField(
                    value = selectedLote?.nombre ?: "Seleccionar Lote",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLoteMenu) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showLoteMenu,
                    onDismissRequest = { showLoteMenu = false }
                ) {
                    lotes.forEach { lote ->
                        DropdownMenuItem(
                            text = { Text(lote.nombre) },
                            onClick = {
                                viewModel.selectLote(lote)
                                showLoteMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedLote != null) {
                val total = lineas.sumOf {it.cantidadArboles ?: 0}
                val realizados = avances.filter {avance -> lineas.any {it.id==avance.lineaId}}.sumOf {it.completos}.coerceAtMost(total)
                Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("$realizados / $total árboles realizados",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        LinearProgressIndicator(progress={if(total>0) realizados.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth(),color=Color(0xFF2E7D32))
                        Text("${lineas.size} líneas · ${total-realizados} árboles pendientes",style=MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (addLineDialog) AddLineDialog(selectedLote!!, lineas, onDismiss = { addLineDialog = false },
                    onAdd = viewModel::agregarLinea, onCreated = { linea ->
                        addLineDialog = false
                        addedLineMessage = "Línea ${linea.numero} guardada · ${linea.cantidadArboles} árboles"
                    })
                addedLineMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if(rangeDialog) RangeBlockDialog(lineas,selectedLote!!.nombre,onDismiss={rangeDialog=false},onBlock=viewModel::bloquearRango)
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=8.dp)) {
                    items(lineas,key={it.id}) { linea ->
                        val bloqueo = omitidas.firstOrNull { it.lineaId == linea.id }
                        val isOmitida = bloqueo != null
                        val avance = avances.firstOrNull { it.lineaId == linea.id }
                        val completa = avance?.terminada == true
                        val foreground = if(completa) Color.White else MaterialTheme.colorScheme.onSurface

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isOmitida && !censoBusy && censoError == null) {
                                    if (censoId != null) {
                                        onLineSelected(censoId!!, linea.id)
                                    }
                                },
                            colors = if(completa) CardDefaults.cardColors(containerColor=Color(0xFF1B5E20),contentColor=Color.White) else if (isOmitida) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()
                        ) {
                            Row(modifier=Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Línea ${linea.numero}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=foreground)
                                    Text("${avance?.completos ?: 0} / ${linea.cantidadArboles ?: 0} realizados" + if(completa) " · Completa" else "",style=MaterialTheme.typography.bodySmall,color=foreground)
                                    if(isOmitida) Text(bloqueo?.responsable?.let {"Bloqueada · $it"} ?: "Bloqueada en este celular",style=MaterialTheme.typography.labelSmall,color=foreground)
                                }
                                TextButton(onClick={if(isOmitida) viewModel.deshacerOmitirLinea(linea.id) else showOmitirDialog=linea.id},enabled=!censoBusy) {
                                    Text(if(isOmitida) "Desbloquear" else "Bloquear",color=foreground)
                                }
                            }
                        }
                    }
                }
            }

            if (showOmitirDialog != null) {
                AlertDialog(
                    onDismissRequest = { showOmitirDialog = null },
                    title = { Text("Omitir línea") },
                    text = { Text("¿Omitir esta línea porque otra persona la está contando?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.omitirLinea(showOmitirDialog!!)
                            showOmitirDialog = null
                        }) {
                            Text("Omitir línea")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOmitirDialog = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

fun getTituloCenso(tipo: String): String = when(tipo) {
    "FRUTOS" -> "Conteo de Frutos"
    "PRODUCCION" -> "Inventario Producción"
    "FLORACION" -> "Censo Floración"
    else -> "Censo"
}
