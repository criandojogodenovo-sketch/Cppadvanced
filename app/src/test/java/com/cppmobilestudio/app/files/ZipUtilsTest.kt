package com.cppmobilestudio.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun `extrai zip valido`() {
        val zip = buildZip(
            listOf(
                "proj/main.cpp" to "int main(){}".toByteArray(),
                "proj/readme.md" to "hello".toByteArray()
            )
        )
        val dest = tmp.newFolder("out")
        val result = ZipUtils.extractSafely(ByteArrayInputStream(zip), dest)
        assertTrue(result is ZipUtils.ExtractResult.Success)
        assertEquals(2, (result as ZipUtils.ExtractResult.Success).filesExtracted)
        assertTrue(File(dest, "proj/main.cpp").exists())
        assertEquals("int main(){}", File(dest, "proj/main.cpp").readText())
    }

    @Test
    fun `recusa zip com zip-slip`() {
        val zip = buildZip(listOf("../evil.txt" to "x".toByteArray()))
        val dest = tmp.newFolder("out2")
        val result = ZipUtils.extractSafely(ByteArrayInputStream(zip), dest)
        assertTrue(result is ZipUtils.ExtractResult.Failed)
        assertTrue(!File(dest.parentFile, "evil.txt").exists())
    }

    @Test
    fun `recusa zip com caminho absoluto`() {
        val zip = buildZip(listOf("/abs/x.txt" to "x".toByteArray()))
        val dest = tmp.newFolder("out3")
        val result = ZipUtils.extractSafely(ByteArrayInputStream(zip), dest)
        assertTrue(result is ZipUtils.ExtractResult.Failed)
    }

    @Test
    fun `zipDirectory cria arquivo reexr ativavel`() {
        val src = tmp.newFolder("proj")
        File(src, "a.cpp").writeText("// a")
        File(src, "sub").mkdirs()
        File(src, "sub/b.cpp").writeText("// b")
        val bos = ByteArrayOutputStream()
        ZipUtils.zipDirectory(src, src.name, bos)
        val dest = tmp.newFolder("extracted")
        val result = ZipUtils.extractSafely(ByteArrayInputStream(bos.toByteArray()), dest)
        assertTrue(result is ZipUtils.ExtractResult.Success)
        assertEquals("// a", File(dest, "proj/a.cpp").readText())
        assertEquals("// b", File(dest, "proj/sub/b.cpp").readText())
    }

    @Test
    fun `zip corrompido falha graciosamente`() {
        val dest = tmp.newFolder("out4")
        val garbage = "isto nao e um zip".toByteArray()
        val result = ZipUtils.extractSafely(ByteArrayInputStream(garbage), dest)
        assertTrue(result is ZipUtils.ExtractResult.Failed)
    }
}
