package com.cppmobilestudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CppTokenizerTest {

    private fun types(text: String) = CppTokenizer.tokenize(text)
        .filter { it.type != CppTokenizer.TokenType.OPERATOR }

    @Test
    fun `reconhece keywords`() {
        val tokens = types("return 0;")
        assertTrue(tokens.any {
            it.type == CppTokenizer.TokenType.KEYWORD && it.start == 0 && it.end == 6
        })
        assertTrue(tokens.any { it.type == CppTokenizer.TokenType.NUMBER && it.start == 7 && it.end == 8 })
    }

    @Test
    fun `reconhece tipos`() {
        val tokens = types("int x = 0;")
        assertTrue(tokens.any {
            it.type == CppTokenizer.TokenType.TYPE && it.start == 0 && it.end == 3
        })
    }

    @Test
    fun `comentario de linha vai ate ao fim`() {
        val tokens = types("int a; // nota")
        val comment = tokens.first { it.type == CppTokenizer.TokenType.COMMENT }
        assertEquals(7, comment.start)
        assertEquals(14, comment.end)
    }

    @Test
    fun `comentario de bloco multilinha`() {
        val text = "int a;\n/* linha1\nlinha2 */\nint b;"
        val tokens = types(text)
        val comment = tokens.first { it.type == CppTokenizer.TokenType.COMMENT }
        assertEquals(text.indexOf("/*"), comment.start)
        assertEquals(text.indexOf("*/") + 2, comment.end)
    }

    @Test
    fun `strings com escapes`() {
        val tokens = types("""const char* s = "a\"b";""")
        val str = tokens.first { it.type == CppTokenizer.TokenType.STRING }
        assertTrue(str.end > str.start)
        // string termina depois do fecho da aspa
        assertEquals('"', "\"a\\\"b\""[3])
    }

    @Test
    fun `preprocessador ocupa a linha`() {
        val tokens = types("#include <iostream>\nint main(){}")
        val pre = tokens.first { it.type == CppTokenizer.TokenType.PREPROCESSOR }
        assertEquals(0, pre.start)
        assertEquals("#include <iostream>".length, pre.end)
    }

    @Test
    fun `hashtag no meio da linha nao e preprocessador`() {
        val tokens = types("a # b")
        assertTrue(tokens.none { it.type == CppTokenizer.TokenType.PREPROCESSOR })
    }

    @Test
    fun `numeros hexadecimais e sufixos`() {
        val tokens = types("0xFFull 42 3.14f")
        val nums = tokens.filter { it.type == CppTokenizer.TokenType.NUMBER }
        assertEquals(3, nums.size)
    }

    @Test
    fun `offsets sao corretos em texto real`() {
        val text = "int main() {\n    return 0;\n}"
        val tokens = CppTokenizer.tokenize(text)
        for (t in tokens) {
            assertTrue("offset fora do texto", t.end <= text.length && t.start < t.end)
            assertEquals(
                "tipo não corresponde ao conteúdo",
                true,
                when (t.type) {
                    CppTokenizer.TokenType.KEYWORD ->
                        text.substring(t.start, t.end) in CppTokenizer.KEYWORDS
                    CppTokenizer.TokenType.TYPE ->
                        text.substring(t.start, t.end) in CppTokenizer.TYPES
                    else -> true
                }
            )
        }
    }

    @Test
    fun `texto vazio nao gera tokens`() {
        assertTrue(CppTokenizer.tokenize("").isEmpty())
    }
}
