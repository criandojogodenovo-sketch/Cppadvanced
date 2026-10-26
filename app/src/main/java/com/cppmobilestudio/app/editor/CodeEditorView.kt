package com.cppmobilestudio.app.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs
import kotlin.math.max

/**
 * Editor de código para telemóvel (Fase A):
 *  - gutter com números de linha (linha atual destacada a laranja);
 *  - realce de sintaxe C/C++ (debounced, desativado em ficheiros grandes);
 *  - indentação automática, auto-pares e skip-over;
 *  - correspondência de chavetas;
 *  - undo/redo com agrupamento.
 *
 * Não depende de Internet nem de serviços externos.
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    var palette: EditorPalette = EditorPalette(true)
        private set

    var highlightEnabled = true
        private set

    val indentUnit: String = "    "

    /** Chamado a cada alteração de conteúdo (para autosave/dirty state). */
    var onContentModified: (() -> Unit)? = null

    /** Chamado quando o realce é automaticamente desativado (ficheiro grande). */
    var onHighlightDisabled: (() -> Unit)? = null

    /** Chamado quando muda a posição do cursor: (linha, coluna). */
    var onCursorPositionChanged: ((line: Int, col: Int) -> Unit)? = null

    val undoManager = UndoRedoManager()

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = textSize
    }
    private val gutterBgPaint = Paint().apply { style = Paint.Style.FILL }
    private val gutterDividerPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val currentLinePaint = Paint().apply { style = Paint.Style.FILL }

    private var gutterWidthPx = 0
    private val gutterMinChars = 2
    private var gutterPaddingPx = dp(6)

    private var highlightGeneration = 0L
    private var pendingRemoved: String = ""
    private var currentLine = 0

    private val bracketSpans = mutableListOf<BackgroundColorSpan>()

    private val highlightRunnable = object : Runnable {
        override fun run() {
            scheduleHighlightNow()
        }
    }

    private val recoveryRunnable = object : Runnable {
        override fun run() {
            onContentModified?.invoke()
        }
    }

    init {
        typeface = Typeface.MONOSPACE
        setHorizontallyScrolling(true)
        setSingleLine(false)
        updatePalette(true)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                pendingRemoved = if (count > 0 && s != null) {
                    s.subSequence(start, start + count).toString()
                } else ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null) {
                    val inserted = s.subSequence(start, start + count).toString()
                    undoManager.record(start, pendingRemoved, inserted)
                    if (count == 1) {
                        when (val c = inserted[0]) {
                            '\n' -> post { handleAutoIndent(start) }
                            '{', '(', '[', '"', '\'' -> post { handleAutoPair(start, c) }
                            '}', ')', ']' -> post { handleSkipOver(start, c) }
                        }
                    }
                }
                highlightGeneration++
                handler?.removeCallbacks(highlightRunnable)
                handler?.postDelayed(highlightRunnable, 220)
                handler?.removeCallbacks(recoveryRunnable)
                handler?.postDelayed(recoveryRunnable, 1500)
                onContentModified?.invoke()
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        addTextChangedListener(watcher)
    }

    // ── Tema ─────────────────────────────────────────────────────────────────

    fun updatePalette(dark: Boolean) {
        palette = EditorPalette(dark)
        setTextColor(palette.foreground)
        setHintTextColor(palette.gutterText)
        gutterBgPaint.color = palette.gutterBackground
        gutterDividerPaint.color = palette.gutterDivider
        currentLinePaint.color = palette.currentLine
        gutterPaint.color = palette.gutterText
        if (highlightEnabled) scheduleHighlightNow()
        invalidate()
    }

    fun setHighlightEnabled(enabled: Boolean) {
        highlightEnabled = enabled
        if (!enabled) {
            text?.let { SyntaxHighlighter.clear(it) }
        } else {
            scheduleHighlightNow()
        }
    }

    // ── Gutter e desenho ─────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        // Fundo da linha atual ANTES do texto (para não o tapar)
        if (layout != null && currentLine in 0 until layout.lineCount) {
            val top = layout.getLineTop(currentLine)
            val bottom = layout.getLineBottom(currentLine)
            canvas.drawRect(
                scrollX.toFloat(), top.toFloat(),
                (scrollX + width).toFloat(), bottom.toFloat(), currentLinePaint
            )
        }
        super.onDraw(canvas)
        if (layout == null) return

        val scrollYv = scrollY
        val scrollXv = scrollX

        // Fundo do gutter fixo no ecrã (coordenadas de conteúdo + scroll)
        val viewH = height - paddingTop - paddingBottom

        val firstLine = layout.getLineForVertical(max(0, scrollYv))
        val lastLine = layout.getLineForVertical(max(0, scrollYv + viewH))
        gutterPaint.color = palette.gutterText
        for (line in firstLine..lastLine.coerceAtMost(layout.lineCount - 1)) {
            val baseline = layout.getLineBaseline(line).toFloat()
            if (baseline < scrollYv || baseline > scrollYv + height) continue
            val num = (line + 1).toString()
            val x = (scrollXv + gutterWidthPx - gutterPaddingPx - gutterPaint.measureText(num))
            if (line == currentLine) {
                gutterPaint.color = palette.gutterActive
            }
            canvas.drawText(num, x, baseline, gutterPaint)
            if (line == currentLine) {
                gutterPaint.color = palette.gutterText
            }
        }

        // Separador vertical
        canvas.drawLine(
            (scrollXv + gutterWidthPx).toFloat(), scrollYv.toFloat(),
            (scrollXv + gutterWidthPx).toFloat(), (scrollYv + height).toFloat(),
            gutterDividerPaint
        )

        // Recalcular largura do gutter conforme nº de dígitos
        val digits = max(gutterMinChars, layout.lineCount.toString().length)
        val wanted = (gutterPaint.measureText("8".repeat(digits)) + gutterPaddingPx * 2).toInt()
        if (wanted != gutterWidthPx) {
            gutterWidthPx = wanted
            setPadding(gutterWidthPx + dp(6), paddingTop, paddingRight, paddingBottom)
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val layout = layout ?: return
        currentLine = layout.getLineForOffset(selStart)
        onCursorPositionChanged?.invoke(currentLine + 1, selStart - layout.getLineStart(currentLine) + 1)
        updateBracketMatching(selStart)
        invalidate()
    }

    // ── Realce de sintaxe ────────────────────────────────────────────────────

    private fun scheduleHighlightNow() {
        val editable = text ?: return
        if (!highlightEnabled) return
        if (editable.length > SyntaxHighlighter.MAX_HIGHLIGHT_CHARS) {
            onHighlightDisabled?.invoke()
            return
        }
        val gen = ++highlightGeneration
        val content = editable.toString()
        val pal = palette
        Thread {
            val tokens = CppTokenizer.tokenize(content)
            post {
                val current = text
                if (current != null && gen == highlightGeneration) {
                    SyntaxHighlighter.apply(current, tokens, pal)
                }
            }
        }.start()
    }

    // ── Auto-indent / pares / skip-over ──────────────────────────────────────

    private fun handleAutoIndent(nlPos: Int) {
        val s = text ?: return
        if (nlPos >= s.length || s[nlPos] != '\n') return
        val lineStart = (s.lastIndexOf('\n', (nlPos - 1).coerceAtLeast(0)) + 1)
            .coerceIn(0, nlPos)
        val line = s.subSequence(lineStart, nlPos).toString()
        val leading = line.takeWhile { it == ' ' || it == '\t' }
        val extra = if (line.trimEnd().endsWith('{')) indentUnit else ""
        val toInsert = leading + extra
        if (toInsert.isEmpty()) return
        undoManager.suppress = true
        try {
            s.insert(nlPos + 1, toInsert)
            setSelection(nlPos + 1 + toInsert.length)
        } finally {
            undoManager.suppress = false
        }
    }

    private fun handleAutoPair(pos: Int, opened: Char) {
        val s = text ?: return
        if (pos >= s.length || s[pos] != opened) return
        // Auto-pares só quando o próximo caractere é fim/branco/fecho
        val nextOk = pos + 1 >= s.length || s[pos + 1].isWhitespace() ||
            s[pos + 1] in ")]}\"'`;,."
        if (!nextOk) return
        val closer = when (opened) {
            '{' -> '}'
            '(' -> ')'
            '[' -> ']'
            '"' -> '"'
            '\'' -> '\''
            else -> return
        }
        // Aspas: se o próximo caractere já for a própria aspa, deixa o skip-over atuar
        if (opened == closer && pos + 1 < s.length && s[pos + 1] == opened) return
        if (opened == closer && pos > 0 && s[pos - 1] == '\\') return
        undoManager.suppress = true
        try {
            s.insert(pos + 1, closer.toString())
            setSelection(pos + 1)
        } finally {
            undoManager.suppress = false
        }
    }

    private fun handleSkipOver(pos: Int, typed: Char) {
        val s = text ?: return
        // Se o utilizador escreveu um fecho imediatamente antes de outro igual,
        // Remove o duplicado e salta por cima (skip-over)
        if (pos + 1 < s.length && s[pos] == typed && s[pos + 1] == typed) {
            undoManager.suppress = true
            try {
                s.delete(pos, pos + 1)
                setSelection(pos + 1)
            } finally {
                undoManager.suppress = false
            }
            return
        }
        // Auto-dedent: '}' quando a linha até ao cursor só tem espaço
        if (typed == '}') {
            val lineStart = (s.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)) + 1).coerceIn(0, pos)
            val before = s.subSequence(lineStart, pos)
            if (before.isNotEmpty() && before.all { it == ' ' || it == '\t' }) {
                val toRemove = minOf(indentUnit.length, before.length)
                undoManager.suppress = true
                try {
                    s.delete(pos - toRemove, pos)
                    setSelection(pos - toRemove)
                } finally {
                    undoManager.suppress = false
                }
            }
        }
    }

    // ── Bracket matching ─────────────────────────────────────────────────────

    private fun updateBracketMatching(cursor: Int) {
        val s = text ?: return
        clearBracketSpans()
        if (s.length > 200_000) return
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')
        val opens = pairs.keys
        val closes = pairs.values

        val idx = if (cursor > 0 && (s[cursor - 1] in opens || s[cursor - 1] in closes)) {
            cursor - 1
        } else if (cursor < s.length && (s[cursor] in opens || s[cursor] in closes)) {
            cursor
        } else return

        val ch = s[idx]
        val match = if (ch in opens) {
            findForward(s, idx, ch, pairs[ch]!!)
        } else {
            findBackward(s, idx, ch, opens.firstOrNull { pairs[it] == ch } ?: return)
        } ?: return

        addBracketSpan(minOf(idx, match), maxOf(idx, match) + 1)
    }

    private fun findForward(s: Editable, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        val limit = minOf(s.length, from + 50_000)
        for (i in from until limit) {
            when (s[i]) {
                open -> depth++
                close -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }

    private fun findBackward(s: Editable, from: Int, close: Char, open: Char): Int? {
        var depth = 0
        val limit = maxOf(0, from - 50_000)
        for (i in from downTo limit) {
            when (s[i]) {
                close -> depth++
                open -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }

    private fun addBracketSpan(start: Int, end: Int) {
        val span = BackgroundColorSpan(palette.bracketMatch)
        bracketSpans.add(span)
        text?.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun clearBracketSpans() {
        val s = text ?: return
        for (span in bracketSpans) s.removeSpan(span)
        bracketSpans.clear()
    }

    // ── API pública para atividades ──────────────────────────────────────────

    /** Insere texto na posição do cursor (usado pela barra de símbolos). */
    fun insertAtCursor(value: String) {
        val s = text ?: return
        val start = selectionStart.coerceIn(0, s.length)
        val end = selectionEnd.coerceIn(0, s.length)
        s.replace(minOf(start, end), maxOf(start, end), value)
        setSelection(minOf(start, end) + value.length)
        requestFocus()
    }

    fun performUndo(): Boolean {
        val s = text ?: return false
        return undoManager.undo(EditableTextBuffer(s))
    }

    fun performRedo(): Boolean {
        val s = text ?: return false
        return undoManager.redo(EditableTextBuffer(s))
    }

    fun goToLine(line: Int): Boolean {
        val layout = layout ?: return false
        if (line < 1 || line > layout.lineCount) return false
        val offset = layout.getLineStart(line - 1)
        requestFocus()
        setSelection(offset)
        bringPointIntoView(offset)
        return true
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        return super.onTextContextMenuItem(id)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
