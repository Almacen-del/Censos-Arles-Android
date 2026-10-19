package com.example.conteoinventarioplantacionarlessas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.conteoinventarioplantacionarlessas.data.InventarioRepository
import com.example.conteoinventarioplantacionarlessas.data.Linea
import com.example.conteoinventarioplantacionarlessas.data.RegistroFrutos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FrutosViewModel(
    val repository: InventarioRepository,
    val censoId: Int,
    val lineaId: String,
    private val initialPosition: Int? = null
) : ViewModel() {

    private val _loteNombre = MutableStateFlow("")
    val loteNombre: StateFlow<String> = _loteNombre

    private val _linea = MutableStateFlow<Linea?>(null)
    val linea: StateFlow<Linea?> = _linea

    private val _registros = MutableStateFlow<List<RegistroFrutos>>(emptyList())
    val registros: StateFlow<List<RegistroFrutos>> = _registros

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    val currentRegistro: RegistroFrutos?
        get() = _registros.value.getOrNull(_currentIndex.value)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _alertas = MutableStateFlow<Map<String,String>>(emptyMap())
    val alertas: StateFlow<Map<String,String>> = _alertas
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    init {
        viewModelScope.launch {
            try {
                _linea.value = repository.getLineaById(lineaId)
                _loteNombre.value = _linea.value?.let { repository.getLoteById(it.loteId)?.nombre } ?: ""
                _alertas.value = repository.getPlantas(lineaId).filter { it.alerta != null }.associate { it.codigo to it.alerta!! }
                _registros.value = repository.prepararFrutos(censoId,lineaId)
                _currentIndex.value = _registros.value.indexOfFirst { it.posicion == initialPosition }.takeIf { it>=0 } ?: _registros.value.indexOfFirst { it.estado != "COMPLETO" }.coerceAtLeast(0)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { _error.value=e.message ?: "No se pudo abrir el catálogo." }
        }
    }

    fun saveAndNext(sexo: String?, frutos: Int?, muerto: Boolean) {
        val registro = currentRegistro ?: return
        if (_busy.value || (!muerto && sexo == "Hembra" && (frutos == null || frutos < 0))) return
        _busy.value=true
        viewModelScope.launch {
            try {
                val updated=registro.copy(sexo=sexo, muerto=muerto, capturado=true, verificadoEn=System.currentTimeMillis(),
                    frutos=if(muerto || sexo != "Hembra") null else frutos,
                    estado=if(!muerto && sexo==null) "PENDIENTE_SEXO" else "COMPLETO")
                repository.saveRegistroFrutos(updated)
                _registros.value=_registros.value.map { if(it.id==registro.id) updated else it }
                if (_currentIndex.value < _registros.value.lastIndex) _currentIndex.value++ else _finished.value=true
                _error.value=null
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { _error.value="No se guardó. Inténtalo nuevamente." }
            finally { _busy.value=false }
        }
    }

    fun goBack() {
        if (!_busy.value && _currentIndex.value > 0) {
            _currentIndex.value -= 1
        }
    }
}

class FrutosViewModelFactory(
    private val repository: InventarioRepository,
    private val censoId: Int,
    private val lineaId: String, private val initialPosition: Int? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FrutosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FrutosViewModel(repository, censoId, lineaId, initialPosition) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
