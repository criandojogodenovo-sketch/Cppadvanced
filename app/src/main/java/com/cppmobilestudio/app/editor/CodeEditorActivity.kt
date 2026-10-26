package com.cppmobilestudio.app.editor

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cppmobilestudio.app.R
import com.cppmobilestudio.app.toolchain.ToolchainManager
import com.cppmobilestudio.app.files.VNode
import com.cppmobilestudio.app.files.Vfs
import com.cppmobilestudio.app.files.VfsLayer
import com.cppmobilestudio.app.projects.ProjectSettingsActivity
import com.cppmobilestudio.app.settings.SettingsRepository
import com.cppmobilestudio.app.util.RecoveryManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context

/**
 * Atividade do editor de código (Fase A): carrega, edita, guarda e recupera
 * ficheiros através do Vfs; todos os botões têm funções reais.
 */
class CodeEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var editor: CodeEditorView
    private lateinit var settings: SettingsRepository
    private lateinit var recovery: RecoveryManager

    private var node: VNode? = null
    private var vfs: Vfs? = null
    private var dirty = false
    private var autosaveRunnable: Runnable? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        recovery = RecoveryManager(this)
        setContentView(R.layout.activity_editor)

        toolbar = findViewById(R.id.toolbar)
        editor = findViewById(R.id.editor)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val encoded = intent.getStringExtra(EXTRA_NODE)
        node = encoded?.let { VNode.decode(it) }
        vfs = when (node) {
            is VNode.Local -> VfsLayer.local(this)
            is VNode.Saf -> VfsLayer.saf(this)
            null -> null
        }

        if (node == null || vfs == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        editor.updatePalette(settings.isEditorDark())
        editor.setHighlightEnabled(settings.highlightEnabled)
        editor.textSize = settings.fontSizeSp.toFloat()
        editor.onHighlightDisabled = {
            findViewById<android.view.View>(R.id.warningBanner).visibility =
                android.view.View.VISIBLE
        }
        editor.onCursorPositionChanged = { line, col ->
            title = buildString {
                append(fileName())
                if (dirty) append(" ${getString(R.string.editor_unsaved_dot)}")
                append("  ·  $line:$col")
            }
        }
        editor.onContentModified = {
            dirty = true
            scheduleRecovery()
            scheduleAutosave()
        }

        loadContent()

        // Barra de símbolos: inserção real no cursor
        val symbols = mapOf(
            R.id.symTab to editor.indentUnit,
            R.id.symSemi to ";", R.id.symLBrace to "{", R.id.symRBrace to "}",
            R.id.symLParen to "(", R.id.symRParen to ")",
            R.id.symLAngle to "<", R.id.symRAngle to ">",
            R.id.symHash to "#", R.id.symQuote to "\"", R.id.symApostrophe to "'",
            R.id.symBackslash to "\\", R.id.symEquals to "=", R.id.symAmp to "&",
            R.id.symStar to "*", R.id.symArrow to "->", R.id.symColonColon to "::"
        )
        for ((id, value) in symbols) {
            findViewById<android.view.View>(id)?.setOnClickListener { editor.insertAtCursor(value) }
        }
    }

    private fun fileName(): String = node?.displayName() ?: "?"

    private fun loadContent() {
        val v = vfs ?: return
        val n = node ?: return
        val content = try {
            v.readText(n)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.error), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // Recuperação após crash: conteúdo mais recente não guardado?
        val rec = recovery.read(key(n))
        val fileLastMod = lastModified(n)
        if (rec != null && rec.content != content && rec.savedAt > fileLastMod) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recover_dialog_title)
                .setMessage(getString(R.string.recover_dialog_msg, formatTime(rec.savedAt)))
                .setPositiveButton(R.string.restore) { _, _ ->
                    editor.setText(rec.content)
                    recovery.clear(key(n))
                }
                .setNegativeButton(R.string.discard) { _, _ -> recovery.clear(key(n)) }
                .show()
        }
        editor.setText(content)
        dirty = false
        supportActionBar?.title = fileName()
    }

    private fun save(): Boolean {
        val v = vfs ?: return false
        val n = node ?: return false
        return try {
            v.writeText(n, editor.text?.toString() ?: "")
            dirty = false
            recovery.clear(key(n))
            supportActionBar?.title = fileName()
            Toast.makeText(this, R.string.file_saved_toast, Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(getString(R.string.save_failed, e.message ?: "?"))
                .setPositiveButton(R.string.ok, null)
                .show()
            false
        }
    }

    private fun key(n: VNode): String = n.encode()

    private fun lastModified(n: VNode): Long = when (n) {
        is VNode.Local -> n.file.lastModified()
        is VNode.Saf -> 0L
    }

    private fun formatTime(ms: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))

    private fun scheduleRecovery() {
        val n = node ?: return
        val snapshot = editor.text?.toString() ?: return
        handler.postDelayed({
            recovery.write(key(n), snapshot)
        }, 800)
    }

    private fun scheduleAutosave() {
        if (!settings.autosaveEnabled) return
        autosaveRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (dirty) save()
        }
        autosaveRunnable = r
        handler.postDelayed(r, 30_000)
    }

    // ── Menu ─────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { confirmExitIfDirty(); return true }
            R.id.action_save -> save()
            R.id.action_undo -> editor.performUndo()
            R.id.action_redo -> editor.performRedo()
            R.id.action_find -> FindReplaceDialog(this, editor).show()
            R.id.action_font_increase -> changeFont(+1)
            R.id.action_font_decrease -> changeFont(-1)
            R.id.action_go_to_line -> goToLineDialog()
            R.id.action_toggle_highlight -> {
                editor.setHighlightEnabled(!editor.highlightEnabled)
                Toast.makeText(
                    this,
                    if (editor.highlightEnabled) "ON" else "OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
            R.id.action_compile -> showCompileUnavailable()
            R.id.action_project_settings -> {
                // resolve o projeto local que contém este ficheiro
                val repo = com.cppmobilestudio.app.projects.ProjectRepository(this)
                val proj = (node as? VNode.Local)?.let { repo.projectOf(it.file) }
                if (proj != null) {
                    val i = Intent(this, ProjectSettingsActivity::class.java)
                    i.putExtra(
                        com.cppmobilestudio.app.projects.FileExplorerActivity.EXTRA_PROJECT_NAME,
                        proj.name
                    )
                    startActivity(i)
                } else {
                    Toast.makeText(this, R.string.project_settings, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCompileUnavailable() {
        val st = ToolchainManager.status(this)
        val detail = st.joinToString("\n") { "• ${it.name}: ${if (it.available) "OK" else it.detail}" }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compile_unavailable_title)
            .setMessage(getString(R.string.compile_unavailable_msg) + "\n\n" + detail)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun changeFont(delta: Int) {
        settings.fontSizeSp = settings.fontSizeSp + delta
        editor.textSize = settings.fontSizeSp.toFloat()
    }

    private fun goToLineDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = getString(R.string.line_number)
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_line)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val line = input.text.toString().toIntOrNull()
                if (line == null || !editor.goToLine(line)) {
                    Toast.makeText(this, R.string.invalid_line, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmExitIfDirty() {
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editor_unsaved_title)
            .setPositiveButton(R.string.editor_save) { _, _ ->
                if (save()) finish()
            }
            .setNegativeButton(R.string.discard) { _, _ -> finish() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (dirty) scheduleRecovery()
    }

    override fun onBackPressed() {
        confirmExitIfDirty()
    }

    companion object {
        const val EXTRA_NODE = "extra_node"

        fun open(ctx: Context, node: VNode) {
            val i = Intent(ctx, CodeEditorActivity::class.java)
            i.putExtra(EXTRA_NODE, node.encode())
            ctx.startActivity(i)
        }
    }
}
