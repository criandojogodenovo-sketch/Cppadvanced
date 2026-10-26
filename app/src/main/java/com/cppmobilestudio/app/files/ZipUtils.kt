package com.cppmobilestudio.app.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ZIP seguro (Fase B/N): extração protegida contra zip-slip e zip-bomb,
 * criação de arquivo a partir de um diretório.
 */
object ZipUtils {

    const val MAX_ENTRIES = 10_000
    const val MAX_TOTAL_UNCOMPRESSED: Long = 512L * 1024 * 1024 // 512 MB
    private const val MAX_SINGLE_FILE: Long = 256L * 1024 * 1024 // 256 MB

    sealed class ExtractResult {
        data class Success(val filesExtracted: Int) : ExtractResult()
        data class Failed(val reason: String) : ExtractResult()
    }

    /**
     * Extrai [input] para [destDir] com validação de todas as entradas:
     * nomes (anti zip-slip), número de entradas, tamanho descomprimido (anti bomba).
     */
    fun extractSafely(input: InputStream, destDir: File): ExtractResult {
        val dest = destDir.canonicalFile
        var entries = 0
        var totalUncompressed = 0L
        try {
            ZipInputStream(input.buffered()).use { zis ->
                while (true) {
                    val entry: ZipEntry = zis.nextEntry ?: break
                    if (entries >= MAX_ENTRIES) {
                        return ExtractResult.Failed("Demasiadas entradas no ZIP (>$MAX_ENTRIES)")
                    }
                    entries++
                    val name = entry.name
                    if (!SafePath.isSafeZipEntryName(name)) {
                        return ExtractResult.Failed("Entrada com caminho suspeito: $name")
                    }
                    val cleanPath = name.replace('\\', '/')
                        .split('/').filter { it.isNotBlank() && it != "." }.joinToString("/")
                    if (cleanPath.isBlank()) { zis.closeEntry(); continue }

                    val outFile = SafePath.resolveInside(dest, cleanPath)
                        ?: return ExtractResult.Failed("Entrada escapa ao destino: $name")

                    if (entry.isDirectory) {
                        if (!outFile.exists() && !outFile.mkdirs()) {
                            return ExtractResult.Failed("Falha ao criar diretório: $cleanPath")
                        }
                    } else {
                        if (!outFile.canonicalFile.parentFile.exists() &&
                            !outFile.canonicalFile.parentFile.mkdirs()
                        ) {
                            return ExtractResult.Failed("Falha ao criar diretório para: $cleanPath")
                        }
                        var written = 0L
                        outFile.outputStream().buffered().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = zis.read(buf)
                                if (n <= 0) break
                                written += n
                                totalUncompressed += n
                                if (written > MAX_SINGLE_FILE) {
                                    return ExtractResult.Failed("Ficheiro demasiado grande: $cleanPath")
                                }
                                if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED) {
                                    return ExtractResult.Failed("ZIP excede o limite de descompressão")
                                }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
        } catch (e: java.util.zip.ZipException) {
            return ExtractResult.Failed("ZIP inválido: ${e.message}")
        } catch (e: IOException) {
            return ExtractResult.Failed("Erro de leitura: ${e.message}")
        }
        return ExtractResult.Success(entries)
    }

    /** Cria um ZIP com o conteúdo de [dir] (entrada raiz = [rootName]). */
    @Throws(IOException::class)
    fun zipDirectory(dir: File, rootName: String, output: OutputStream) {
        val base = dir.canonicalFile
        ZipOutputStream(output.buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("$rootName/"))
            zos.closeEntry()
            addToZip(base, base, rootName, zos)
        }
    }

    private fun addToZip(base: File, file: File, rootName: String, zos: ZipOutputStream) {
        val rel = base.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        val entryName = "$rootName/$rel"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryName/"))
            zos.closeEntry()
            file.listFiles()?.forEach { addToZip(base, it, rootName, zos) }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
}
