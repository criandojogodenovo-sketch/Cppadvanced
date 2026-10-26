package com.cppmobilestudio.app.editor

/**
 * Abstração mínima de buffer de texto para o UndoRedoManager —
 * permite testes JVM puros e integração com android.text.Editable.
 */
interface TextBuffer {
    fun length(): Int
    fun delete(start: Int, end: Int)
    fun insert(where: Int, text: String)
}

/** Implementação sobre android.text.Editable. */
class EditableTextBuffer(private val editable: android.text.Editable) : TextBuffer {
    override fun length(): Int = editable.length
    override fun delete(start: Int, end: Int) {
        val s = start.coerceIn(0, editable.length)
        val e = end.coerceIn(s, editable.length)
        if (e > s) editable.delete(s, e)
    }
    override fun insert(where: Int, text: String) {
        val w = where.coerceIn(0, editable.length)
        editable.insert(w, text)
    }
}

/** Implementação simples sobre StringBuilder (testes JVM). */
class StringBuilderTextBuffer(private val sb: StringBuilder = StringBuilder()) : TextBuffer {
    override fun length(): Int = sb.length
    override fun delete(start: Int, end: Int) {
        val s = start.coerceIn(0, sb.length)
        val e = end.coerceIn(s, sb.length)
        sb.delete(s, e)
    }
    override fun insert(where: Int, text: String) {
        sb.insert(where.coerceIn(0, sb.length), text)
    }
    override fun toString(): String = sb.toString()
}

/**
 * Gestor de desfazer/refazer com agrupamento temporal de inserções contíguas
 * (Fase A). Lógica pura — testável em JVM.
 */
class UndoRedoManager(private val limit: Int = 200) {

    data class Op(
        val start: Int,
        val removed: String,
        val inserted: String,
        val timestamp: Long
    )

    private val undoStack = ArrayDeque<Op>()
    private val redoStack = ArrayDeque<Op>()

    /** Ativo quando a própria app modifica o texto (undo/redo/replaces) — não registar. */
    @Volatile
    var suppress: Boolean = false

    private val mergeWindowMs = 900L

    fun record(start: Int, removed: String, inserted: String, now: Long = System.currentTimeMillis()) {
        if (suppress) return
        if (removed.isEmpty() && inserted.isEmpty()) return
        val last = undoStack.lastOrNull()
        if (last != null &&
            removed.isEmpty() && last.removed.isEmpty() &&
            inserted.length == 1 && last.inserted.length >= 1 &&
            inserted != "\n" &&                    // nova linha nunca se agrupa (nova operação)
            last.start + last.inserted.length == start &&
            now - last.timestamp <= mergeWindowMs &&
            '\n' !in last.inserted
        ) {
            // agrupa escrita contínua de caracteres
            undoStack.removeLast()
            undoStack.addLast(Op(last.start, "", last.inserted + inserted, now))
        } else {
            undoStack.addLast(Op(start, removed, inserted, now))
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Aplica undo sobre [buffer]. Devolve true se algo mudou. */
    fun undo(buffer: TextBuffer): Boolean {
        val op = undoStack.lastOrNull() ?: return false
        undoStack.removeLast()
        suppress = true
        try {
            val end = op.start + op.inserted.length
            if (op.inserted.isNotEmpty()) {
                buffer.delete(op.start.coerceIn(0, buffer.length()), end.coerceIn(0, buffer.length()))
            }
            if (op.removed.isNotEmpty()) {
                buffer.insert(op.start.coerceAtMost(buffer.length()), op.removed)
            }
        } finally {
            suppress = false
        }
        redoStack.addLast(op)
        return true
    }

    /** Aplica redo sobre [buffer]. Devolve true se algo mudou. */
    fun redo(buffer: TextBuffer): Boolean {
        val op = redoStack.lastOrNull() ?: return false
        redoStack.removeLast()
        suppress = true
        try {
            if (op.removed.isNotEmpty()) {
                val at = op.start.coerceAtMost(buffer.length())
                buffer.delete(at, (at + op.removed.length).coerceAtMost(buffer.length()))
            }
            if (op.inserted.isNotEmpty()) {
                buffer.insert(op.start.coerceAtMost(buffer.length()), op.inserted)
            }
        } finally {
            suppress = false
        }
        undoStack.addLast(op)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
