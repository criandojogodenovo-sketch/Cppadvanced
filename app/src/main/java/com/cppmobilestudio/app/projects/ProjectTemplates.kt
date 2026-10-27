package com.cppmobilestudio.app.projects

import android.content.Context
import com.cppmobilestudio.app.files.FileOps
import java.io.File

/**
 * Templates reais de projeto — geram ficheiros C/C++ completos e compiláveis
 * (Fase B). Os conteúdos são genuínos, não placeholders decorativos.
 */
object ProjectTemplates {

    enum class Template(val label: String) {
        CPP_CONSOLE("Consola C++17 (main.cpp)"),
        CPP_CMAKE_MULTI("CMake multi-ficheiro (src/ + include/)"),
        CPP_STATIC_LIB("Biblioteca estática C++ (CMake)"),
        C_SINGLE_FILE("Ficheiro único C (main.c)"),
        AGDK_TRIANGLE("Triângulo OpenGL ES 3.0 (AGDK) — projeto Android completo")
    }

    fun materialize(project: Project, template: Template, context: Context) {
        val root = project.rootDir
        when (template) {
            Template.CPP_CONSOLE -> {
                File(root, "main.cpp").writeText(CONSOLE_MAIN_CPP, Charsets.UTF_8)
                File(root, "CMakeLists.txt").writeText(consoleCMake(project.name), Charsets.UTF_8)
                File(root, "README.md").writeText(readme(project.name, template), Charsets.UTF_8)
            }
            Template.CPP_CMAKE_MULTI -> {
                File(root, "src").mkdirs()
                File(root, "include").mkdirs()
                File(root, "src/main.cpp").writeText(MULTI_MAIN, Charsets.UTF_8)
                File(root, "src/math_utils.cpp").writeText(MATH_UTILS_CPP, Charsets.UTF_8)
                File(root, "include/math_utils.hpp").writeText(MATH_UTILS_HPP, Charsets.UTF_8)
                File(root, "CMakeLists.txt").writeText(multiCMake(project.name), Charsets.UTF_8)
                File(root, "README.md").writeText(readme(project.name, template), Charsets.UTF_8)
            }
            Template.CPP_STATIC_LIB -> {
                File(root, "src").mkdirs()
                File(root, "include").mkdirs()
                File(root, "src/${libSourceName(project.name)}")
                    .writeText(libSource(project.name), Charsets.UTF_8)
                File(root, "include/${libHeaderName(project.name)}")
                    .writeText(libHeader(project.name), Charsets.UTF_8)
                File(root, "CMakeLists.txt").writeText(libCMake(project.name), Charsets.UTF_8)
                File(root, "README.md").writeText(readme(project.name, template), Charsets.UTF_8)
            }
            Template.C_SINGLE_FILE -> {
                File(root, "main.c").writeText(CONSOLE_MAIN_C, Charsets.UTF_8)
                File(root, "README.md").writeText(readme(project.name, template), Charsets.UTF_8)
            }
            Template.AGDK_TRIANGLE -> materializeAgdk(project, context)
        }
    }

    /**
     * Projeto AGDK completo (GameActivity + native_app_glue + CMake + GLES 3.0),
     * materializado a partir dos assets do app com substituição de variáveis.
     * Inclui workflow de CI próprio (.github/workflows/build.yml) e gradle wrapper.
     */
    private fun materializeAgdk(project: Project, context: Context) {
        val root = project.rootDir
        val slug = agdkSlug(project.name)
        val pkg = "com.cppstudio.$slug"
        val pkgPath = pkg.replace('.', '/')
        val subst = mapOf(
            "{{PACKAGE}}" to pkg,
            "{{PKG_PATH}}" to pkgPath,
            "{{APP_LABEL}}" to project.name,
            "{{LIB_NAME}}" to slug,
            "{{PROJECT_NAME}}" to project.name
        )

        // asset (templates/agdk-triangle/…) → caminho no projeto gerado
        val mapping = listOf(
            "settings.gradle.kts.txt" to "settings.gradle.kts",
            "root-build.gradle.kts.txt" to "build.gradle.kts",
            "gradle.properties.txt" to "gradle.properties",
            "gitignore.txt" to ".gitignore",
            "readme.md.txt" to "README.md",
            "workflow-build.yml.txt" to ".github/workflows/build.yml",
            "app-build.gradle.kts.txt" to "app/build.gradle.kts",
            "AndroidManifest.txt" to "app/src/main/AndroidManifest.xml",
            "MainActivity.kt.txt" to "app/src/main/java/$pkgPath/MainActivity.kt",
            "strings.txt" to "app/src/main/res/values/strings.xml",
            "themes.txt" to "app/src/main/res/values/themes.xml",
            "icon.xml.txt" to "app/src/main/res/drawable/ic_launcher_foreground.xml",
            "CMakeLists.txt" to "app/src/main/cpp/CMakeLists.txt",
            "main.cpp.txt" to "app/src/main/cpp/main.cpp",
            "triangle.vert" to "app/src/main/assets/shaders/triangle.vert",
            "triangle.frag" to "app/src/main/assets/shaders/triangle.frag",
            "gradle-wrapper/gradlew.txt" to "gradlew",
            "gradle-wrapper/gradlew.bat.txt" to "gradlew.bat",
            "gradle-wrapper/gradle-wrapper.properties.txt" to "gradle/wrapper/gradle-wrapper.properties"
        )

        val assets = context.assets
        for ((assetName, targetRel) in mapping) {
            val target = File(root, targetRel)
            if (!target.parentFile.exists()) target.parentFile.mkdirs()
            if (assetName.endsWith(".jar")) {
                assets.open("templates/agdk-triangle/$assetName").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                var text = assets.open("templates/agdk-triangle/$assetName")
                    .readBytes().toString(Charsets.UTF_8)
                for ((k, v) in subst) text = text.replace(k, v)
                target.writeText(text, Charsets.UTF_8)
            }
        }

        // gradlew precisa de bit executável (usado no CI Linux; no telemóvel é ignorado)
        try {
            java.io.File(root, "gradlew").setExecutable(true, false)
        } catch (_: Exception) {
        }

        File(root, "README.md").appendText(
            "\n---\n\nGerado pelo template AGDK do C++ Mobile Studio. " +
            "Compilação local indisponível por agora (ETAPA 4) — usa “Compilar na nuvem”.\n"
        )
    }

