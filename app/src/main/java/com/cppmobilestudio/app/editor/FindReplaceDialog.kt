package com.cppmobilestudio.app.editor

import android.app.Activity
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.cppmobilestudio.app.R
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Pesquisa e substituição reais sobre o conteúdo do editor (Fase A):
 * opções de maiúsculas, palavra completa e regex; navegação e substituição.
 */
class FindReplaceDialog(private val activity: Activity, private val editor: CodeEditorView) {

    private var matches: List<IntRange> = emptyList()
    private var currentIndex = -1
    private val markSpans = mutableListOf<BackgroundColorSpan>()
    private var dialog: AlertDialog? = null

    fun show(initialQuery: String = "") {
        val view = activity.layoutInflater.inflate(R.layout.dialog_find_replace, null)
        val findInput = view.findViewById<EditText>(R.id.findInput)
        val replaceInput = view.findViewById<EditText>(R.id.replaceInput)
        val chkCase = view.findViewById<CheckBox>(R.id.chkCase)
        val chkWord = view.findViewById<CheckBox>(R.id.chkWholeWord)
        val chkRegex = view.findViewById<CheckBox>(R.id.chkRegex)
        val matchInfo = view.findViewById<TextView>(R.id.matchInfo)
        val btnPrev = view.findViewById<Button>(R.id.btnPrev)
        val btnNext = view.findViewById<Button>(R.id.btnNext)
        val btnReplaceOne = view.findViewById<Button>(R.id.btnReplaceOne)
        val btnReplaceAll = view.findViewById<Button>(R.id.btnReplaceAll)

        findInput.setText(initialQuery)
        findInput.setSelection(findInput.text?.length ?: 0)

        fun refresh() {
            matches = computeMatches(findInput, chkCase, chkWord, chkRegex)
            matchInfo.text = if (matches.isEmpty()) {
                activity.getString(R.string.no_matches)
            } else {
                activity.getString(R.string.match_count, matches.size)
            }
            paintMarks()
        }

        findInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = refresh()
        })
        chkCase.setOnCheckedChangeListener { _, _ -> refresh() }
        chkWord.setOnCheckedChangeListener { _, _ -> refresh() }
        chkRegex.setOnCheckedChangeListener { _, _ -> refresh() }

        btnNext.setOnClickListener {
            if (matches.isEmpty()) return@setOnClickListener
            val cursor = editor.selectionEnd
            currentIndex = matches.indexOfFirst { it.first >= cursor }
            if (currentIndex == -1) currentIndex = 0
            jumpTo(matches[currentIndex])
        }
        btnPrev.setOnClickListener {
            if (matches.isEmpty()) return@setOnClickListener
            val cursor = editor.selectionStart
            currentIndex = matches.indexOfLast { it.last < cursor }
            if (currentIndex == -1) currentIndex = matches.size - 1
            jumpTo(matches[currentIndex])
        }
        btnReplaceOne.setOnClickListener {
            if (matches.isEmpty()) return@setOnClickListener
            val cursor = editor.selectionStart.coerceAtLeast(0)
            val target = matches.firstOrNull { it.first >= cursor || it.contains(cursor) }
                ?: matches.first()
            val replacement = replaceInput.text?.toString() ?: ""
            editor.text?.replace(target.first, target.last + 1, replacement)
            refresh()
        }
        btnReplaceAll.setOnClickListener {
            val replacement = replaceInput.text?.toString() ?: ""
            var count = 0
            val editable = editor.text ?: return@setOnClickListener
            // aplica de trás para a frente para preservar offsets
            for (m in matches.asReversed()) {
                editable.replace(m.first, m.last + 1, replacement)
                count++
            }
            Toast.makeText(
                activity,
                activity.getString(R.string.replaced_count, count),
                Toast.LENGTH_SHORT
            ).show()
            refresh()
        }

        refresh()

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.find_replace)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .create()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog?.show()
    }

    private fun computeMatches(
        findInput: EditText,
        chkCase: CheckBox,
        chkWord: CheckBox,
        chkRegex: CheckBox
    ): List<IntRange> {
        val text = editor.text?.toString() ?: return emptyList()
        val query = findInput.text?.toString() ?: return emptyList()
        if (query.isEmpty()) return emptyList()

        return try {
            if (chkRegex.isChecked) {
                val flags = if (chkCase.isChecked) 0 else Pattern.CASE_INSENSITIVE
                val p = Pattern.compile(query, flags)
                val m = p.matcher(text)
                buildList {
                    while (m.find()) {
                        add(IntRange(m.start(), m.end() - 1))
                        if (size > 500) break
                    }
                }
            } else {
                val hay = if (chkCase.isChecked) text else text.lowercase()
                val needle = if (chkCase.isChecked) query else query.lowercase()
                val out = ArrayList<IntRange>()
                var idx = hay.indexOf(needle)
                while (idx >= 0 && out.size <= 500) {
                    val end = idx + needle.length - 1
                    var ok = true
                    if (chkWord.isChecked) {
                        val beforeOk = idx == 0 || !wordChar(hay[idx - 1])
                        val afterOk = end + 1 >= hay.length || !wordChar(hay[end + 1])
                        ok = beforeOk && afterOk
                    }
                    if (ok) out.add(IntRange(idx, end))
                    idx = hay.indexOf(needle, idx + maxOf(1, needle.length))
                }
                out
            }
        } catch (_: PatternSyntaxException) {
            Toast.makeText(activity, R.string.invalid_regex, Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    private fun wordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun jumpTo(range: IntRange) {
        editor.requestFocus()
        editor.setSelection(range.first, range.last + 1)
        editor.bringPointIntoView(range.first)
    }

    private fun paintMarks() {
        val editable = editor.text ?: return
        for (s in markSpans) editable.removeSpan(s)
        markSpans.clear()
        for (m in matches) {
            if (m.last < editable.length) {
                val span = BackgroundColorSpan(editor.palette.searchMatch)
                markSpans.add(span)
                editable.setSpan(span, m.first, m.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun clearMarks() {
        val editable = editor.text ?: return
        for (s in markSpans) editable.removeSpan(s)
        markSpans.clear()
        matches = emptyList()
    }
}
