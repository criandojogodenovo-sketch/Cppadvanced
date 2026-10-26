package com.cppmobilestudio.app.toolchain

/**
 * Parser de diagnósticos do compilador (Fase C/O) — Kotlin puro, testável em JVM.
 * Suporta o formato padrão clang/gcc: "ficheiro:linha:coluna: severidade: mensagem"
 * e a variante sem coluna "ficheiro:linha: severidade: mensagem".
 */
object DiagnosticParser {

    enum class Severity { ERROR, WARNING, NOTE, INFO }

    data class Diagnostic(
        val file: String,
        val line: Int,
        val column: Int,
        val severity: Severity,
        val message: String,
        val raw: String
    )

    private val LINE_REGEX = Regex(
        """^(.+?):(\d+)(?::(\d+))?:\s*(fatal error|error|warning|note|info):\s?(.*)$"""
    )

    /** Extrai diagnósticos de um log de compilação (stdout+stderr). */
    fun parse(log: String): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()
        for (rawLine in log.lineSequence()) {
            val m = LINE_REGEX.find(rawLine) ?: continue
            val file = m.groupValues[1]
            val line = m.groupValues[2].toIntOrNull() ?: continue
            val col = m.groupValues[3].toIntOrNull() ?: 1
            val sev = when (m.groupValues[4]) {
                "fatal error", "error" -> Severity.ERROR
                "warning" -> Severity.WARNING
                "note" -> Severity.NOTE
                else -> Severity.INFO
            }
            out.add(
                Diagnostic(
                    file = file,
                    line = line,
                    column = col,
                    severity = sev,
                    message = m.groupValues[5],
                    raw = rawLine
                )
            )
        }
        return out
    }

    /** Resumo legível: "3 erros, 2 avisos" ou "sem erros". */
    fun summarize(diagnostics: List<Diagnostic>): String {
        val errors = diagnostics.count { it.severity == Severity.ERROR }
        val warnings = diagnostics.count { it.severity == Severity.WARNING }
        return when {
            errors > 0 && warnings > 0 -> "$errors erro(s), $warnings aviso(s)"
            errors > 0 -> "$errors erro(s)"
            warnings > 0 -> "$warnings aviso(s)"
            else -> "sem erros"
        }
    }
}
