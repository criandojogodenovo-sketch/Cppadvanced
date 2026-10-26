package com.cppmobilestudio.app.files

import java.io.File

/**
 * Validação e normalização de caminhos — proteção contra path traversal.
 * Toda operação de ficheiros do C++ Mobile Studio passa por aqui (Fase N).
 */
object SafePath {

    /** Nomes de ficheiro/pasta proibidos ou perigosos. */
    private val RESERVED_WINDOWS_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Valida um nome de ficheiro/pasta novo (sem caminho).
     * Recusa: vazio, ".", "..", caracteres de controlo, '/', '\', ':' e
     * nomes reservados do Windows (para ZIPs portáveis).
     */
    fun isValidEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name == "." || name == "..") return false
        if (name.contains('/') || name.contains('\\') || name.contains(':')) return false
        if (name.contains('\u0000')) return false
        if (name.any { it.code < 0x20 }) return false
        if (name.endsWith(".") || name.endsWith(" ")) return false
        val upper = name.uppercase().substringBefore('.')
        if (upper in RESERVED_WINDOWS_NAMES) return false
        return true
    }

    /**
     * Resolve [relative] dentro de [base], garantindo que o resultado
     * permanece estritamente dentro de [base].
     * Devolve null se o caminho escapar da base (ataque de traversal) ou for inválido.
     */
    fun resolveInside(base: File, relative: String): File? {
        if (relative.contains('\u0000')) return null
        val r = relative.trim()
        // Caminhos absolutos são recusados: apenas caminhos relativos à base são aceites.
        // (o trimStart('/') antigo transformava "/etc/passwd" em "etc/passwd" — risco de traversal)
        if (r.startsWith("/") || r.startsWith("\\")) return null
        val baseCanonical = base.canonicalFile
        val candidate = File(baseCanonical, r)
        val candidateCanonical = try {
            candidate.canonicalFile
        } catch (_: Exception) {
            return null
        }
        return if (isInside(baseCanonical, candidateCanonical)) candidateCanonical else null
    }

    /** Verdadeiro se [child] for igual a [parent] ou estiver dentro dele (canónico). */
    fun isInside(parent: File, child: File): Boolean {
        var cur: File? = child
        val parentPath = parent.canonicalFile.path
        while (cur != null) {
            if (cur.canonicalFile.path == parentPath) return true
            cur = cur.parentFile
        }
        return false
    }

    /**
     * Valida o nome de entrada de um ZIP antes de extrair (zip-slip).
     * Recusa caminhos absolutos, "..", unidades de disco e entradas nulas.
     */
    fun isSafeZipEntryName(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        if (entryName.contains('\u0000')) return false
        if (entryName.startsWith("/") || entryName.startsWith("\\")) return false
        if (entryName.contains(':')) return false // "C:\..." e nomes alternativos NTFS
        val parts = entryName.replace('\\', '/').split('/')
        // A entrada pode referir-se a um diretório implícito, todos os segmentos têm de ser válidos
        return parts.filter { it.isNotBlank() }.all { isValidEntryName(it) || it == "." }
    }
}
