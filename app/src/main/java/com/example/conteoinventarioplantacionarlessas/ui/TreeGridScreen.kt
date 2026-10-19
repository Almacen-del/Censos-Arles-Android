package com.example.conteoinventarioplantacionarlessas.ui

import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.conteoinventarioplantacionarlessas.data.*

data class TreeTile(val codigo:String,val posicion:Int,val complete:Boolean,val draft:Boolean,val edited:Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeGridScreen(repo:InventarioRepository,tipo:String,censo:Int,lineaId:String,onTree:(Int)->Unit,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    var adding by remember {mutableStateOf(false)}
    var confirmAdd by remember {mutableStateOf(false)}
    BackHandler(enabled=adding) {}
    val context=LocalContext.current
    var reversed by remember(censo,lineaId) {mutableStateOf(tipo=="FLORACION" && CountDirection.reversed(context,censo,lineaId))}
    var directionChanged by remember(censo,lineaId) {mutableStateOf(false)}
    var ready by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var linea by remember {mutableStateOf<Linea?>(null)}
    var lote by remember {mutableStateOf("")}
    val frutos by remember {repo.getRegistrosFrutos(censo,lineaId)}.collectAsState(initial=emptyList())
    val flores by remember {repo.getRegistrosFloracion(censo,lineaId)}.collectAsState(initial=emptyList())
    val drafts by remember {repo.borradores(tipo,censo,lineaId)}.collectAsState(initial=emptyList())
    LaunchedEffect(Unit) {
        try {
            repo.draftQueue.flush()
            linea=repo.getLineaById(lineaId)
            lote=linea?.let {repo.getLoteById(it.loteId)?.nombre}.orEmpty()
            if(tipo=="FRUTOS") repo.prepararFrutos(censo,lineaId) else repo.prepararFloracion(censo,lineaId)
            ready=true
        } catch(e:kotlinx.coroutines.CancellationException) {throw e}
        catch(e:Exception) {error="No se pudo abrir la línea. Regresa e inténtalo de nuevo."}
    }
    val tiles=if(tipo=="FRUTOS") frutos.map {r->
        val draft=drafts.firstOrNull {it.posicion==r.posicion && it.actualizadoEn>r.verificadoEn}
        TreeTile(r.arbolCodigo,r.posicion,r.capturado && r.estado=="COMPLETO" && draft==null && (if(r.muerto) r.frutos==null else (r.sexo=="Macho" && r.frutos==null) || (r.sexo=="Hembra" && r.frutos!=null && r.frutos>=0)),
            draft!=null || (r.capturado && r.estado!="COMPLETO"),draft?.actualizadoEn ?: 0)
    } else flores.map {r->
        val draft=drafts.firstOrNull {it.posicion==r.posicion && it.actualizadoEn>r.verificadoEn}
        TreeTile(r.arbolCodigo,r.posicion,r.capturado && r.estado=="COMPLETO" && draft==null && FloracionValidation.complete(r.sexo,r.inflorescencias,r.botones,r.muerto),
            draft!=null || (r.capturado && r.estado!="COMPLETO"),draft?.actualizadoEn ?: 0)
    }
    val orderedTiles=countingOrder(tiles,reversed) {it.posicion}
    Scaffold(containerColor=Color.Black,topBar={TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Black,titleContentColor=Color.White),title={Text("Árboles del surco")},
        navigationIcon={TextButton(enabled=!adding,onClick=onBack) {Text("Atrás",color=Color.White)}})}) {padding->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("$lote · ${linea?.numero.orEmpty()}",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("${tiles.count {it.complete}} de ${tiles.size} árboles completos",color=Color.White)
            if(tiles.isNotEmpty()) LinearProgressIndicator(progress={tiles.count {it.complete}.toFloat()/tiles.size},modifier=Modifier.fillMaxWidth(),color=Color(0xFF81C784),trackColor=Color(0xFF454545))
            Text("Verde: completo guardado · Gris: pendiente o borrador",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
            error?.let {Text(it,color=Color(0xFFFFB4AB))}
            if(!ready && error==null) CircularProgressIndicator()
            if(ready) {
                if(tipo=="FLORACION" && tiles.size>1) {
                    Text("Sentido del conteo",color=Color.White,style=MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(enabled=!adding,selected=!reversed,onClick={reversed=false;directionChanged=true;CountDirection.save(context,censo,lineaId,false)},label={Text("Primero → último")},modifier=Modifier.weight(1f))
                        FilterChip(enabled=!adding,selected=reversed,onClick={reversed=true;directionChanged=true;CountDirection.save(context,censo,lineaId,true)},label={Text("Último → primero")},modifier=Modifier.weight(1f))
                    }
                }
                val resume=if(directionChanged) null else orderedTiles.filter {it.edited>0}.maxByOrNull {it.edited}
                val next=resume ?: orderedTiles.firstOrNull {!it.complete} ?: orderedTiles.firstOrNull()
                Button(colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF555555),contentColor=Color.White),onClick={next?.let {onTree(it.posicion)}},enabled=next!=null && !adding,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(if(next?.draft==true) "Continuar borrador · ${next.codigo}" else "Continuar · ${next?.codigo.orEmpty()}")}
                if(tipo=="FLORACION") OutlinedButton(onClick={confirmAdd=true},enabled=!adding,modifier=Modifier.fillMaxWidth()) {
                    Text(if(adding) "Agregando árbol…" else "+ Agregar árbol")
                }
                LazyVerticalGrid(columns=GridCells.Adaptive(92.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(orderedTiles,key={it.codigo}) {tree->
                        Card(colors=CardDefaults.cardColors(containerColor=if(tree.complete) Color(0xFF1B5E20) else Color(0xFF454545),contentColor=Color.White),
                            modifier=Modifier.aspectRatio(1f).clickable(enabled=!adding) {onTree(tree.posicion)}) {
                            Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(tree.codigo,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Text(if(tree.complete) "Completo" else if(tree.draft) "Borrador" else "Pendiente",style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if(confirmAdd) AlertDialog(onDismissRequest={if(!adding) confirmAdd=false},
        title={Text(if(reversed) "Agregar al principio" else "Agregar al final")},
        text={Text(if(reversed) "El nuevo árbol será P001. Los árboles de este censo pasarán a P002, P003… conservando sexo, conteos y borradores. Los censos anteriores conservan sus códigos."
            else "Se añadirá un árbol al final del surco, pendiente de verificar y contar.")},
        confirmButton={TextButton(enabled=!adding,onClick={
            if(!adding) {adding=true;error=null;scope.launch {
                try {
                    val position=repo.agregarArbolFloracion(censo,lineaId,reversed)
                    linea=repo.getLineaById(lineaId)
                    confirmAdd=false
                    onTree(position)
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {confirmAdd=false;error=e.message ?: "No se pudo agregar el árbol."}
                finally {adding=false}
            }}
        }) {Text("Agregar árbol")}},
        dismissButton={TextButton(enabled=!adding,onClick={confirmAdd=false}) {Text("Cancelar")}})
}
