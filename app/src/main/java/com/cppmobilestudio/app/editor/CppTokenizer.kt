package com.cppmobilestudio.app.editor

/**
 * Tokenizador C/C++ manual (sem regex) — desenhado para ser rápido em telemóveis
 * e testável em JVM pura (Fase A / ETAPA 2).
 */
object CppTokenizer {

    enum class TokenType {
        KEYWORD, TYPE, PREPROCESSOR, STRING, CHAR, NUMBER, COMMENT, OPERATOR
    }

    data class Token(val type: TokenType, val start: Int, val end: Int)

    val KEYWORDS = setOf(
        "alignas", "alignof", "and", "and_eq", "asm", "auto", "break", "case",
        "catch", "class", "co_await", "co_return", "co_yield", "compl", "concept",
        "const", "consteval", "constexpr", "constinit", "const_cast", "continue",
        "decltype", "default", "delete", "do", "dynamic_cast", "else", "enum",
        "explicit", "export", "extern", "false", "for", "friend", "goto", "if",
        "inline", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
        "nullptr", "operator", "or", "or_eq", "private", "protected", "public",
        "register", "reinterpret_cast", "requires", "return", "sizeof",
        "static", "static_assert", "static_cast", "struct", "switch", "template",
        "this", "thread_local", "throw", "true", "try", "typedef", "typeid",
        "typename", "union", "using", "virtual", "volatile", "while", "xor", "xor_eq",
        // C
        "_Bool", "_Complex", "_Imaginary", "restrict", "_Alignas", "_Alignof",
        "_Static_assert", "_Thread_local"
    )

    val TYPES = setOf(
        "int", "short", "long", "float", "double", "char", "bool", "void",
        "signed", "unsigned", "wchar_t", "char8_t", "char16_t", "char32_t",
        "size_t", "ssize_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t",
        "int8_t", "int16_t", "int32_t", "int64_t", "intptr_t", "uintptr_t",
        "string", "wstring", "vector", "map", "set", "unordered_map", "unordered_set",
        "pair", "array", "list", "deque", "queue", "stack", "optional", "variant",
        "unique_ptr", "shared_ptr", "weak_ptr", "function", "tuple", "thread",
        "mutex", "ostream", "istream", "iostream", "ofstream", "ifstream",
        "byte", "string_view", "span"
    )

    private val OPERATOR_CHARS = "+-*/%=<>!&|^~?:"

    /** Tokeniza [text] completo. Complexidade O(n); sem alocações desnecessárias. */
    fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>(text.length / 6 + 16)
        val n = text.length
        var i = 0

        fun lineStartBefore(idx: Int): Int {
            var j = idx - 1
            while (j >= 0 && text[j] != '\n') j--
            return j + 1
        }

        fun onlyWhitespaceBefore(idx: Int): Boolean {
            val ls = lineStartBefore(idx)
            for (k in ls until idx) {
                if (!text[k].isWhitespace()) return false
            }
            return true
        }

        while (i < n) {
            val c = text[i]
            when {
                c == '\n' || c == '\r' || c == ' ' || c == '\t' -> i++

                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) n else it }
                    tokens.add(Token(TokenType.COMMENT, i, end))
                    i = end
                }

                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2)
                    val e = if (end == -1) n else end + 2
                    tokens.add(Token(TokenType.COMMENT, i, e))
                    i = e
                }

                c == '"' -> {
                    var j = i + 1
                    while (j < n) {
                        val sc = text[j]
                        if (sc == '\\' && j + 1 < n) { j += 2; continue }
                        if (sc == '"' || sc == '\n') { j += if (sc == '"') 1 else 0; break }
                        j++
                    }
                    tokens.add(Token(TokenType.STRING, i, j.coerceAtMost(n)))
                    i = j
                }

                c == '\'' -> {
                    var j = i + 1
                    while (j < n) {
                        val sc = text[j]
                        if (sc == '\\' && j + 1 < n) { j += 2; continue }
                        if (sc == '\'' || sc == '\n') { j += if (sc == '\'') 1 else 0; break }
                        j++
                    }
                    tokens.add(Token(TokenType.CHAR, i, j.coerceAtMost(n)))
                    i = j
                }

                c == '#' && onlyWhitespaceBefore(i) -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) n else it }
                    tokens.add(Token(TokenType.PREPROCESSOR, i, end))
                    i = end
                }

                c.isDigit() -> {
                    var j = i
                    if (c == '0' && j + 1 < n && (text[j + 1] == 'x' || text[j + 1] == 'X')) {
                        j += 2
                        while (j < n && (text[j].isDigit() || text[j] in 'a'..'f' ||
                                    text[j] in 'A'..'F' || text[j] == '\'' )
                        ) j++
                    } else if (c == '0' && j + 1 < n && (text[j + 1] == 'b' || text[j + 1] == 'B')) {
                        j += 2
                        while (j < n && (text[j] == '0' || text[j] == '1' || text[j] == '\'')) j++
                    } else {
                        while (j < n && (text[j].isDigit() || text[j] == '.' || text[j] == '\'')) j++
                        if (j < n && (text[j] == 'e' || text[j] == 'E')) {
                            var k = j + 1
                            if (k < n && (text[k] == '+' || text[k] == '-')) k++
                            if (k < n && text[k].isDigit()) j = k
                        }
                    }
                    // sufixos: u, l, f, z e combinações
                    while (j < n && text[j].lowercaseChar() in "ulzf") j++
                    tokens.add(Token(TokenType.NUMBER, i, j))
                    i = j
                }

                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val word = text.substring(i, j)
                    val type = when {
                        word in KEYWORDS -> TokenType.KEYWORD
                        word in TYPES -> TokenType.TYPE
                        else -> null
                    }
                    if (type != null) tokens.add(Token(type, i, j))
                    i = j
                }

                c in OPERATOR_CHARS -> {
                    tokens.add(Token(TokenType.OPERATOR, i, i + 1))
                    i++
                }

                else -> i++ // pontuação e outros: sem span (cor padrão)
            }
        }
        return tokens
    }
}
