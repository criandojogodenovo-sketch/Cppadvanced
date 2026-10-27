package com.cppmobilestudio.app.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Cliente GitHub REST v3 mínimo (Kotlin puro + org.json — sem dependências extra).
 * Cobre o fluxo "Compilar na nuvem":
 *   criar/verificar repo → snapshot (tree+commit+ref) → monitorizar workflow
 *   → ler logs de erros → descarregar artifact (APK).
 *
 * Segurança: o token só circula no header Authorization destes pedidos;
 * nunca é registado em logs nem gravado em ficheiros.
 */
class GitHubApi(private val token: String) {

    class ApiError(val code: Int, message: String) : IOException(message)

    data class TreeFile(
        val path: String,
        val content: ByteArray,
        val binary: Boolean = false
    )

    // ── Camada HTTP ──────────────────────────────────────────────────────────

    private fun open(method: String, urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = false // redirects geridos manualmente
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "CppMobileStudio")
        return conn
    }

    /** Executa o pedido seguindo redirects manualmente (até 5 saltos). */
    private fun request(
        method: String,
        urlStr: String,
        body: JSONObject? = null
    ): Pair<Int, ByteArray> {
        var url = urlStr
        var attempt = 0
        while (true) {
            val conn = open(method, url)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream: InputStream? =
                if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            val location = conn.getHeaderField("Location")
            conn.disconnect()

            if (code in 300..399 && location != null) {
                if (++attempt > 5) throw ApiError(code, "Demasiados redirects")
                url = if (location.startsWith("http")) location
                else URL(URL(urlStr), location).toString()
                continue
            }
            return code to bytes
        }
    }

    private fun requestJson(method: String, urlStr: String, body: JSONObject? = null): JSONObject {
        val (code, bytes) = request(method, urlStr, body)
        if (code !in 200..299) {
            throw ApiError(code, "HTTP $code: " + bytes.toString(Charsets.UTF_8).take(300))
        }
        return if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun requestText(method: String, urlStr: String): String {
        val (code, bytes) = request(method, urlStr)
        if (code !in 200..299) {
            throw ApiError(code, "HTTP $code ao ler log")
        }
        return String(bytes, Charsets.UTF_8)
    }

    // ── Utilizador / repositórios ────────────────────────────────────────────

    /** Utilizador autenticado — valida o token e obtém o "owner". */
    fun authenticatedUser(): JSONObject =
        requestJson("GET", "https://api.github.com/user")

    fun repoExists(owner: String, name: String): Boolean {
        val (code, _) = request("GET", "https://api.github.com/repos/$owner/$name")
        return code == 200
    }

    fun createRepo(name: String, isPrivate: Boolean): JSONObject {
        val body = JSONObject()
            .put("name", name)
            .put("private", isPrivate)
            .put("auto_init", false)
            .put("description", "Projeto gerado pelo C++ Mobile Studio")
        return requestJson("POST", "https://api.github.com/user/repos", body)
    }

    // ── Snapshot do projeto (blobs → tree → commit → ref) ────────────────────

    private fun createBlob(owner: String, repo: String, contentB64: String): String {
        val body = JSONObject().put("content", contentB64).put("encoding", "base64")
        return requestJson("POST", "https://api.github.com/repos/$owner/$repo/git/blobs", body)
            .getString("sha")
    }

    /** Cria uma árvore completa com TODOS os ficheiros (snapshot integral). */
    fun createTreeWithFiles(owner: String, repo: String, files: List<TreeFile>): String {
        val tree = JSONArray()
        for (f in files) {
            val entry = JSONObject()
                .put("path", f.path)
                .put("mode", "100644")
                .put("type", "blob")
            if (f.binary) {
                entry.put("sha", createBlob(
                    owner, repo,
                    Base64.getEncoder().encodeToString(f.content)
                ))
            } else {
                entry.put("content", String(f.content, Charsets.UTF_8))
            }
            tree.put(entry)
        }
        return requestJson("POST", "https://api.github.com/repos/$owner/$repo/git/trees",
            JSONObject().put("tree", tree)).getString("sha")
    }

    fun createCommit(owner: String, repo: String, treeSha: String,
                     message: String, parentSha: String?): String {
        val body = JSONObject().put("message", message).put("tree", treeSha)
        if (parentSha != null) body.put("parents", JSONArray().put(parentSha))
        return requestJson("POST", "https://api.github.com/repos/$owner/$repo/git/commits", body)
            .getString("sha")
    }

    /** Devolve o SHA atual de refs/heads/main ou null se a branch ainda não existir. */
    fun branchHead(owner: String, repo: String, branch: String = "main"): String? {
        val (code, bytes) = request("GET",
            "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch")
        if (code == 404) return null
        if (code !in 200..299) throw ApiError(code, "HTTP $code ao ler ref")
        return JSONObject(String(bytes, Charsets.UTF_8))
            .getJSONObject("object").getString("sha")
    }

    fun createRef(owner: String, repo: String, sha: String, branch: String = "main") {
        requestJson("POST", "https://api.github.com/repos/$owner/$repo/git/refs",
            JSONObject()
                .put("ref", "refs/heads/$branch")
                .put("sha", sha))
    }

    fun updateRef(owner: String, repo: String, sha: String, branch: String = "main") {
        requestJson("PATCH", "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch",
            JSONObject().put("sha", sha).put("force", true))
    }

    // ── Actions: runs, jobs, logs, artifacts ─────────────────────────────────

    fun workflowRuns(owner: String, repo: String, perPage: Int = 10): JSONObject =
        requestJson("GET",
            "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=$perPage")

    fun runJobs(owner: String, repo: String, runId: Long): JSONObject =
        requestJson("GET",
            "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs")

    /** Log textual de um job (a API responde com redirect — gerido manualmente). */
    fun jobLog(owner: String, repo: String, jobId: Long): String =
        requestText("GET",
            "https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs")

    fun runArtifacts(owner: String, repo: String, runId: Long): JSONObject =
        requestJson("GET",
            "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts")

    fun downloadArtifact(owner: String, repo: String, artifactId: Long,
                         out: java.io.OutputStream) {
        val (code, bytes) = request("GET",
            "https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId/zip")
        if (code !in 200..299) throw ApiError(code, "HTTP $code ao descarregar artifact")
        out.use { it.write(bytes) }
    }

    fun dispatchWorkflow(owner: String, repo: String, workflowFile: String, ref: String = "main") {
        val (code, bytes) = request("POST",
            "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches",
            JSONObject().put("ref", ref))
        if (code != 204) {
            throw ApiError(code, "HTTP $code ao disparar workflow: "
                + bytes.toString(Charsets.UTF_8).take(200))
        }
    }
}
