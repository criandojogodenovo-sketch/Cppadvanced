package com.cppmobilestudio.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cppmobilestudio.app.console.ConsoleActivity
import com.cppmobilestudio.app.docs.DocsActivity
import com.cppmobilestudio.app.editor.CodeEditorActivity
import com.cppmobilestudio.app.files.VNode
import com.cppmobilestudio.app.files.VfsLayer
import com.cppmobilestudio.app.projects.FileExplorerActivity
import com.cppmobilestudio.app.projects.Project
import com.cppmobilestudio.app.projects.ProjectRepository
import com.cppmobilestudio.app.projects.ProjectTemplates
import com.cppmobilestudio.app.settings.SettingsActivity
import com.cppmobilestudio.app.settings.SettingsRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Dashboard de projetos (Fase B/M): recentes, criar projeto, abrir pasta SAF,
 * acesso à consola, documentação e definições. Tudo funcional offline.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repo: ProjectRepository
    private lateinit var settings: SettingsRepository
    private lateinit var adapter: RecentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ProjectRepository(this)
        settings = SettingsRepository(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_folder -> openFolderSaf()
                R.id.action_console -> startActivity(Intent(this, ConsoleActivity::class.java))
                R.id.action_docs -> startActivity(Intent(this, DocsActivity::class.java))
                R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }

        adapter = RecentsAdapter(
            onOpen = { recent -> openRecent(recent) },
            onRemove = { recent ->
                repo.removeRecent(recent.name, recent.local)
                refresh()
            }
        )
        val list = findViewById<RecyclerView>(R.id.recentsList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabNewProject).setOnClickListener {
            showNewProjectDialog()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val recents = repo.recents()
        adapter.submit(recents)
        val empty = recents.isEmpty()
        findViewById<View>(R.id.emptyState).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.recentsList).visibility = if (empty) View.GONE else View.VISIBLE

        val freeGb = try {
            val stat = StatFs(repo.projectsRoot.absolutePath)
            stat.availableBytes / (1024.0 * 1024 * 1024)
        } catch (_: Exception) {
            -1.0
        }
        findViewById<TextView>(R.id.storageInfo).text =
            getString(
                R.string.project_count,
                repo.listProjects().size,
                if (freeGb >= 0) String.format("%.1f GB", freeGb) else "—"
            )
    }

    // ── Novo projeto ─────────────────────────────────────────────────────────

    private fun showNewProjectDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new_project, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.projectNameInput)
        val templates = mapOf(
            R.id.tplConsole to ProjectTemplates.Template.CPP_CONSOLE,
            R.id.tplCMakeMulti to ProjectTemplates.Template.CPP_CMAKE_MULTI,
            R.id.tplStaticLib to ProjectTemplates.Template.CPP_STATIC_LIB,
            R.id.tplC to ProjectTemplates.Template.C_SINGLE_FILE
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_project)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                if (name.isEmpty() || !com.cppmobilestudio.app.files.SafePath.isValidEntryName(name)) {
                    Toast.makeText(this, R.string.invalid_project_name, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val selected = templates.entries.firstOrNull {
                    view.findViewById<android.widget.RadioButton>(it.key).isChecked
                }?.value ?: ProjectTemplates.Template.CPP_CONSOLE

                val project = repo.createProject(name, selected)
                if (project == null) {
                    Toast.makeText(this, R.string.project_exists, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.project_created, Toast.LENGTH_SHORT).show()
                    openProjectExplorer(project.name, null)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Abertura de projetos ─────────────────────────────────────────────────

    private fun openRecent(recent: ProjectRepository.Recent) {
        if (recent.local) {
            openProjectExplorer(recent.name, null)
        } else {
            val uri = recent.uri?.let { Uri.parse(it) } ?: return
            repo.touchRecent(recent.name, local = false, uri = recent.uri)
            val i = Intent(this, FileExplorerActivity::class.java)
            i.putExtra(FileExplorerActivity.EXTRA_NODE, VNode.Saf(uri, "").encode())
            i.putExtra(FileExplorerActivity.EXTRA_PROJECT_NAME, recent.name)
            startActivity(i)
        }
    }

    private fun openProjectExplorer(name: String, unused: Project?) {
        val dir = java.io.File(repo.projectsRoot, name)
        if (!dir.isDirectory) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            return
        }
        repo.touchRecent(name, local = true)
        val i = Intent(this, FileExplorerActivity::class.java)
        i.putExtra(FileExplorerActivity.EXTRA_NODE, VNode.Local(dir).encode())
        i.putExtra(FileExplorerActivity.EXTRA_PROJECT_NAME, name)
        startActivity(i)
    }

    private fun openFolderSaf() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            startActivityForResult(intent, REQ_OPEN_TREE)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OPEN_TREE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val name = DocumentFile.fromTreeUri(this, uri)?.name ?: "Pasta"
            repo.touchRecent(name, local = false, uri = uri.toString())
            refresh()
            val i = Intent(this, FileExplorerActivity::class.java)
            i.putExtra(FileExplorerActivity.EXTRA_NODE, VNode.Saf(uri, "").encode())
            i.putExtra(FileExplorerActivity.EXTRA_PROJECT_NAME, name)
            startActivity(i)
        }
    }

    // ── Adapter de recentes ──────────────────────────────────────────────────

    private class RecentsAdapter(
        val onOpen: (ProjectRepository.Recent) -> Unit,
        val onRemove: (ProjectRepository.Recent) -> Unit
    ) : RecyclerView.Adapter<RecentsAdapter.VH>() {

        private val items = mutableListOf<ProjectRepository.Recent>()

        fun submit(list: List<ProjectRepository.Recent>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: android.widget.ImageView = v.findViewById(R.id.icon)
            val name: TextView = v.findViewById(R.id.name)
            val meta: TextView = v.findViewById(R.id.meta)
            val remove: ImageButton = v.findViewById(R.id.removeBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_project, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.meta.text = if (item.local) {
                holder.itemView.context.getString(R.string.recent_projects) + " · local"
            } else {
                holder.itemView.context.getString(R.string.external_projects)
            }
            holder.icon.setImageResource(
                if (item.local) R.drawable.ic_folder else R.drawable.ic_folder_open
            )
            holder.itemView.setOnClickListener { onOpen(item) }
            holder.remove.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        private const val REQ_OPEN_TREE = 41
    }
}
