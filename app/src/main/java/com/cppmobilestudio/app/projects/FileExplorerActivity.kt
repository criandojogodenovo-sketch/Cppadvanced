package com.cppmobilestudio.app.projects

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cppmobilestudio.app.R
import com.cppmobilestudio.app.build.ToolchainManager
import com.cppmobilestudio.app.editor.CodeEditorActivity
import com.cppmobilestudio.app.files.FileOps
import com.cppmobilestudio.app.files.SafePath
import com.cppmobilestudio.app.files.TextSearchEngine
import com.cppmobilestudio.app.files.VEntry
import com.cppmobilestudio.app.files.VNode
import com.cppmobilestudio.app.files.Vfs
import com.cppmobilestudio.app.files.VfsLayer
import com.cppmobilestudio.app.files.ZipUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Explorador de ficheiros integrado (Fase B): criar, renomear, mover, eliminar,
 * duplicar, importar/exportar ZIP, pesquisa por nome e conteúdo.
 * Todas as operações passam pelo Vfs com validação de caminhos.
 */
class FileExplorerActivity : AppCompatActivity() {

    private lateinit var vfs: Vfs
    private lateinit var current: VNode
    private var projectName: String = ""

    private lateinit var adapter: FilesAdapter
    private var entries: List<VEntry> = emptyList()
    private var searchMode = false

    private val textExtensions = setOf(
        "cpp", "cc", "cxx", "c", "h", "hpp", "hh", "hxx", "inl", "tpp",
        "txt", "md", "json", "cmake", "xml", "gradle", "properties", "yml",
        "yaml", "glsl", "vert", "frag", "comp", "sh", "bat", "ini", "cfg", "log"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_explorer)

        val nodeEncoded = intent.getStringExtra(EXTRA_NODE)
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: ""
        val decoded = nodeEncoded?.let { VNode.decode(it) }

