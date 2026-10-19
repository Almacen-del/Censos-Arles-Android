package com.example.conteoinventarioplantacionarlessas.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.conteoinventarioplantacionarlessas.data.CloudSync
import com.example.conteoinventarioplantacionarlessas.data.SupabaseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LoginInput {
    fun email(value: String): String = value.trim()
    fun validEmail(value: String): Boolean = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email(value))
}
data class LoginState(val uid: String? = null,val email: String? = null,val busy: Boolean = false,val error: String? = null,val configured: Boolean = true)
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences=application.getSharedPreferences("session",Context.MODE_PRIVATE)
    private val restored=SupabaseSession.current(application)
    private val _state=MutableStateFlow(if(preferences.getBoolean("guest",false)) LoginState(uid=CloudSync.GUEST,email="Invitado · solo en este celular")
        else LoginState(uid=restored?.scope,email=restored?.email))
    val state: StateFlow<LoginState> = _state
    private var request=0
    fun signIn(email: String,password: String) {
        if(_state.value.busy)return
        if(!LoginInput.validEmail(email)||password.isEmpty()){_state.value=_state.value.copy(error="Escribe tu correo y contraseña.");return}
        val revision=++request;_state.value=_state.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                val session=withContext(Dispatchers.IO){SupabaseSession.signIn(LoginInput.email(email),password)}
                if(revision!=request)return@launch
                SupabaseSession.save(getApplication(),session)
                preferences.edit().putBoolean("guest",false).commit()
                _state.value=LoginState(uid=session.scope,email=session.email)
            }catch(e: Exception){if(revision==request)_state.value=_state.value.copy(busy=false,error=e.message ?: "Sin conexión. Tus registros permanecen en este celular.")}
        }
    }
    fun enterGuest(){request++;CloudSync.cancel(getApplication(),SupabaseSession.current(getApplication())?.scope);SupabaseSession.clear(getApplication());preferences.edit().putBoolean("guest",true).commit();_state.value=LoginState(uid=CloudSync.GUEST,email="Invitado · solo en este celular")}
    fun signOut(){request++;CloudSync.cancel(getApplication(),SupabaseSession.current(getApplication())?.scope);SupabaseSession.clear(getApplication());preferences.edit().putBoolean("guest",false).commit();_state.value=LoginState()}
    override fun onCleared(){request++}
}
