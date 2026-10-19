package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(state: LoginState, onSignIn: (String, String) -> Unit, onGuest: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Password intentionally excluded from saved instance state and persistence.
    var password by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black,
        background = Color.Black, surface = Color(0xFF252525), onSurface = Color.White)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Spacer(Modifier.height(28.dp))
                Text("Censos Arles", style = MaterialTheme.typography.headlineLarge)
                Text("Ingresa con tu cuenta de trabajador", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = email, onValueChange = { email = it }, enabled = !state.busy,
                    label = { Text("Correo electrónico") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, enabled = !state.busy,
                    label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { onSignIn(email, password) },
                    enabled = state.configured && !state.busy && LoginInput.validEmail(email) && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (state.busy) "Ingresando…" else "Iniciar sesión")
                }
                OutlinedButton(onClick = onGuest, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continuar como invitado sin internet") }
                Text("El invitado guarda en este celular. Después puedes vincular sus registros a tu cuenta.")
                Text("Necesitas internet para ingresar. Mantén tu sesión abierta para registrar en campo sin señal.")
                Text("Si no recuerdas tu contraseña, consulta al encargado.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
