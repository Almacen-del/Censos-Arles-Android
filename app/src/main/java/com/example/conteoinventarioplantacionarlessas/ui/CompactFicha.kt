package com.example.conteoinventarioplantacionarlessas.ui

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun rememberFichaNavigation(onExit: ()->Unit, enabled: Boolean, dirty: Boolean, beforeBack: suspend ()->Boolean = {true}): (() -> Unit) -> Unit {
    val scope=rememberCoroutineScope()
    var leaving by remember {mutableStateOf(false)}
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val request: (() -> Unit) -> Unit = { if(enabled) pending=it }
    BackHandler { request(onExit) }
    if(pending!=null) AlertDialog(onDismissRequest={pending=null},
        title={Text("¿Retroceder de esta ficha?")},
        text={Text(if(dirty) "Tus cambios se conservarán como borrador antes de retroceder." else "Confirma para evitar salir por una pulsación accidental.")},
        confirmButton={TextButton(onClick={pending=null}) {Text("Seguir aquí")}},
        dismissButton={TextButton(enabled=enabled && !leaving,onClick={leaving=true;scope.launch {if(beforeBack()) {val action=pending;pending=null;action?.invoke()};leaving=false}}) {Text("Sí, retroceder")}})
    return request
}

@Composable
fun CompactFicha(title: String, onBack: ()->Unit, actions: @Composable ()->Unit,
    content: @Composable ColumnScope.(Boolean)->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,onPrimary=Color.Black,
        background=Color.Black,surface=Color(0xFF202020),onSurface=Color.White,
        surfaceVariant=Color(0xFF363636),onSurfaceVariant=Color(0xFFDDDDDD),
        secondaryContainer=Color(0xFF555555),onSecondaryContainer=Color.White)) {
        Scaffold(modifier=Modifier.imePadding().testTag("ficha"),containerColor=Color.Black,
            topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick=onBack) {Text("Atrás")}
                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=1)
            }},
            bottomBar={Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=4.dp)) {actions()}}
        ) { padding ->
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                val dense=maxHeight < 360.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=if(dense) 2.dp else 8.dp),
                    verticalArrangement=Arrangement.spacedBy(if(dense) 2.dp else 6.dp)) {content(dense)}
            }
        }
    }
}

@Composable
fun CompactNumberField(value: String, onChange: (String)->Unit, label: String, modifier: Modifier=Modifier, enabled: Boolean=true, next: Boolean=false) {
    val focus=LocalFocusManager.current
    OutlinedTextField(value,onChange,label={Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1)},modifier=modifier,enabled=enabled,singleLine=true,
        textStyle=MaterialTheme.typography.titleLarge.copy(fontSize=22.sp,fontWeight=FontWeight.SemiBold),
        isError=!FloracionValidation.validNumber(value),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=if(next) ImeAction.Next else ImeAction.Done),
        keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Next)},onDone={focus.clearFocus()}))
}
