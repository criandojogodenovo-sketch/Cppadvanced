package com.cppmobilestudio.app.util

import com.cppmobilestudio.app.files.FileOps
import org.json.JSONObject
import java.io.File

/**
 * Recuperação de alterações após encerramento inesperado (Fase A).
 * Grava periodicamente o conteúdo não guardado em `<filesDir>/recovery/`
 * indexado por hash do caminho; ao reabrir o ficheiro, a app oferece restaurar.
 */
class RecoveryManager(private val context: android.content.Context) {

    private val dir: File
        get() = File(context.filesDir, "recovery").apply { mkdirs() }

    private fun fileFor(path: String): File {
        val safe = Integer.toHexString(path.hashCode()) + "_" +
            Integer.toHexString(path.length)
        return File(dir, safe + ".json")
    }

    /** Guarda (ou apaga, se content == null) o estado de recuperação de [path]. */
    fun write(path: String, content: String?) {
        val f = fileFor(path)
        if (content == null) {
            if (f.exists()) f.delete()
            return
        }
        val json = JSONObject()
            .put("path", path)
            .put("savedAt", System.currentTimeMillis())
            .put("content", content)
        try {
            FileOps.writeAtomic(f, json.toString())
        } catch (_: Exception) {
            // recuperação é best-effort; nunca deve derrubar a app
        }
    }

    data class Recovery(val path: String, val savedAt: Long, val content: String)

    /** Devolve a recuperação guardada para [path], se existir. */
    fun read(path: String): Recovery? {
        val f = fileFor(path)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            if (json.optString("path") != path) return null
            Recovery(path, json.optLong("savedAt", 0), json.optString("content"))
        } catch (_: Exception) {
            null
        }
    }

    fun clear(path: String) = write(path, null)
}
