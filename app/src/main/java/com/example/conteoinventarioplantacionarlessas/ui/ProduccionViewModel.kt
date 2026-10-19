package com.example.conteoinventarioplantacionarlessas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.conteoinventarioplantacionarlessas.data.InventarioRepository
import com.example.conteoinventarioplantacionarlessas.data.Linea
import com.example.conteoinventarioplantacionarlessas.data.RegistroProduccion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProduccionViewModel(
    val repository: InventarioRepository,
    val censoId: Int,
    val lineaId: String
) : ViewModel() {

    private val _loteNombre = MutableStateFlow("")
    val loteNombre: StateFlow<String> = _loteNombre

    private val _linea = MutableStateFlow<Linea?>(null)
    val linea: StateFlow<Linea?> = _linea

    private val _registro = MutableStateFlow<RegistroProduccion?>(null)
    val registro: StateFlow<RegistroProduccion?> = _registro

    init {
        viewModelScope.launch {
            _linea.value = repository.getLineaById(lineaId)
                _loteNombre.value = _linea.value?.let { repository.getLoteById(it.loteId)?.nombre } ?: ""
            val reg = repository.getRegistroProduccion(censoId, lineaId)
            if (reg == null && _linea.value != null) {
                val newReg = RegistroProduccion(
                    censoId = censoId,
                    loteId = _linea.value!!.loteId,
                    lineaId = lineaId,
                    vivos = null,
                    muertos = null,
                    resiembras = null,
                    observacion = null,
                    estado = "BORRADOR"
                )
                repository.saveRegistroProduccion(newReg)
                _registro.value = repository.getRegistroProduccion(censoId, lineaId)
            } else {
                _registro.value = reg
            }
        }
    }

    private val _busy=MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error=MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun save(vivos: Int?, muertos: Int?, resiembras: Int?, observacion: String?, after: ()->Unit = {}) {
        if(_busy.value || listOf(vivos,muertos,resiembras).any {it==null || it<0}) return
        _busy.value=true
        viewModelScope.launch {
            try {
            _registro.value?.let { reg ->
                val newReg = reg.copy(
                    vivos = vivos,
                    muertos = muertos,
                    resiembras = resiembras,
                    observacion = observacion,
                    estado = "COMPLETO"
                )
                repository.saveRegistroProduccion(newReg)
                _registro.value = newReg
                after()
            }
            } catch(e: kotlinx.coroutines.CancellationException) {throw e}
            catch(e: Exception) {_error.value="No se guardó. Inténtalo de nuevo."}
            finally {_busy.value=false}
        }
    }
}

class ProduccionViewModelFactory(
    private val repository: InventarioRepository,
    private val censoId: Int,
    private val lineaId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProduccionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProduccionViewModel(repository, censoId, lineaId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
