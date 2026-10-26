package com.cppmobilestudio.app.files

/**
 * Motor de pesquisa de conteúdo — Kotlin puro (testável em JVM).
 * A camada UI fornece os (caminho, conteúdo) já lidos; o motor faz a correspondência.
 */
object TextSearchEngine {

    data class SearchHit(val filePath: String, val line: Int, val column: Int, val preview: String)

    data class Options(
        val caseSensitive: Boolean = false,
        val maxHitsPerFile: Int = 20
    )

    /**
     * Pesquisa [query] em [files] (chave = caminho relativo, valor = conteúdo).
     * Devolve ocorrências por linha com uma pequena pré-visualização.
     */
    fun searchContent(
        files: Map<String, String>,
        query: String,
        options: Options = Options()
    ): List<SearchHit> {
        if (query.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        val needle = if (options.caseSensitive) query else query.lowercase()
        for ((path, content) in files) {
            val lines = content.split('\n')
            for ((idx, line) in lines.withIndex()) {
                if (hits.size >= totalCap(files.size, options)) break
                val haystack = if (options.caseSensitive) line else line.lowercase()
                var col = haystack.indexOf(needle)
                var foundInLine = 0
                while (col >= 0 && foundInLine < options.maxHitsPerFile) {
                    val previewStart = (col - 24).coerceAtLeast(0)
                    val previewEnd = (col + query.length + 24).coerceAtMost(line.length)
                    hits.add(
                        SearchHit(
                            filePath = path,
                            line = idx + 1,
                            column = col + 1,
                            preview = line.substring(previewStart, previewEnd).trim()
                        )
                    )
                    foundInLine++
                    col = haystack.indexOf(needle, col + needle.length)
                }
            }
        }
        return hits
    }

    private fun totalCap(fileCount: Int, options: Options): Int =
        (fileCount.coerceAtLeast(1)) * options.maxHitsPerFile

    /** Pesquisa por nome de ficheiro (contém, case-insensitive). */
    fun matchName(name: String, query: String, caseSensitive: Boolean = false): Boolean {
        val n = if (caseSensitive) name else name.lowercase()
        val q = if (caseSensitive) query else query.lowercase()
        return n.contains(q)
    }
}
