package com.cppmobilestudio.app.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Camada de sistema de ficheiros virtual (Fase B):
 *  - LocalVfs: projetos no armazenamento privado da app (File I/O direto);
 *  - SafVfs: pastas escolhidas pelo utilizador via Storage Access Framework.
 */
sealed class VNode {
    abstract fun displayName(): String

    data class Local(val file: File) : VNode() {
        override fun displayName(): String = file.name
    }

    data class Saf(val treeUri: Uri, val relativePath: String) : VNode() {
        override fun displayName(): String = relativePath.trim('/').substringAfterLast('/')
    }

    /** Serialização para passagem por Intents (nunca expõe segredos). */
    fun encode(): String = when (this) {
        is Local -> "local:" + file.absolutePath
        is Saf -> "saf:" + treeUri.toString() + "|" + relativePath
    }

    companion object {
        fun decode(encoded: String): VNode? {
            return when {
                encoded.startsWith("local:") -> Local(File(encoded.removePrefix("local:")))
                encoded.startsWith("saf:") -> {
                    val body = encoded.removePrefix("saf:")
                    val idx = body.indexOf('|')
                    if (idx <= 0) return null
                    val uri = Uri.parse(body.substring(0, idx))
                    val rel = body.substring(idx + 1)
                    Saf(uri, rel)
                }
                else -> null
            }
        }
    }
}

data class VEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

interface Vfs {
    /** Lista os filhos de [dir]. Devolve lista vazia se o nó não existir. */
    fun list(dir: VNode): List<VEntry>

    fun exists(node: VNode): Boolean
    fun isDirectory(node: VNode): Boolean
    fun readText(node: VNode): String
    fun writeText(node: VNode, content: String)
    fun createFile(dir: VNode, name: String): VNode?
    fun createDir(dir: VNode, name: String): VNode?
    fun rename(node: VNode, newName: String): VNode?
    fun delete(node: VNode): Boolean
    fun child(dir: VNode, name: String): VNode?
    fun parent(node: VNode): VNode?

    /** Nome amigável da raiz do sistema de ficheiros (para breadcrumbs). */
    fun rootLabel(node: VNode): String
}

class LocalVfs(private val guardRoot: File) : Vfs {

    private fun guard(node: VNode.Local): File? {
        val canonical = node.file.canonicalFile
        return if (SafePath.isInside(guardRoot.canonicalFile, canonical)) canonical else null
    }

    override fun list(dir: VNode): List<VEntry> {
        val d = guard(dir as? VNode.Local ?: return emptyList()) ?: return emptyList()
        val children = d.listFiles() ?: return emptyList()
        return children
            .filter { !it.name.startsWith(".tmp-") }
            .map {
                VEntry(
                    name = it.name,
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isFile) it.length() else -1L,
                    lastModified = it.lastModified()
                )
            }
            .sortedWith(compareByDescending<VEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun exists(node: VNode): Boolean =
        (node as? VNode.Local)?.let { guard(it)?.exists() } == true

    override fun isDirectory(node: VNode): Boolean =
        (node as? VNode.Local)?.let { guard(it)?.isDirectory } == true

    override fun readText(node: VNode): String {
        val f = guard(node as? VNode.Local ?: return "")
            ?: throw SecurityException("Caminho fora da raiz autorizada")
        return f.readText(Charsets.UTF_8)
    }

    override fun writeText(node: VNode, content: String) {
        val f = guard(node as? VNode.Local ?: return)
            ?: throw SecurityException("Caminho fora da raiz autorizada")
        FileOps.writeAtomic(f, content)
    }

    override fun createFile(dir: VNode, name: String): VNode? {
        val d = guard(dir as? VNode.Local ?: return null) ?: return null
        if (!SafePath.isValidEntryName(name)) return null
        val target = File(d, name)
        if (target.exists()) return null
        if (!target.createNewFile()) return null
        return VNode.Local(target)
    }

    override fun createDir(dir: VNode, name: String): VNode? {
        val d = guard(dir as? VNode.Local ?: return null) ?: return null
        if (!SafePath.isValidEntryName(name)) return null
        val target = File(d, name)
        if (target.exists()) return null
        if (!target.mkdirs()) return null
        return VNode.Local(target)
    }

    override fun rename(node: VNode, newName: String): VNode? {
        val f = guard(node as? VNode.Local ?: return null) ?: return null
        if (!SafePath.isValidEntryName(newName)) return null
        val target = File(f.parentFile, newName)
        if (target.exists()) return null
        if (!f.renameTo(target)) return null
        return VNode.Local(target)
    }

    override fun delete(node: VNode): Boolean {
        val f = guard(node as? VNode.Local ?: return false) ?: return false
        val root = guardRoot.canonicalFile
        if (f.path == root.path) return false // nunca apagar a raiz de projetos
        return FileOps.deleteInside(root, f)
    }

    override fun child(dir: VNode, name: String): VNode? {
        val d = guard(dir as? VNode.Local ?: return null) ?: return null
        if (!SafePath.isValidEntryName(name)) return null
        val c = File(d, name)
        return if (c.exists()) VNode.Local(c) else null
    }

    override fun parent(node: VNode): VNode? {
        val f = guard(node as? VNode.Local ?: return null) ?: return null
        val p = f.parentFile ?: return null
        return if (SafePath.isInside(guardRoot.canonicalFile, p.canonicalFile)) VNode.Local(p) else null
    }

    override fun rootLabel(node: VNode): String {
        val f = (node as? VNode.Local)?.file
        return if (f != null && f.canonicalPath == guardRoot.canonicalFile.path) "Projetos"
        else node.displayName()
    }
}

/** Fábrica de Vfs — raiz de projetos no armazenamento privado da app. */
object VfsLayer {

