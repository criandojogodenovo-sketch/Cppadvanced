package com.cppmobilestudio.app.cloud

import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.cppmobilestudio.app.R
import com.cppmobilestudio.app.projects.Project
import com.cppmobilestudio.app.projects.ProjectRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File

/**
 * "Compilar na nuvem" (Fase 7): commit + push do projeto via API do GitHub,
 * monitorização do workflow em tempo real, log de erros e instalação do APK.
 * O token vive encriptado (TokenStore) e nunca é mostrado por inteiro.
 */
class CloudBuildActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var tokenStore: TokenStore
    private lateinit var repo: ProjectRepository

    private lateinit var statusText: TextView
    private lateinit var repoText: TextView
    private lateinit var logText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnBuild: Button
    private lateinit var btnInstall: Button
    private lateinit var btnOpenRepo: Button

    private var apkReady: File? = null
    private var repoName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_build)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.cloud_build_title)

        val projectName = intent.getStringExtra(EXTRA_PROJECT) ?: ""
        repo = ProjectRepository(this)
        val found = repo.findProject(projectName)
        if (found == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        project = found
        tokenStore = TokenStore(this)

        statusText = findViewById(R.id.cloudStatus)
        repoText = findViewById(R.id.cloudRepo)
        logText = findViewById(R.id.cloudLog)
        progress = findViewById(R.id.cloudProgress)
        btnBuild = findViewById(R.id.btnCloudBuild)
        btnInstall = findViewById(R.id.btnInstallApk)
        btnOpenRepo = findViewById(R.id.btnOpenRepo)

        logText.movementMethod = ScrollingMovementMethod()
        repoName = "cppstudio-" + CloudBuildManager.slug(project.name)
        repoText.text = getString(R.string.cloud_repo_line, ownerName(), repoName)

        btnBuild.setOnClickListener { startCloudBuild() }
        btnInstall.setOnClickListener { installApk() }
        btnOpenRepo.setOnClickListener { openRepoInBrowser() }
        btnInstall.isEnabled = false

        // Se já existir APK de uma build anterior, ativa logo o instalar
        val prev = File(project.rootDir, "cloud-builds")
        prev.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".apk") }
            ?.let { apkReady = it; btnInstall.isEnabled = true }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ── Fluxo principal ──────────────────────────────────────────────────────

    private fun ownerName(): String {
        // O login real é obtido com o token antes do build e fica em cache;
        // sem build anterior mostramos apenas um marcador neutro.
        return prefs().getString("cached_login", "github") ?: "github"
    }

    private fun prefs() = getSharedPreferences("cppstudio_cloud", MODE_PRIVATE)

    private fun startCloudBuild() {
        if (!tokenStore.has()) { askForToken(); return }

        val token = tokenStore.get() ?: return
        setBusy(true)
        statusText.text = getString(R.string.cloud_status_validating)
        logText.text = ""

        Thread {
            try {
                val api = GitHubApi(token)
                val login = api.authenticatedUser().optString("login", "github")
                prefs().edit().putString("cached_login", login).apply()
                runOnUiThread {
                    repoText.text = getString(R.string.cloud_repo_line, login, repoName)
                }

                val manager = CloudBuildManager(project, api, login, applicationContext)
                manager.run(object : CloudBuildManager.Listener {
                    override fun onStatus(message: String) = runOnUiThread {
                        statusText.text = message
                    }

                    override fun onLog(text: String) = runOnUiThread {
                        logText.text = text
                    }

                    override fun onSuccess(apk: File) = runOnUiThread {
                        apkReady = apk
                        btnInstall.isEnabled = true
                        statusText.text = getString(R.string.cloud_status_apk_ready)
                        Toast.makeText(this@CloudBuildActivity,
                            R.string.cloud_build_ok, Toast.LENGTH_LONG).show()
                    }

                    override fun onError(message: String) = runOnUiThread {
                        statusText.text = message
                        Toast.makeText(this@CloudBuildActivity,
                            R.string.cloud_build_failed, Toast.LENGTH_LONG).show()
                    }
                })
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        btnBuild.isEnabled = !busy
    }

    // ── Token ────────────────────────────────────────────────────────────────

    private fun askForToken() {
        val view = layoutInflater.inflate(R.layout.dialog_token, null)
        val input = view.findViewById<TextInputEditText>(R.id.tokenInput)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.token_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.token_save) { _, _ ->
                val t = input.text?.toString()?.trim() ?: ""
                if (t.length >= 30) {
                    tokenStore.save(t)
                    startCloudBuild()
                } else {
                    Toast.makeText(this, R.string.token_invalid, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Ações pós-build ──────────────────────────────────────────────────────

    private fun installApk() {
        val apk = apkReady ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.install_failed, e.message ?: "?"),
                Toast.LENGTH_LONG).show()
        }
    }

    private fun openRepoInBrowser() {
        val login = prefs().getString("cached_login", null)
        if (login == null) {
            Toast.makeText(this, R.string.cloud_no_repo_yet, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val i = Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/$login/$repoName"))
            startActivity(i)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_PROJECT = "extra_project"
    }
}
