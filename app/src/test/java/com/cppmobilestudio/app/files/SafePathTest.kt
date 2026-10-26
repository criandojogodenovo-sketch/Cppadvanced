package com.cppmobilestudio.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SafePathTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `nomes validos sao aceites`() {
        assertTrue(SafePath.isValidEntryName("main.cpp"))
        assertTrue(SafePath.isValidEntryName("math_utils.hpp"))
        assertTrue(SafePath.isValidEntryName("CMakeLists.txt"))
        assertTrue(SafePath.isValidEntryName("projeto-01"))
    }

    @Test
    fun `traversal e recusado`() {
        assertFalse(SafePath.isValidEntryName(".."))
        assertFalse(SafePath.isValidEntryName("."))
        assertFalse(SafePath.isValidEntryName("a/b"))
        assertFalse(SafePath.isValidEntryName("a\\b"))
        assertFalse(SafePath.isValidEntryName("a:b"))
        assertFalse(SafePath.isValidEntryName(""))
        assertFalse(SafePath.isValidEntryName("  "))
        assertFalse(SafePath.isValidEntryName("nome\u0000"))
    }

    @Test
    fun `resolveInside aceita caminhos internos`() {
        val base = tmp.newFolder("base")
        val resolved = SafePath.resolveInside(base, "src/main.cpp")
        assertNotNull(resolved)
        assertTrue(SafePath.isInside(base.canonicalFile, resolved!!))
    }

    @Test
    fun `resolveInside recusa escape da base`() {
        val base = tmp.newFolder("base2")
        assertNull(SafePath.resolveInside(base, "../etc/passwd"))
        assertNull(SafePath.resolveInside(base, "/etc/passwd"))
    }

    @Test
    fun `entradas zip perigosas sao recusadas`() {
        assertFalse(SafePath.isSafeZipEntryName("../evil.sh"))
        assertFalse(SafePath.isSafeZipEntryName("/abs/path"))
        assertFalse(SafePath.isSafeZipEntryName("C:\\temp\\x.cpp"))
        assertTrue(SafePath.isSafeZipEntryName("src/main.cpp"))
        assertTrue(SafePath.isSafeZipEntryName("include/lib.hpp"))
    }

    @Test
    fun `isInside cobre raiz e filhos`() {
        val base = tmp.newFolder("base3").canonicalFile
        val child = File(base, "a/b.txt")
        assertTrue(SafePath.isInside(base, base))
        assertTrue(SafePath.isInside(base, child))
        assertFalse(SafePath.isInside(base, base.parentFile ?: File("/")))
    }

    @Test
    fun `nomes reservados do windows sao recusados`() {
        assertFalse(SafePath.isValidEntryName("CON"))
        assertFalse(SafePath.isValidEntryName("NUL.txt"))
        assertEquals(true, SafePath.isValidEntryName("console.cpp"))
    }
}
