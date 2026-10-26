package com.cppmobilestudio.app.toolchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticParserTest {

    @Test
    fun `parseia erro clang com linha e coluna`() {
        val log = """
            main.cpp:12:5: error: expected ';' after return statement
                return 0
                    ^
            1 error generated.
        """.trimIndent()
        val diags = DiagnosticParser.parse(log)
        assertEquals(1, diags.size)
        val d = diags[0]
        assertEquals("main.cpp", d.file)
        assertEquals(12, d.line)
        assertEquals(5, d.column)
        assertEquals(DiagnosticParser.Severity.ERROR, d.severity)
        assertTrue(d.message.contains("expected ';'"))
    }

    @Test
    fun `parseia aviso gcc sem coluna`() {
        val log = "utils.cpp:3: warning: unused variable 'x' [-Wunused-variable]"
        val diags = DiagnosticParser.parse(log)
        assertEquals(1, diags.size)
        assertEquals(DiagnosticParser.Severity.WARNING, diags[0].severity)
        assertEquals(3, diags[0].line)
        assertEquals(1, diags[0].column) // coluna ausente => 1
    }

    @Test
    fun `parseia fatal error e note`() {
        val log = """
            lib.hpp:1:1: fatal error: no such file or directory
            main.cpp:7:2: note: declared here
        """.trimIndent()
        val diags = DiagnosticParser.parse(log)
        assertEquals(2, diags.size)
        assertEquals(DiagnosticParser.Severity.ERROR, diags[0].severity)
        assertEquals(DiagnosticParser.Severity.NOTE, diags[1].severity)
    }

    @Test
    fun `ignora linhas que nao sao diagnosticos`() {
        val log = """
            [100%] Built target app
            Scanning dependencies of target app
            -- Configuring done
        """.trimIndent()
        assertTrue(DiagnosticParser.parse(log).isEmpty())
    }

    @Test
    fun `resumo conta erros e avisos`() {
        val log = """
            a.cpp:1:1: error: x
            b.cpp:2:1: warning: y
            c.cpp:3:1: warning: z
        """.trimIndent()
        val summary = DiagnosticParser.summarize(DiagnosticParser.parse(log))
        assertEquals("1 erro(s), 2 aviso(s)", summary)
    }

    @Test
    fun `log vazio resulta em nada`() {
        assertTrue(DiagnosticParser.parse("").isEmpty())
        assertEquals("sem erros", DiagnosticParser.summarize(emptyList()))
    }
}
