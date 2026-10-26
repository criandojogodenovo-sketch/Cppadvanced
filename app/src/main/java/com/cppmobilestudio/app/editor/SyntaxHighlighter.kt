package com.cppmobilestudio.app.editor

import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.BackgroundColorSpan

/**
 * Aplica tokens como spans no Editable. Cada TokenSpan resolve a cor no momento
 * do desenho, o que permite trocar de tema sem re-tokenizar (Fase A).
 */
class TokenSpan(private val type: CppTokenizer.TokenType, palette: EditorPalette) :
    CharacterStyle() {

    private var color: Int = palette.color(type)

    fun updatePalette(palette: EditorPalette) {
        color = palette.color(type)
    }

    override fun updateDrawState(tp: android.text.TextPaint) {
        tp.color = color
    }
}

/** Paleta de cores do editor (escura e clara — Fase M: temas escuros e claros). */
class EditorPalette(dark: Boolean) {

    val isDark = dark

    val background = if (dark) Color.parseColor("#0C0C0E") else Color.parseColor("#FAFAFC")
    val foreground = if (dark) Color.parseColor("#E8E8ED") else Color.parseColor("#1A1A20")
    val gutterText = if (dark) Color.parseColor("#55555F") else Color.parseColor("#A0A0AA")
    val gutterActive = Color.parseColor("#FF8C42")
    val gutterBackground = if (dark) Color.parseColor("#111114") else Color.parseColor("#EFEFF3")
    val gutterDivider = if (dark) Color.parseColor("#2A2A31") else Color.parseColor("#D8D8DE")
    val currentLine = if (dark) Color.parseColor("#1A1A20") else Color.parseColor("#F0F0F4")
    val bracketMatch = Color.parseColor("#33FF8C42")
    val searchMatch = Color.parseColor("#3DFFC107")

    private val colors: Map<CppTokenizer.TokenType, Int> = if (dark) mapOf(
        CppTokenizer.TokenType.KEYWORD to Color.parseColor("#FF8C42"),
        CppTokenizer.TokenType.TYPE to Color.parseColor("#4FC1FF"),
        CppTokenizer.TokenType.PREPROCESSOR to Color.parseColor("#C586C0"),
        CppTokenizer.TokenType.STRING to Color.parseColor("#89D185"),
        CppTokenizer.TokenType.CHAR to Color.parseColor("#89D185"),
        CppTokenizer.TokenType.NUMBER to Color.parseColor("#B5CEA8"),
        CppTokenizer.TokenType.COMMENT to Color.parseColor("#6A737D"),
        CppTokenizer.TokenType.OPERATOR to Color.parseColor("#D4D4D4")
    ) else mapOf(
        CppTokenizer.TokenType.KEYWORD to Color.parseColor("#C25A0A"),
        CppTokenizer.TokenType.TYPE to Color.parseColor("#0B69A3"),
        CppTokenizer.TokenType.PREPROCESSOR to Color.parseColor("#8E44AD"),
        CppTokenizer.TokenType.STRING to Color.parseColor("#1E7B34"),
        CppTokenizer.TokenType.CHAR to Color.parseColor("#1E7B34"),
        CppTokenizer.TokenType.NUMBER to Color.parseColor("#3D7A1E"),
        CppTokenizer.TokenType.COMMENT to Color.parseColor("#8A9299"),
        CppTokenizer.TokenType.OPERATOR to Color.parseColor("#333338")
    )

    fun color(type: CppTokenizer.TokenType): Int = colors[type] ?: foreground
}

/** Aplicação/remoção de spans de realce sobre um Editable. */
object SyntaxHighlighter {

    /** Limite acima do qual o realce em tempo real é desativado (proteção de desempenho). */
    const val MAX_HIGHLIGHT_CHARS = 300_000

    fun apply(editable: Editable, tokens: List<CppTokenizer.Token>, palette: EditorPalette) {
        // Remove apenas os nossos spans — preserva seleção e estilos do sistema
        for (s in editable.getSpans(0, editable.length, TokenSpan::class.java)) {
            editable.removeSpan(s)
        }
        for (t in tokens) {
            if (t.end > t.start && t.start in 0 until editable.length) {
                editable.setSpan(
                    TokenSpan(t.type, palette),
                    t.start,
                    t.end.coerceAtMost(editable.length),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    fun clear(editable: Editable) {
        for (s in editable.getSpans(0, editable.length, TokenSpan::class.java)) {
            editable.removeSpan(s)
        }
    }

    /** Marca de span usada pela pesquisa (distinta do realce de sintaxe). */
    class SearchMarkSpan : BackgroundColorSpan(0)
}
