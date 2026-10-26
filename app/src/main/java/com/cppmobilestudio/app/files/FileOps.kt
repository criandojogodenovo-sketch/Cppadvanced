package com.cppmobilestudio.app.files

import java.io.File
import java.io.IOException

/**
 * Operações de ficheiros seguras: escrita atómica, cópia recursiva e
 * eliminação com verificação de limites (Fase N — Segurança e estabilidade).
 */
object FileOps {

    /**
     * Escrita atómica: grava para um ficheiro temporário no mesmo diretório
     * e faz rename. Evita corrupção se a app morrer a meio da gravação.
     */
    @Throws(IOException::class)
    fun writeAtomic(target: File, content: String) {
        val parent = target.parentFile ?: throw IOException("Sem diretório pai: ${target.path}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Não foi possível criar o diretório: ${parent.path}")
        }
        val tmp = File(parent, target.name + ".tmp-${System.nanoTime()}")
        try {
            tmp.writeText(content, Charsets.UTF_8)
            if (target.exists() && !target.delete()) {
                throw IOException("Não foi possível substituir: ${target.path}")
            }
            if (!tmp.renameTo(target)) {
                // fallback: cópia byte a byte (alguns filesystems não garantem rename)
                target.outputStream().use { out -> tmp.inputStream().use { it.copyTo(out) } }
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Cópia recursiva com limite de profundidade. */
    @Throws(IOException::class)
    fun copyRecursive(src: File, dst: File, depth: Int = 0) {
        require(depth < 64) { "Profundidade de diretórios excessiva" }
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) throw IOException("Falha ao criar ${dst.path}")
            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dst, child.name), depth + 1)
            }
        } else {
            src.copyTo(dst, overwrite = true)
        }
    }

    /**
     * Eliminação recursiva. Recusa eliminar a raiz [guardRoot] ou algo fora dela.
     * Devolve true em caso de sucesso.
     */
    fun deleteInside(guardRoot: File, target: File): Boolean {
        val rootCanonical = guardRoot.canonicalFile
        val targetCanonical = target.canonicalFile
        if (targetCanonical.path == rootCanonical.path) return false // nunca apagar a raiz
        if (!SafePath.isInside(rootCanonical, targetCanonical)) return false
        return deleteRecursive(targetCanonical)
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { if (!deleteRecursive(it)) return false }
        }
        return file.delete()
    }

    /** Tamanho legível (KB/MB/GB). */
    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    /** Tamanho recursivo de uma pasta (com limite de ficheiros para evitar travamentos). */
    fun sizeOf(file: File, limitFiles: Int = 20_000): Long {
        var total = 0L
        var count = 0
        fun walk(f: File) {
            if (count > limitFiles) return
            if (f.isFile) { total += f.length(); count++ } else f.listFiles()?.forEach { walk(it) }
        }
        walk(file)
        return total
    }

    /** Heurística simples de ficheiro binário (bytes nulos nos primeiros 8 KB). */
    fun looksBinary(file: File): Boolean {
        if (file.length() == 0L) return false
        val head = file.inputStream().use { input ->
            val buf = ByteArray(8192)
            val n = input.read(buf)
            if (n <= 0) return false
            buf.copyOf(n)
        }
        return head.contains(0.toByte())
    }

    /** Heurística para conteúdo já em memória: byte nulo na amostra => binário. */
    fun looksBinaryText(content: String): Boolean {
        val sample = if (content.length > 8000) content.substring(0, 8000) else content
        return sample.indexOf('\u0000') >= 0
    }
}
