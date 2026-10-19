package com.example.conteoinventarioplantacionarlessas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.conteoinventarioplantacionarlessas.data.Censo
import com.example.conteoinventarioplantacionarlessas.data.InventarioRepository
import com.example.conteoinventarioplantacionarlessas.data.Linea
import com.example.conteoinventarioplantacionarlessas.data.Lote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine

class InventarioViewModel(private val repository: InventarioRepository) : ViewModel() {
    suspend fun agregarLinea(loteId: String, numero: String, cantidad: Int): Linea =
        repository.agregarLinea(loteId, numero, cantidad)
    suspend fun flushLocalDrafts() { check(repository.draftQueue.flush()) }

    val lotes: StateFlow<List<Lote>> = repository.getLotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCensoId = MutableStateFlow<Int?>(null)
    val selectedCensoId: StateFlow<Int?> = _selectedCensoId

    private val _selectedLote = MutableStateFlow<Lote?>(null)
    val selectedLote: StateFlow<Lote?> = _selectedLote

    private val _lineasPorLote = MutableStateFlow<List<Linea>>(emptyList())
    val lineasPorLote: StateFlow<List<Linea>> = _lineasPorLote

    fun selectCenso(id: Int) {
        _selectedCensoId.value = id
    }

    private var lineasJob: Job? = null

    fun selectLote(lote: Lote) {
        _selectedLote.value = lote
        lineasJob?.cancel()
        lineasJob = viewModelScope.launch {
            repository.getLineas(lote.id).collect {
                _lineasPorLote.value = it
            }
        }
    }

    fun loadMockData() {
        viewModelScope.launch {
            repository.loadMockData()
        }
    }

    suspend fun createOrGetCenso(tipo: String): Censo {
        run {
            _selectedCensoId.value?.let { id ->
                repository.getCensoById(id)?.takeIf { it.tipo == tipo }?.let { return it }
            }
        }
        val censo = repository.createOrGetCenso(tipo)
        selectCenso(censo.id)
        return censo
    }

    fun getCensos(tipo: String) = repository.getCensos(tipo)

    suspend fun nuevoCenso(tipo: String) {
        selectCenso(repository.nuevoCenso(tipo).id)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun avanceLineas(tipo: String) = combine(selectedCensoId,selectedLote) { id,lote -> id to lote }.flatMapLatest { (id,lote) ->
        if(id==null || lote==null) flowOf(emptyList()) else repository.getAvanceLineas(id,lote.id,tipo)
    }

    suspend fun bloquearRango(desde: String, hasta: String, responsable: String?) {
        val lote=requireNotNull(_selectedLote.value)
        val censo=requireNotNull(_selectedCensoId.value)
        val lines=_lineasPorLote.value
        val start=lines.indexOfFirst { it.id==desde }
        val end=lines.indexOfFirst { it.id==hasta }
        require(start>=0 && end>=start) { "Revisa el inicio y el fin del rango." }
        repository.bloquearLineas(censo,lote.id,lines.subList(start,end+1).map { it.id },responsable)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lineasOmitidas = selectedCensoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getLineasOmitidas(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun omitirLinea(lineaId: String) {
        viewModelScope.launch {
            _selectedCensoId.value?.let { cId ->
                repository.omitirLinea(cId, lineaId)
            }
        }
    }

    fun deshacerOmitirLinea(lineaId: String) {
        viewModelScope.launch {
            _selectedCensoId.value?.let { cId ->
                repository.deshacerOmitirLinea(cId, lineaId)
            }
        }
    }
}

class InventarioViewModelFactory(private val repository: InventarioRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventarioViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventarioViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
