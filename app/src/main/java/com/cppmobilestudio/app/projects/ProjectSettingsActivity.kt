package com.cppmobilestudio.app.projects

import android.os.Bundle
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cppmobilestudio.app.R
import com.cppmobilestudio.app.toolchain.ToolchainManager
import com.cppmobilestudio.app.files.FileOps
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

/**
 * Definições do projeto: padrão C++, sistema de build, requisitos em falta.
 * Guarda em project.json (persistência real).
 */
class ProjectSettingsActivity : AppCompatActivity() {

    private var project: Project? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Projeto a partir do nome (explorer) ou do caminho do ficheiro (editor)
        val repo = ProjectRepository(this)
        val name = intent.getStringExtra(FileExplorerActivity.EXTRA_PROJECT_NAME)
        project = name?.let { repo.findProject(it) }

        val p = project
        if (p == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.projectInfo).text = buildString {
            appendLine("Nome: ${p.name}")
            appendLine("Linguagem: ${p.language}")
            appendLine("Padrão: ${p.cppStandard}")
            appendLine("Build: ${p.buildSystem}")
            appendLine("Arquitetura: ${p.architecture}")
            appendLine("Alvo: ${p.target}")
            appendLine("Diretório: ${p.rootDir.absolutePath}")
            appendLine("Ficheiros: ${countFiles(p.rootDir)}")
        }

        when (p.cppStandard) {
            "C++20" -> findViewById<RadioButton>(R.id.std20).isChecked = true
            "C++23" -> findViewById<RadioButton>(R.id.std23).isChecked = true
            else -> findViewById<RadioButton>(R.id.std17).isChecked = true
        }
        if (p.buildSystem == "cmake") {
            findViewById<RadioButton>(R.id.buildCmake).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.buildSingle).isChecked = true
        }

        // Requisitos em falta — estado real da toolchain
        val missing = ToolchainManager.status(this).filter { !it.available }
            .joinToString("\n") { "• ${it.name}" }
        findViewById<TextView>(R.id.missingReqs).text =
            missing.ifBlank { getString(R.string.ok) }

        findViewById<android.view.View>(R.id.btnSaveProject).setOnClickListener {
            val current = project ?: return@setOnClickListener
            val std = when {
                findViewById<RadioButton>(R.id.std20).isChecked -> "C++20"
                findViewById<RadioButton>(R.id.std23).isChecked -> "C++23"
                else -> "C++17"
            }
            val bs = if (findViewById<RadioButton>(R.id.buildCmake).isChecked) "cmake" else "single-files"
            val updated = current.copy(cppStandard = std, buildSystem = bs)
            updated.save()
            project = updated
            Toast.makeText(this, R.string.file_saved_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun countFiles(dir: File): Int {
        var count = 0
        fun walk(f: File) {
            if (count > 9999) return
            if (f.isFile) count++ else f.listFiles()?.forEach { if (it.name != Project.META_DIR) walk(it) }
        }
        walk(dir)
        return count
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
