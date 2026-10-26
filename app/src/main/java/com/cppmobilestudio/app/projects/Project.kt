package com.cppmobilestudio.app.projects

import com.cppmobilestudio.app.files.FileOps
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Modelo de projeto (Fase B / secção 21 do prompt): metadados persistentes
 * em `<projeto>/.cppstudio/project.json`.
 */
data class Project(
    val name: String,
    val rootDir: File,
    val language: String = "cpp",
    val cppStandard: String = "C++17",
    val compiler: String = "clang-bundled (pendente, ETAPA 4)",
    val architecture: String = "arm64-v8a",
    val target: String = "host-android",
    val buildSystem: String = "single-files",
    val createdAt: Long = System.currentTimeMillis(),
    val dependencies: List<String> = emptyList(),
    val configStatus: String = "template"
) {
    val metaDir: File get() = File(rootDir, ".cppstudio")
    val metaFile: File get() = File(metaDir, "project.json")

    fun save() {
        if (!metaDir.exists()) metaDir.mkdirs()
        val json = JSONObject()
            .put("name", name)
            .put("createdAt", createdAt)
            .put("language", language)
            .put("cppStandard", cppStandard)
            .put("compiler", compiler)
            .put("architecture", architecture)
            .put("target", target)
            .put("buildSystem", buildSystem)
            .put("dependencies", JSONArray(dependencies))
            .put("configStatus", configStatus)
        FileOps.writeAtomic(metaFile, json.toString(2))
    }

    companion object {
        const val META_DIR = ".cppstudio"

        /** Deteta um projeto a partir de um diretório (com ou sem metadados). */
        fun fromDirectory(dir: File): Project? {
            if (!dir.isDirectory) return null
            val meta = File(dir, "$META_DIR/project.json")
            return if (meta.exists()) {
                try {
                    val o = JSONObject(meta.readText())
                    Project(
                        name = o.optString("name", dir.name),
                        rootDir = dir,
                        language = o.optString("language", "cpp"),
                        cppStandard = o.optString("cppStandard", "C++17"),
                        compiler = o.optString("compiler", ""),
                        architecture = o.optString("architecture", "arm64-v8a"),
                        target = o.optString("target", "host-android"),
                        buildSystem = o.optString("buildSystem", "single-files"),
                        createdAt = o.optLong("createdAt", 0L),
                        dependencies = o.optJSONArray("dependencies")?.let { arr ->
                            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                        } ?: emptyList(),
                        configStatus = o.optString("configStatus", "template")
                    )
                } catch (_: Exception) {
                    basic(dir)
                }
            } else {
                basic(dir)
            }
        }

        private fun basic(dir: File): Project = Project(name = dir.name, rootDir = dir)
    }
}
