package com.example.conteoinventarioplantacionarlessas.data

object TreeInsertion {
    data class Move(val old:Planta,val newCode:String,val newPosition:Int)
    data class Plan(val code:String,val position:Int,val moves:List<Move>)
    fun number(code:String):Int {
        require(Regex("P[0-9]+").matches(code)) { "Código de árbol no compatible: $code" }
        return requireNotNull(code.drop(1).toIntOrNull()).also {require(it in 1 until Int.MAX_VALUE)}
    }
    fun code(n:Int)="P"+n.toString().padStart(3,'0')
    fun plan(plants:List<Planta>,first:Boolean):Plan {
        require(plants.map {it.codigo}.distinct().size==plants.size && plants.map {it.posicion}.distinct().size==plants.size)
        require(plants.all {it.posicion in 1 until Int.MAX_VALUE})
        val numbers=plants.map {number(it.codigo)}
        require(numbers.distinct().size==numbers.size)
        return if(first) Plan("P001",1,plants.map {Move(it,code(number(it.codigo)+1),it.posicion+1)})
        else Plan(code((numbers.maxOrNull() ?: 0)+1),(plants.maxOfOrNull {it.posicion} ?: 0)+1,emptyList())
    }
}
