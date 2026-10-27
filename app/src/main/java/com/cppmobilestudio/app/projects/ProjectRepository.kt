package com.cppmobilestudio.app.projects

import android.content.Context
import com.cppmobilestudio.app.files.SafePath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Repositório de projetos e lista de recentes (Fase B).
 * Projetos vivem em armazenamento privado da app; pastas externas (SAF)
 * são registadas à parte com o seu Uri persistido.
 */
class ProjectRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("cppstudio_projects", Context.MODE_PRIVATE)

    val projectsRoot: File
        get() = com.cppmobilestudio.app.files.VfsLayer.projectsRoot(context)

    fun listProjects(): List<Project> {
        val root = projectsRoot
        if (!root.exists()) root.mkdirs()
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { Project.fromDirectory(it) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun findProject(name: String): Project? =
        SafePath.resolveInside(projectsRoot, name)?.let { dir ->
            if (dir.isDirectory) Project.fromDirectory(dir) else null
        }

    /** Cria um projeto com o template indicado. Devolve null se o nome for inválido/duplicado. */
    fun createProject(name: String, template: ProjectTemplates.Template): Project? {
        val clean = name.trim()
        if (!SafePath.isValidEntryName(clean)) return null
        val dir = File(projectsRoot, clean)
        if (dir.exists()) return null
        if (!dir.mkdirs()) return null
        val project = Project(
            name = clean,
            rootDir = dir,
            language = if (template == ProjectTemplates.Template.C_SINGLE_FILE) "c" else "cpp",
            buildSystem = if (template == ProjectTemplates.Template.CPP_CMAKE_MULTI ||
                template == ProjectTemplates.Template.CPP_STATIC_LIB
            ) "cmake"
            else if (template == ProjectTemplates.Template.AGDK_TRIANGLE) "gradle-agdk"
            else "single-files"
        )
        ProjectTemplates.materialize(project, template, context.applicationContext)
        project.save()
        touchRecent(project.name, local = true)
        return project
    }

    // ── Recentes (locais e SAF) ──────────────────────────────────────────────

    data class Recent(
        val name: String,
        val local: Boolean,
        val uri: String?,     // para SAF
        val lastOpened: Long
    )

    fun recents(): List<Recent> {
        val arr = JSONArray(prefs.getString(KEY_RECENTS, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Recent(
                name = o.getString("n"),
                local = o.getBoolean("l"),
                uri = o.optString("u").takeIf { it.isNotBlank() },
                lastOpened = o.getLong("t")
            )
        }.sortedByDescending { it.lastOpened }
    }

    fun touchRecent(name: String, local: Boolean = true, uri: String? = null) {
        val list = recents().filter { !(it.name == name && it.local == local) }
            .toMutableList()
        list.add(0, Recent(name, local, uri, System.currentTimeMillis()))
        while (list.size > 20) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject()
                    .put("n", r.name)
                    .put("l", r.local)
                    .put("u", r.uri ?: "")
                    .put("t", r.lastOpened)
            )
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    fun removeRecent(name: String, local: Boolean) {
        val list = recents().filter { !(it.name == name && it.local == local) }
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject()
                    .put("n", r.name)
                    .put("l", r.local)
                    .put("u", r.uri ?: "")
                    .put("t", r.lastOpened)
            )
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    fun clearRecents() = prefs.edit().remove(KEY_RECENTS).apply()

    /** Encontra o projeto local que contém um ficheiro (para extras de UI). */
    fun projectOf(file: File): Project? {
        var cur: File? = file
        val root = projectsRoot.canonicalFile
        while (cur != null) {
            val c = cur.canonicalFile
            if (c.parentFile?.canonicalFile == root) {
                return Project.fromDirectory(c)
            }
            cur = cur.parentFile
        }
        return null
    }

    companion object {
        private const val KEY_RECENTS = "recents"
    }
}