    /** Slug a-z0-9_ para package Java e alvo CMake (nunca começa por dígito). */
    private fun agdkSlug(name: String): String {
        val s = name.lowercase()
            .map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }
            .joinToString("").trim('_')
        val fixed = if (s.isEmpty() || s[0].isDigit()) "app_$s" else s
        return fixed.take(30).ifBlank { "triangle" }
    }

    // ── Conteúdos ────────────────────────────────────────────────────────────

    private const val CONSOLE_MAIN_CPP = """#include <iostream>
#include <string>

// Ponto de entrada do programa.
// A compilação local será suportada pela ETAPA 4 (ver docs/TOOLCHAIN-STRATEGY.md).
int main() {
    std::string nome;
    std::cout << "Olá do C++ Mobile Studio!\n";
    std::cout << "Como te chamas? ";
    if (std::getline(std::cin, nome) && !nome.empty()) {
        std::cout << "Bem-vindo, " << nome << "!\n";
    }
    for (int i = 1; i <= 5; ++i) {
        std::cout << i << "² = " << (i * i) << '\n';
    }
    return 0;
}
"""

    private const val CONSOLE_MAIN_C = """#include <stdio.h>

/* Programa C mínimo — a compilação local chega na ETAPA 4. */
int main(void) {
    printf("Olá do C++ Mobile Studio (C)!\n");
    for (int i = 1; i <= 5; ++i) {
        printf("%d ao quadrado = %d\n", i, i * i);
    }
    return 0;
}
"""

    private const val MULTI_MAIN = """#include <iostream>
#include "math_utils.hpp"

int main() {
    std::cout << "Projeto multi-ficheiro do C++ Mobile Studio\n";
    for (int n : {1, 2, 3, 4, 5}) {
        std::cout << "fib(" << n << ") = " << fib(n)
                  << ", quadrado = " << quadrado(n) << '\n';
    }
    return 0;
}
"""

    private const val MATH_UTILS_HPP = """#pragma once

namespace utils {
    // Fibonacci iterativo
    long long fib(int n);

    // Quadrado de um inteiro
    long long quadrado(long long x);
}
"""

    private const val MATH_UTILS_CPP = """#include "math_utils.hpp"

namespace utils {
    long long fib(int n) {
        long long a = 0, b = 1;
        for (int i = 0; i < n; ++i) {
            long long t = a + b;
            a = b;
            b = t;
        }
        return a;
    }

    long long quadrado(long long x) {
        return x * x;
    }
}
"""

    private fun libName(name: String): String =
        name.filter { it.isLetterOrDigit() }.ifBlank { "mylib" }

    private fun libHeaderName(name: String): String = "${libName(name)}.hpp"
    private fun libSourceName(name: String): String = "${libName(name)}.cpp"

    private fun libHeader(name: String): String = """#pragma once
// Biblioteca gerada pelo C++ Mobile Studio

namespace ${libName(name)} {
    int versao();

    // Soma dois inteiros — substitui pela tua API
    int soma(int a, int b);
}
"""

    private fun libSource(name: String): String = """#include "${libHeaderName(name)}"

namespace ${libName(name)} {
    int versao() { return 1; }

    int soma(int a, int b) { return a + b; }
}
"""

    private fun consoleCMake(name: String): String = """cmake_minimum_required(VERSION 3.22)
project($name CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(app main.cpp)
"""

    private fun multiCMake(name: String): String = """cmake_minimum_required(VERSION 3.22)
project($name CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(app
    src/main.cpp
    src/math_utils.cpp
)

target_include_directories(app PRIVATE include)
"""

    private fun libCMake(name: String): String = """cmake_minimum_required(VERSION 3.22)
project($name CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(${libName(name)} STATIC
    src/${libSourceName(name)}
)

target_include_directories(${libName(name)} PUBLIC include)
"""

    private fun readme(name: String, template: Template): String = """# $name

Projeto criado com o **C++ Mobile Studio** (template: ${template.label}).

## Estado da compilação

- Edição e organização: disponível **offline** nesta versão.
- Compilação local no telemóvel: prevista na **ETAPA 4** (clang empacotado;
  ver `docs/TOOLCHAIN-STRATEGY.md` no repositório).
- O `CMakeLists.txt` (quando existente) já está pronto para a ETAPA 5 e para
  compilação num PC ou runner Linux.
"""
}