    fun projectsRoot(context: Context): File {
        val ext = context.getExternalFilesDir(null)
        return File(ext ?: context.filesDir, "projects")
    }

    fun local(context: Context): LocalVfs = LocalVfs(projectsRoot(context))

    fun saf(context: Context): SafVfs = SafVfs(context)
}

/**
 * SAF: suporta operações básicas (listar, ler, escrever, criar, eliminar, renomear).
 * Nota documentada: percorrer árvores SAF é mais lento que File I/O.
 */
class SafVfs(private val context: Context) : Vfs {

    private fun resolve(node: VNode.Saf): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, node.treeUri) ?: return null
        val segs = node.relativePath.trim('/').split('/').filter { it.isNotBlank() }
        var cur = root
        for (s in segs) {
            cur = cur.findFile(s) ?: return null
        }
        return cur
    }

    private fun saf(node: VNode): DocumentFile? {
        val s = node as? VNode.Saf ?: return null
        return resolve(s)
    }

    override fun list(dir: VNode): List<VEntry> {
        val d = saf(dir) ?: return emptyList()
        return d.listFiles()
            .take(5000) // proteção de desempenho em árvores gigantes
            .map {
                VEntry(
                    name = it.name ?: "?",
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isFile) it.length() else -1L,
                    lastModified = it.lastModified()
                )
            }
            .sortedWith(compareByDescending<VEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun exists(node: VNode): Boolean = saf(node) != null

    override fun isDirectory(node: VNode): Boolean = saf(node)?.isDirectory == true

    override fun readText(node: VNode): String {
        val f = saf(node) ?: return ""
        return context.contentResolver.openInputStream(f.uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: ""
    }

    override fun writeText(node: VNode, content: String) {
        val f = saf(node) ?: throw java.io.IOException("Ficheiro SAF inexistente")
        // "wt" para truncar corretamente
        context.contentResolver.openOutputStream(f.uri, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw java.io.IOException("Sem permissão de escrita para o ficheiro")
    }

    override fun createFile(dir: VNode, name: String): VNode? {
        val d = saf(dir) ?: return null
        if (!SafePath.isValidEntryName(name)) return null
        if (d.findFile(name) != null) return null
        val mime = if (name.endsWith(".zip")) "application/zip" else "text/plain"
        val created = d.createFile(mime, name) ?: return null
        return VNode.Saf((dir as VNode.Saf).treeUri, (dir.relativePath.trim('/') + "/" + name).replace("//", "/"))
            .takeIf { resolve(it) != null } ?: run {
            // fallback: devolve o nó construído mesmo se resolve falhar (alguns providers demoram)
            VNode.Saf((dir as VNode.Saf).treeUri, (dir.relativePath.trim('/') + "/" + name))
        }
    }

    override fun createDir(dir: VNode, name: String): VNode? {
        val d = saf(dir) ?: return null
        if (!SafePath.isValidEntryName(name)) return null
        if (d.findFile(name) != null) return null
        val created = d.createDirectory(name) ?: return null
        val dn = created.name ?: name
        return VNode.Saf(
            (dir as VNode.Saf).treeUri,
            (dir.relativePath.trim('/') + "/" + dn).replace("//", "/")
        )
    }

    override fun rename(node: VNode, newName: String): VNode? {
        val f = saf(node) ?: return null
        if (!SafePath.isValidEntryName(newName)) return null
        if (!f.renameTo(newName)) return null
        val parent = parent(node) as? VNode.Saf ?: return null
        return VNode.Saf(parent.treeUri, (parent.relativePath.trim('/') + "/" + newName).replace("//", "/"))
    }

    override fun delete(node: VNode): Boolean = saf(node)?.delete() == true

    override fun child(dir: VNode, name: String): VNode? {
        val d = saf(dir) ?: return null
        if (d.findFile(name) == null) return null
        return VNode.Saf(
            (dir as VNode.Saf).treeUri,
            (dir.relativePath.trim('/') + "/" + name).replace("//", "/")
        )
    }

    override fun parent(node: VNode): VNode? {
        val n = node as? VNode.Saf ?: return null
        val rel = n.relativePath.trim('/')
        if (rel.isBlank()) return null // já está na raiz
        val parentRel = rel.substringBeforeLast('/', "")
        return VNode.Saf(n.treeUri, parentRel)
    }

    override fun rootLabel(node: VNode): String {
        val n = node as? VNode.Saf ?: return "Pasta"
        val root = DocumentFile.fromTreeUri(context, n.treeUri)
        return root?.name ?: "Pasta SAF"
    }
}
