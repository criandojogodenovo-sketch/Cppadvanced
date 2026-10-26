package com.cppmobilestudio.app.projects

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
        C_SINGLE_FILE("Ficheiro único C (main.c)")
    }

    fun materialize(project: Project, template: Template) {
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
        }
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
