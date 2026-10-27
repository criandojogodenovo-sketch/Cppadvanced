package com.cppmobilestudio.app.cloud

import com.cppmobilestudio.app.files.ZipUtils
import com.cppmobilestudio.app.projects.Project
import com.cppmobilestudio.app.toolchain.BuildHistoryStore
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

/**
 * Orquestra o ciclo REAL de build na nuvem (sem simulação):
 *  1. garantir repo GitHub (cria se não existir);
 *  2. snapshot do projeto (tree + commit + push via API);
 *  3. o push dispara o workflow do próprio projeto;
 *  4. monitorizar o run até conclusão (ou timeout);
 *  5. em falha: descarregar e mostrar o log de erros;
 *  6. em sucesso: descarregar o APK (artifact) para <projeto>/cloud-builds/.
 *
 * O histórico de builds é registado no BuildHistoryStore (consola do app).
 */
class CloudBuildManager(
    private val project: Project,
    private val api: GitHubApi,
    private val owner: String,
    private val appContext: android.content.Context
) {

    private val contextDir: File get() = appContext.cacheDir

    interface Listener {
        fun onStatus(message: String)
        fun onLog(text: String)
        fun onSuccess(apk: File)
        fun onError(message: String)
    }

    val repoName: String = "cppstudio-" + slug(project.name)
    private val branch = "main"
    private val pollIntervalMs = 15_000L
    private val waitRunAppearMs = 90_000L
    private val maxRunDurationMs = 30 * 60_000L

    // Ficheiros/diretórios que NÃO sobem para o repo (espelham o .gitignore)
    private val ignoreDirs = setOf(
        ".git", ".gradle", "build", ".cxx", ".externalNativeBuild",
        ".idea", ".vscode", "captures", "cloud-builds"
    )
    private val ignoreFiles = setOf("local.properties", ".DS_Store")

    fun run(listener: Listener) {
        try {
            listener.onStatus("A preparar repositório $owner/$repoName…")
            ensureRepo(listener)

            listener.onStatus("A recolher ficheiros do projeto…")
            val files = collectFiles()
            if (files.isEmpty()) {
                listener.onError("Sem ficheiros para enviar")
                return
            }

            listener.onStatus("A enviar ${files.size} ficheiros (commit)…")
            val head = api.branchHead(owner, repoName, branch)
            val treeSha = api.createTreeWithFiles(owner, repoName, files)
            val commitSha = api.createCommit(
                owner, repoName, treeSha,
                "Build via C++ Mobile Studio — " + nowStamp(),
                parentSha = head
            )
            if (head == null) api.createRef(owner, repoName, commitSha, branch)
            else api.updateRef(owner, repoName, commitSha, branch)
            listener.onStatus("Commit $commitSha enviado. O workflow disparou.")

            val run = waitForRun(listener, commitSha)
                ?: run {
                    listener.onError("Workflow não arrancou em tempo útil (verifica a aba Actions do repo).")
                    record(false, "run não apareceu")
                    null
                } ?: return

            listener.onStatus("Run #$run: em execução…")
            val (status, conclusion) = watchRun(listener, run)

            when {
                status == "completed" && conclusion == "success" -> {
                    listener.onStatus("Build OK — a descarregar APK…")
                    val apk = downloadApk(listener, run)
                    if (apk != null) {
                        record(true, "APK gerado (${apk.length() / 1024} KB)")
                        listener.onSuccess(apk)
                    } else {
                        listener.onError("Build OK mas APK não encontrado nos artifacts")
                        record(false, "artifact em falta")
                    }
                }
                status == "completed" -> {
                    val log = fetchFailureLog(listener, run)
                    record(false, "workflow: $conclusion — ver log")
                    listener.onError("A compilação falhou. Log de erros abaixo.")
                    if (log.isNotBlank()) listener.onLog(log)
                }
                else -> {
                    listener.onError("Timeout à espera do workflow — verifica o repo no GitHub.")
                    record(false, "timeout")
                }
            }
        } catch (e: GitHubApi.ApiError) {
            val msg = if (e.code == 401)
                "Token recusado pelo GitHub (401). Vai a Definições e atualiza o token."
            else "Erro da API (${e.code}): ${e.message}"
            listener.onError(msg)
            record(false, "API ${e.code}")
        } catch (e: Exception) {
            listener.onError("Falha de rede: ${e.message ?: e.javaClass.simpleName}")
            record(false, "rede")
        }
    }

    // ── Passos ───────────────────────────────────────────────────────────────

    private fun ensureRepo(listener: Listener) {
        if (!api.repoExists(owner, repoName)) {
            listener.onStatus("A criar repo $owner/$repoName…")
            api.createRepo(repoName, isPrivate = false) // público = Actions grátis (ver relatório/risco)
        }
    }

    private fun collectFiles(): List<GitHubApi.TreeFile> {
        val out = ArrayList<GitHubApi.TreeFile>()
        val root = project.rootDir
        walk(root, root, out)
        return out
    }

    private fun walk(base: File, dir: File, out: MutableList<GitHubApi.TreeFile>) {
        val entries = dir.listFiles() ?: return
        for (f in entries.sortedBy { it.name.lowercase() }) {
            if (f.isDirectory) {
                if (f.name in ignoreDirs) continue
                walk(base, f, out)
            } else {
                if (f.name in ignoreFiles) continue
                if (f.name.endsWith(".apk")) continue
                val rel = f.relativeTo(base).invariantSeparatorsPath
                // Limites de sanidade: 5 MB por ficheiro, 500 ficheiros
                if (f.length() > 5L * 1024 * 1024) continue
                val binary = f.extension.lowercase() in BINARY_EXT
                out.add(GitHubApi.TreeFile(rel, f.readBytes(), binary))
                if (out.size >= 500) return
            }
        }
    }

    private fun waitForRun(listener: Listener, commitSha: String): Long? {
        val deadline = System.currentTimeMillis() + waitRunAppearMs
        while (System.currentTimeMillis() < deadline) {
            val arr = api.workflowRuns(owner, repoName)
                .optJSONArray("workflow_runs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optString("head_sha") == commitSha) return r.getLong("id")
            }
            Thread.sleep(5_000)
        }
        return null
    }

    private fun watchRun(listener: Listener, runId: Long): Pair<String, String> {
        val deadline = System.currentTimeMillis() + maxRunDurationMs
        var lastStatus = ""
        while (System.currentTimeMillis() < deadline) {
            val arr = api.workflowRuns(owner, repoName)
                .optJSONArray("workflow_runs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optLong("id") == runId) {
                    val st = r.optString("status", "")
                    val cc = r.optString("conclusion", "")
                    if (st != lastStatus) {
                        lastStatus = st
                        listener.onStatus("Estado do workflow: $st" +
                            (if (cc.isNotBlank()) " ($cc)" else ""))
                    }
                    if (st == "completed") return st to cc
                    break
                }
            }
            Thread.sleep(pollIntervalMs)
        }
        return "timeout" to ""
    }

    private fun fetchFailureLog(listener: Listener, runId: Long): String {
        return try {
            val jobs = api.runJobs(owner, repoName, runId)
                .optJSONArray("jobs") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until jobs.length()) {
                val job = jobs.getJSONObject(i)
                sb.append("── Job: ").append(job.optString("name"))
                    .append(" → ").append(job.optString("conclusion")).append('\n')
                val steps = job.optJSONArray("steps") ?: continue
                for (s in 0 until steps.length()) {
                    val st = steps.getJSONObject(s)
                    if (st.optString("conclusion") != "success") {
                        sb.append("   passo: ").append(st.optString("name"))
                            .append(" → ").append(st.optString("conclusion")).append('\n')
                    }
                }
                val log = api.jobLog(owner, repoName, job.getLong("id"))
                sb.append('\n').append(tail(log, 150)).append("\n\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "Não foi possível obter o log: ${e.message}"
        }
    }

    private fun downloadApk(listener: Listener, runId: Long): File? {
        val artifacts = api.runArtifacts(owner, repoName, runId)
            .optJSONArray("artifacts") ?: return null
        var artifactId = -1L
        for (i in 0 until artifacts.length()) {
            val a = artifacts.getJSONObject(i)
            if (a.optString("name") == "app-debug-apk" &&
                !a.optBoolean("expired", false)) {
                artifactId = a.getLong("id")
                break
            }
        }
        if (artifactId < 0) return null

        val buildsDir = File(project.rootDir, "cloud-builds")
        if (!buildsDir.exists()) buildsDir.mkdirs()
        val tmpZip = File(contextDir, "artifact-$runId.zip")
        api.downloadArtifact(owner, repoName, artifactId, FileOutputStream(tmpZip))

        val extract = ZipUtils.extractSafely(tmpZip.inputStream().buffered(), buildsDir)
        tmpZip.delete()
        if (extract !is ZipUtils.ExtractResult.Success) return null

        return buildsDir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".apk") }
    }

    private fun record(ok: Boolean, summary: String) {
        try {
            BuildHistoryStore(appContext).add(
                BuildHistoryStore.BuildRecord(
                    timestamp = System.currentTimeMillis(),
                    project = project.name,
                    status = if (ok) "ok" else "error",
                    summary = "nuvem/$repoName — $summary"
                )
            )
        } catch (_: Exception) {
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private fun tail(text: String, maxLines: Int): String {
        val lines = text.lines()
        return if (lines.size <= maxLines) text
        else lines.takeLast(maxLines).joinToString("\n")
    }

    private fun nowStamp(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date())

    companion object {
        private val BINARY_EXT = setOf("jar", "png", "jpg", "webp", "so", "zip")

        /** Slug seguro p/ nome de repo e package: minúsculas, a-z0-9 e '_'. */
        fun slug(name: String): String {
            val s = name.lowercase()
                .map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }
                .joinToString("")
                .trim('_')
            val fixed = if (s.isEmpty() || s[0].isDigit()) "app_$s" else s
            return fixed.take(40)
        }
    }
}
