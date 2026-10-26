package com.cppmobilestudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoRedoManagerTest {

    private fun buffer(initial: String) = StringBuilderTextBuffer(StringBuilder(initial))

    @Test
    fun `grava e desfaz insercao simples`() {
        val b = buffer("int ")
        val m = UndoRedoManager()
        m.record(4, "", "x")
        assertTrue(m.canUndo)
        assertTrue(m.undo(b))
    }

    @Test
    fun `agrupa digitacao continua e desfaz de uma vez`() {
        val b = buffer("")
        val m = UndoRedoManager()
        val t0 = 1_000_000L
        m.record(0, "", "i", t0)
        m.record(1, "", "n", t0 + 100)
        m.record(2, "", "t", t0 + 200)
        assertEquals(1, countOps(m))
        // undo remove "int" inteiro
        val buf = buffer("int")
        assertTrue(m.undo(buf))
        assertEquals("", buf.toString())
    }

    @Test
    fun `nova linha quebra agrupamento`() {
        val b = buffer("")
        val m = UndoRedoManager()
        val t0 = 1_000_000L
        m.record(0, "", "a", t0)
        m.record(1, "", "\n", t0 + 50)
        assertEquals(2, countOps(m))
    }

    @Test
    fun `janela de agrupamento expira`() {
        val b = buffer("")
        val m = UndoRedoManager()
        val t0 = 1_000_000L
        m.record(0, "", "a", t0)
        m.record(1, "", "b", t0 + 10_000) // fora da janela de 900 ms
        assertEquals(2, countOps(m))
    }

    @Test
    fun `undo e redo restauram conteúdo`() {
        val b = buffer("abc")
        val m = UndoRedoManager()
        m.record(3, "", "de")
        assertTrue(m.undo(b))
        assertEquals("abc", b.toString())
        assertFalse(m.canUndo)
        assertTrue(m.canRedo)
        assertTrue(m.redo(b))
        assertEquals("abcde", b.toString())
    }

    @Test
    fun `undo de substituicao restaura removido`() {
        val b = buffer("int x = 1;")
        val m = UndoRedoManager()
        // substitui "1" por "42"
        m.record(8, "1", "42")
        assertTrue(m.undo(b))
        assertEquals("int x = 1;", b.toString())
        assertTrue(m.redo(b))
        assertEquals("int x = 42;", b.toString())
    }

    @Test
    fun `record com suppress nao grava`() {
        val m = UndoRedoManager()
        m.suppress = true
        m.record(0, "", "x")
        m.suppress = false
        assertFalse(m.canUndo)
    }

    @Test
    fun `limite de entradas respeitado`() {
        val m = UndoRedoManager(limit = 5)
        var pos = 0
        for (i in 1..20) {
            m.record(pos, "", "ch$i")
            pos += 4
        }
        var ops = 0
        val b = buffer("x".repeat(200))
        while (m.undo(b)) ops++
        assertEquals(5, ops)
    }

    private fun countOps(m: UndoRedoManager): Int {
        // conta undos possíveis sem alterar buffer real
        val b = buffer("")
        var n = 0
        while (m.undo(b)) n++
        // repõe estado com redo (buffer vazio não corresponde, mas só interessa contar)
        while (m.redo(b)) {}
        return n
    }
}
