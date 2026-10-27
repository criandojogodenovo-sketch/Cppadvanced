package com.cppmobilestudio.app.toolchain

import android.content.Context
import java.io.File

/**
 * Estado REAL da toolchain no dispositivo (Fase C).
 * Nada de simulação: verifica de facto o nativeLibraryDir da app.
 * A toolchain local (clang/cmake empacotados em jniLibs) será instalada
 * nas ETAPAS 4-5; até lá o estado reportado é "não incluída nesta build".
 */
object ToolchainManager {

    data class ToolchainStatus(
        val id: String,
        val name: String,
        val available: Boolean,
        val detail: String
    )

    const val CLANG_LIB = "libcppstudio-clang.so"
    const val CMAKE_LIB = "libcppstudio-cmake.so"

    /** Lista o estado atual das ferramentas locais. */
    fun status(context: Context): List<ToolchainStatus> {
        val nativeDir: String? = try {
            context.applicationInfo.nativeLibraryDir
        } catch (_: Exception) {
            null
        }
        val native = nativeDir?.let(::File)

        val clangAvailable = native?.let { File(it, CLANG_LIB).exists() } == true
        val cmakeAvailable = native?.let { File(it, CMAKE_LIB).exists() } == true

        val pending = "A compilação local ainda NÃO existe (ETAPA 4/5). Usa “Compilar na nuvem” " +
            "para este projeto — o APK real sai do GitHub Actions."

        return listOf(
            ToolchainStatus(
                id = "clang-bundled",
                name = "Clang/LLVM (compilação local)",
                available = clangAvailable,
                detail = if (clangAvailable) "Disponível" else pending
            ),
            ToolchainStatus(
                id = "cmake-bundled",
                name = "CMake (projetos nativos)",
                available = cmakeAvailable,
                detail = if (cmakeAvailable) "Disponível" else pending
            ),
            ToolchainStatus(
                id = "remote-actions",
                name = "Compilação remota (GitHub Actions)",
                available = true,
                detail = "Opcional — apenas com autorização do utilizador (ETAPA 10)"
            )
        )
    }

    /** Executável clang, quando existir (usado a partir da ETAPA 4). */
    fun clangExecutable(context: Context): File? {
        val dir = context.applicationInfo.nativeLibraryDir ?: return null
        val f = File(dir, CLANG_LIB)
        return if (f.exists()) f else null
    }
}