        vfs = when (decoded) {
            is VNode.Local -> VfsLayer.local(this)
            is VNode.Saf -> VfsLayer.saf(this)
            null -> {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        current = decoded

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = projectName.ifBlank { vfs.rootLabel(current) }

        adapter = FilesAdapter(
            onOpen = { entry -> openEntry(entry) },
            onLongOpen = { entry -> showItemMenu(entry) }
        )
        val list = findViewById<RecyclerView>(R.id.fileList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val breadcrumb = findViewById<TextView>(R.id.breadcrumb)
        breadcrumb.setOnClickListener { showBreadcrumbMenu() }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        navigateUp() || finish()
        return true
    }

    // ── Navegação ────────────────────────────────────────────────────────────

    private fun navigateUp(): Boolean {
        val parent = vfs.parent(current) ?: return false
        if (!searchMode) {
            current = parent
            refresh()
        }
        return true
    }

    private fun refresh() {
        if (searchMode) return
        entries = vfs.list(current)
        adapter.submit(entries)
        findViewById<TextView>(R.id.emptyState).visibility =
            if (entries.isEmpty()) View.VISIBLE else View.GONE

        val breadcrumb = findViewById<TextView>(R.id.breadcrumb)
        val rootLabel = vfs.rootLabel(current)
        val rel = when (val n = current) {
            is VNode.Local -> {
                val root = VfsLayer.projectsRoot(this)
                if (n.file.canonicalPath == root.canonicalFile.path) ""
                else root.canonicalFile.toPath().relativize(n.file.canonicalFile.toPath()).toString()
            }
            is VNode.Saf -> n.relativePath
        }
        breadcrumb.text = if (rel.isBlank()) rootLabel else "$rootLabel / $rel"

        findViewById<TextView>(R.id.pathBar).text =
            getString(R.string.items_count, entries.size) + "  ·  " + when (val n = current) {
                is VNode.Local -> n.file.absolutePath
                is VNode.Saf -> n.treeUri.lastPathSegment ?: ""
            }
    }

    private fun openEntry(entry: VEntry) {
        if (entry.isDirectory) {
            val child = vfs.child(current, entry.name) ?: return
            current = child
            refresh()
        } else {
            openFileInEditor(entry.name)
        }
    }

    private fun openFileInEditor(name: String) {
        val node = vfs.child(current, name) ?: return
        CodeEditorActivity.open(this, node)
    }

    private fun showBreadcrumbMenu() {
        val rootLabel = vfs.rootLabel(current)
        val parts = when (val n = current) {
            is VNode.Local -> {
                val root = VfsLayer.projectsRoot(this)
                if (n.file.canonicalPath == root.canonicalFile.path) emptyList()
                else root.canonicalFile.toPath()
                    .relativize(n.file.canonicalFile.toPath()).toString().split("/")
            }
            is VNode.Saf -> n.relativePath.split("/").filter { it.isNotBlank() }
        }
        // (caminhoRelativoAcumulado, rótulo)
        val items = mutableListOf("" to rootLabel)
        var acc = ""
        for (p in parts) {
            acc = if (acc.isBlank()) p else "$acc/$p"
            items.add(acc to p)
        }
        val titles = items.map { it.second }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.explorer_title)
            .setItems(titles) { _, which ->
                val base = when (val n = current) {
                    is VNode.Local -> VNode.Local(VfsLayer.projectsRoot(this))
                    is VNode.Saf -> VNode.Saf(n.treeUri, "")
                }
                val rel = items[which].first
                val target = navigateTo(base, rel)
                if (target != null) {
                    current = target
                    searchMode = false
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Navega por segmentos (respeita a validação por nome simples do Vfs). */
    private fun navigateTo(base: VNode, rel: String): VNode? {
        if (rel.isBlank()) return base
        var cur = base
        for (seg in rel.split("/").filter { it.isNotBlank() }) {
            cur = vfs.child(cur, seg) ?: return null
        }
        return cur
    }

    // ── Menu de contexto de item ─────────────────────────────────────────────

    private fun showItemMenu(entry: VEntry) {
        val options = mutableListOf(
            getString(R.string.open),
            getString(R.string.rename),
            getString(R.string.duplicate),
            getString(R.string.copy_path),
            getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> openEntry(entry)
                    1 -> renameDialog(entry)
                    2 -> duplicate(entry)
                    3 -> copyPath(entry)
                    4 -> confirmDelete(entry)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun inputDialog(titleRes: Int, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(initial)
        input.setSelection(initial.length)
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameDialog(entry: VEntry) {
        inputDialog(R.string.rename, entry.name) { newName ->
            if (newName == entry.name) return@inputDialog
            if (!SafePath.isValidEntryName(newName)) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                return@inputDialog
            }
            val node = vfs.child(current, entry.name) ?: return@inputDialog
            val renamed = vfs.rename(node, newName)
            if (renamed == null) {
                Toast.makeText(this, R.string.name_already_exists, Toast.LENGTH_SHORT).show()
            } else {
                refresh()
            }
        }
    }

    private fun duplicate(entry: VEntry) {
        val dot = entry.name.lastIndexOf('.')
        val copyName = if (dot > 0) {
            entry.name.substring(0, dot) + "_copia" + entry.name.substring(dot)
        } else entry.name + "_copia"
        if (!SafePath.isValidEntryName(copyName)) {
            Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            return
        }
        val src = vfs.child(current, entry.name) ?: return
        try {
            if (entry.isDirectory) {
                // Duplicação de pastas: suportada em projetos locais (File I/O)
                val lsrc = (src as? VNode.Local)?.file
                val ldir = (current as? VNode.Local)?.file
                val ldst = ldir?.let { java.io.File(it, copyName) }
                if (lsrc != null && ldst != null && !ldst.exists()) {
                    FileOps.copyRecursive(lsrc, ldst)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.operation_failed, "pasta SAF"),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            } else {
                val created = vfs.createFile(current, copyName)
                if (created == null) {
                    Toast.makeText(this, R.string.name_already_exists, Toast.LENGTH_SHORT).show()
                    return
                }
                vfs.writeText(created, vfs.readText(src))
            }
            refresh()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.operation_failed, e.message ?: "?"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun copyPath(entry: VEntry) {
        val path = when (val n = current) {
            is VNode.Local -> java.io.File(n.file, entry.name).absolutePath
            is VNode.Saf -> "${n.relativePath}/${entry.name}"
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("path", path))
        Toast.makeText(this, R.string.path_copied, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(entry: VEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_msg, entry.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                val node = vfs.child(current, entry.name) ?: return@setPositiveButton
                if (vfs.delete(node)) {
                    refresh()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.operation_failed, entry.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Criar ficheiro / pasta ───────────────────────────────────────────────

    private fun newFileDialog() {
        inputDialog(R.string.new_file, "novo.cpp") { name ->
            if (!SafePath.isValidEntryName(name)) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                return@inputDialog
            }
            val created = vfs.createFile(current, name)
            if (created == null) {
                Toast.makeText(this, R.string.name_already_exists, Toast.LENGTH_SHORT).show()
            } else {
                refresh()
                openFileInEditor(name)
            }
        }
    }

    private fun newFolderDialog() {
        inputDialog(R.string.new_folder, "pasta") { name ->
            if (!SafePath.isValidEntryName(name)) {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                return@inputDialog
            }
            val created = vfs.createDir(current, name)
            if (created == null) {
                Toast.makeText(this, R.string.name_already_exists, Toast.LENGTH_SHORT).show()
            } else {
                refresh()
            }
        }
    }

    // ── ZIP ──────────────────────────────────────────────────────────────────

    private fun importZip() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/zip"
            startActivityForResult(intent, REQ_IMPORT_ZIP)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportZip() {
        val dirName = projectName.ifBlank { vfs.rootLabel(current) }
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/zip"
            intent.putExtra(Intent.EXTRA_TITLE, "$dirName.zip")
            startActivityForResult(intent, REQ_EXPORT_ZIP)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMPORT_ZIP && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val destDir = (current as? VNode.Local)?.file
            if (destDir == null) {
                // Extração ZIP: suportada em projetos locais (File I/O direto)
                Toast.makeText(this, R.string.zip_invalid, Toast.LENGTH_LONG).show()
                return
            }
            Thread {
                val result = contentResolver.openInputStream(uri)?.use {
                    ZipUtils.extractSafely(it, destDir)
                } ?: ZipUtils.ExtractResult.Failed("sem dados")
                runOnUiThread {
                    when (result) {
                        is ZipUtils.ExtractResult.Success -> {
                            Toast.makeText(this, R.string.zip_imported, Toast.LENGTH_SHORT).show()
                            refresh()
                        }
                        is ZipUtils.ExtractResult.Failed -> {
                            Toast.makeText(this, R.string.zip_invalid, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.start()
        } else if (requestCode == REQ_EXPORT_ZIP && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val thread = Thread {
                try {
                    val dir = (current as? VNode.Local)?.file
                    if (dir != null) {
                        contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            ZipUtils.zipDirectory(dir, dir.name, out)
                        }
                        runOnUiThread {
                            Toast.makeText(this, R.string.operation_done, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.operation_failed, e.message ?: "?"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            thread.start()
        }
    }

    // ── Pesquisa ─────────────────────────────────────────────────────────────

    private fun showSearchDialog() {
        val options = arrayOf(
            getString(R.string.search_by_name),
            getString(R.string.search_by_content)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_files)
            .setItems(options) { _, which ->
                if (which == 0) searchByNameDialog() else searchByContentDialog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun searchByNameDialog() {
        inputDialog(R.string.search_by_name, "") { query ->
            if (query.isBlank()) {
                clearSearch()
                return@inputDialog
            }
            val results = collectEntries(current, maxDepth = 4)
                .filter { TextSearchEngine.matchName(it.name, query) }
            showSearchResults(results)
        }
    }

    private fun searchByContentDialog() {
        inputDialog(R.string.search_hint_content, "") { query ->
            if (query.isBlank()) {
                clearSearch()
                return@inputDialog
            }
            Toast.makeText(this, R.string.search_running, Toast.LENGTH_SHORT).show()
            Thread {
                val files = HashMap<String, String>()
                collectEntries(current, maxDepth = 4)
                    .filter { !it.isDirectory && isTextFile(it.name) && it.sizeBytes < 1_000_000 }
                    .take(2000)
                    .forEach { e ->
                        val node = vfs.child(current, e.name)
                        if (node != null) {
                            try {
                                val c = vfs.readText(node)
                                if (!FileOps.looksBinaryText(c)) files[e.name] = c
                            } catch (_: Exception) {
                            }
                        }
                    }
                val hits = TextSearchEngine.searchContent(files, query)
                runOnUiThread {
                    val mapped = hits.map { h ->
                        VEntry(
                            name = "${h.filePath}:${h.line}",
                            isDirectory = false,
                            sizeBytes = -1,
                            lastModified = 0
                        )
                    }
                    showSearchResults(mapped)
                }
            }.start()
        }
    }

    private fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in textExtensions
    }

    private fun collectEntries(root: VNode, maxDepth: Int): List<VEntry> {
        val out = ArrayList<VEntry>()
        fun walk(dir: VNode, depth: Int) {
            if (depth > maxDepth || out.size > 3000) return
            for (e in vfs.list(dir)) {
                out.add(e)
                if (e.isDirectory) {
                    vfs.child(dir, e.name)?.let { walk(it, depth + 1) }
                }
            }
        }
        walk(root, 0)
        return out
    }

    private fun showSearchResults(results: List<VEntry>) {
        searchMode = true
        entries = results
        adapter.submit(entries)
        findViewById<TextView>(R.id.emptyState).visibility =
            if (results.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.pathBar).text =
            getString(R.string.search_results, results.size)
        Toast.makeText(this, R.string.operation_done, Toast.LENGTH_SHORT).show()
    }

    private fun clearSearch() {
        searchMode = false
        refresh()
    }

    // ── Menu da toolbar ──────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_explorer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { if (!navigateUp()) finish() ; return true }
            R.id.action_new_file -> newFileDialog()
            R.id.action_new_folder -> newFolderDialog()
            R.id.action_search -> showSearchDialog()
            R.id.action_import_zip -> importZip()
            R.id.action_export_zip -> exportZip()
            R.id.action_project_settings -> {
                val i = Intent(this, ProjectSettingsActivity::class.java)
                i.putExtra(EXTRA_PROJECT_NAME, projectName)
                startActivity(i)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private inner class FilesAdapter(
        val onOpen: (VEntry) -> Unit,
        val onLongOpen: (VEntry) -> Unit
    ) : RecyclerView.Adapter<FilesAdapter.VH>() {

        private val items = mutableListOf<VEntry>()

        fun submit(list: List<VEntry>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: android.widget.ImageView = v.findViewById(R.id.icon)
            val name: TextView = v.findViewById(R.id.name)
            val meta: TextView = v.findViewById(R.id.meta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_entry, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.name.text = e.name
            holder.icon.setImageResource(
                if (e.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
            )
            holder.icon.setColorFilter(
                if (e.isDirectory) 0xFFFF8C42.toInt() else 0xFFA9A9B3.toInt()
            )
            holder.meta.text = if (e.isDirectory) {
                "pasta"
            } else {
                if (e.sizeBytes >= 0) FileOps.humanSize(e.sizeBytes) else ""
            }
            holder.itemView.setOnClickListener { onOpen(e) }
            holder.itemView.setOnLongClickListener {
                onLongOpen(e)
                true
            }
        }
    }

    companion object {
        const val EXTRA_NODE = "extra_node"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        private const val REQ_IMPORT_ZIP = 61
        private const val REQ_EXPORT_ZIP = 62
    }
}
