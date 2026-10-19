package com.example.conteoinventarioplantacionarlessas
import com.example.conteoinventarioplantacionarlessas.ui.countingOrder
import org.junit.Assert.*
import org.junit.Test
class CountDirectionTest {
    data class Tree(val id:String,val position:Int,val count:Int?)
    @Test fun reverseTraversalPreservesIdentityCountsAndOriginalList() {
        val source=listOf(Tree("P001",1,0),Tree("P020",20,null),Tree("P003",3,7))
        val reverse=countingOrder(source,true) {it.position}
        assertEquals(listOf("P020","P003","P001"),reverse.map {it.id})
        assertSame(source[1],reverse.first());assertSame(source[0],reverse.last())
        assertEquals(listOf("P001","P020","P003"),source.map {it.id})
        val resume=reverse.indexOfFirst {it.position==3}
        assertEquals("P001",reverse[resume+1].id)
        assertEquals(listOf("P001","P003","P020"),countingOrder(source,false) {it.position}.map {it.id})
    }
    @Test fun emptyAndSingleTreeLinesAreSafe() {
        assertTrue(countingOrder(emptyList<Tree>(),true) {it.position}.isEmpty())
        val only=listOf(Tree("P007",7,null))
        assertEquals(only,countingOrder(only,true) {it.position})
    }
}
