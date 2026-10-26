package com.cppmobilestudio.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchEngineTest {

    private val files = mapOf(
        "main.cpp" to "int main() {\n    return 0;\n}\n",
        "utils.cpp" to "int soma(int a, int b) {\n    return a + b;\n}\nreturn aux;\n"
    )

    @Test
    fun `encontra por conteudo com linha e coluna`() {
        val hits = TextSearchEngine.searchContent(files, "return")
        assertEquals(3, hits.size)
        val first = hits.first { it.filePath == "main.cpp" }
        assertEquals(2, first.line)
        assertEquals(5, first.column)
    }

    @Test
    fun `case insensitive por defeito`() {
        val hits = TextSearchEngine.searchContent(files, "RETURN")
        assertEquals(3, hits.size)
    }

    @Test
    fun `case sensitive opcional`() {
        val hits = TextSearchEngine.searchContent(
            files, "RETURN",
            TextSearchEngine.Options(caseSensitive = true)
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `consulta vazia nao devolve resultados`() {
        assertTrue(TextSearchEngine.searchContent(files, "").isEmpty())
    }

    @Test
    fun `limite de hits por ficheiro`() {
        val repetitive = mapOf("loop.cpp" to "x x x x x x x x x x\n")
        val hits = TextSearchEngine.searchContent(
            repetitive, "x",
            TextSearchEngine.Options(maxHitsPerFile = 3)
        )
        assertEquals(3, hits.size)
    }

    @Test
    fun `matchName por nome de ficheiro`() {
        assertTrue(TextSearchEngine.matchName("Main.cpp", "main"))
        assertTrue(TextSearchEngine.matchName("readme.md", "MD"))
        assertFalse(TextSearchEngine.matchName("readme.md", "cpp"))
    }
}
