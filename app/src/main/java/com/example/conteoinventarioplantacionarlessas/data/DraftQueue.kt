package com.example.conteoinventarioplantacionarlessas.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Ordered disk writes outlive navigation, without blocking the UI thread. */
class DraftQueue(private val dao: InventarioDao, private val onSaved: ()->Unit = {}) {
    companion object { private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO) }
    private data class Write(val row:BorradorFicha?,val result:CompletableDeferred<Boolean>)
    private val channel=Channel<Write>(Channel.UNLIMITED)
    init {scope.launch {
        var healthy=true
        for(write in channel) {
            if(write.row!=null) healthy=try {val changed=dao.guardarBorradorConObservacion(write.row);if(changed) runCatching {onSaved()};true} catch(e:Exception) {false}
            write.result.complete(healthy)
        }
    }}
    fun save(row:BorradorFicha):Deferred<Boolean> = CompletableDeferred<Boolean>().also {channel.trySend(Write(row,it))}
    suspend fun flush():Boolean = CompletableDeferred<Boolean>().also {channel.send(Write(null,it))}.await()
}
