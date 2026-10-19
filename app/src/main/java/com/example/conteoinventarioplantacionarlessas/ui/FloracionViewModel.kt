package com.example.conteoinventarioplantacionarlessas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.conteoinventarioplantacionarlessas.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FloracionState(
    val loading: Boolean = true,
    val linea: Linea? = null,
    val lote: String = "",
    val fecha: Long? = null,
    val registros: List<RegistroFloracion> = emptyList(),
    val index: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val alertas: Map<String,String> = emptyMap(),
    val resumen: Boolean = false
) {
    val actual get() = registros.getOrNull(index)
    val completos get() = registros.count { it.estado == "COMPLETO" && FloracionValidation.complete(it.sexo, it.inflorescencias, it.botones, it.muerto) }
}

object FloracionValidation {
    fun validNumber(text: String) = text.isBlank() || (text.all { it.isDigit() } && text.toIntOrNull() != null)
    fun complete(sexo: String?, inflorescencias: Int?, botones: Int?, muerto: Boolean = false) =
        if (muerto) inflorescencias == null && botones == null else sexo in listOf("Macho", "Hembra") && inflorescencias != null && inflorescencias >= 0 &&
            (if (inflorescencias == 0) botones == null else botones != null && botones >= 0)
}

class FloracionViewModel(
    val repository: InventarioRepository,
    val censoId: Int,
    val lineaId: String,
    private val initialPosition: Int? = null,
    val reversed: Boolean = false
) : ViewModel() {
    private val _state = MutableStateFlow(FloracionState())
    val state: StateFlow<FloracionState> = _state

    init { cargar() }

    fun cargar() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val linea = requireNotNull(repository.getLineaById(lineaId)) { "No se encontró la línea." }
                val censo = requireNotNull(repository.getCensoById(censoId)) { "No se encontró el censo." }
                val registros = countingOrder(repository.prepararFloracion(censoId, lineaId),reversed) {it.posicion}
                val avance = repository.getAvanceFloracion(censoId, lineaId)
                val savedIndex = registros.indexOfFirst { it.posicion == (initialPosition ?: avance?.posicion) }
                val pending = registros.indexOfFirst { it.estado != "COMPLETO" || !FloracionValidation.complete(it.sexo, it.inflorescencias, it.botones, it.muerto) }
                _state.value = FloracionState(
                    loading = false, linea = linea,
                    lote = repository.getLoteById(linea.loteId)?.nombre ?: linea.loteId,
                    fecha = censo.fecha, registros = registros,
                    alertas = repository.getPlantas(lineaId).filter { it.alerta != null }.associate { it.codigo to it.alerta!! },
                    index = if (savedIndex >= 0) savedIndex else pending.coerceAtLeast(0),
                    resumen = initialPosition == null && pending == -1 && registros.isNotEmpty()
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = e.message ?: "No se pudo abrir la línea.") }
        }
    }

    // El criterio de sexo no oculta campos: está pendiente de confirmación.
    fun guardar(sexo: String?, infText: String, botonesText: String, completo: Boolean, muerto: Boolean = false, observacion: String = "", after: (() -> Unit)? = null) {
        val snapshot = _state.value
        val registro = snapshot.actual ?: return
        if (snapshot.busy) return
        if (!muerto && (!FloracionValidation.validNumber(infText) || !FloracionValidation.validNumber(botonesText))) {
            _state.value = snapshot.copy(error = "Usa cantidades enteras, positivas o cero, dentro del rango permitido.")
            return
        }
        val inf = if(muerto) null else infText.toIntOrNull()
        val botones = if (muerto || inf == 0) null else botonesText.toIntOrNull()
        if (completo && sexo != null && !FloracionValidation.complete(sexo, inf, botones, muerto)) {
            _state.value = snapshot.copy(error = "Selecciona el sexo y completa las cantidades. Con 0 inflorescencias no hay muestra de botones.")
            return
        }
        _state.value = snapshot.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                check(repository.draftQueue.flush()) { "No se pudo guardar el borrador." }
                val updated = registro.copy(observacion = observacion, sexo = sexo, inflorescencias = inf, botones = botones, muerto = muerto, capturado = true, verificadoEn = System.currentTimeMillis(),
                    estado = if (!completo) "BORRADOR" else if (muerto || sexo != null) "COMPLETO" else "PENDIENTE_SEXO")
                val next = if (completo) (snapshot.index + 1).coerceAtMost(snapshot.registros.lastIndex) else snapshot.index
                repository.guardarFloracionConAvance(updated, snapshot.registros[next].posicion)
                val registros = snapshot.registros.map { if (it.id == registro.id) updated else it }
                _state.value = snapshot.copy(registros = registros, index = next, busy = false, error = null,
                    message = "Guardado en el celular", resumen = completo && snapshot.index == registros.lastIndex)
                after?.invoke()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = snapshot.copy(busy = false, error = "No se guardó. Inténtalo nuevamente.") }
        }
    }

    fun eliminarArbol(after:()->Unit) {
        val snapshot=_state.value
        val row=snapshot.actual ?: return
        if(snapshot.busy) return
        _state.value=snapshot.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                repository.eliminarArbolCreado(censoId,lineaId,row.posicion)
                after()
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {_state.value=snapshot.copy(busy=false,error=e.message ?: "No se pudo eliminar el árbol.")}
        }
    }

    fun irA(index: Int) {
        val snapshot = _state.value
        if (snapshot.busy || index !in snapshot.registros.indices) return
        _state.value = snapshot.copy(busy = true)
        viewModelScope.launch {
            try {
                repository.guardarAvanceFloracion(AvanceFloracion(censoId, lineaId, snapshot.registros[index].posicion))
                _state.value = snapshot.copy(index = index, busy = false, resumen = false, message = null, error = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = snapshot.copy(busy = false, error = "No se pudo guardar el punto de trabajo.") }
        }
    }
}

class FloracionViewModelFactory(private val repository: InventarioRepository, private val censoId: Int, private val lineaId: String, private val initialPosition: Int? = null, private val reversed: Boolean = false) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FloracionViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return FloracionViewModel(repository, censoId, lineaId, initialPosition, reversed) as T
    }
}
