# C++ Mobile Studio

[![Android CI](https://github.com/criandojogodenovo-sketch/Cppadvanced/actions/workflows/android-ci.yml/badge.svg)](https://github.com/criandojogodenovo-sketch/Cppadvanced/actions/workflows/android-ci.yml)

Ambiente de desenvolvimento C/C++ para Android, desenhado para quem programa
**exclusivamente num telemóvel** — sem PC, sem root, offline por defeito.

## Estado do projeto

Ver **[docs/ROADMAP.md](docs/ROADMAP.md)** para o estado real (verificado por CI)
de cada etapa. Resumo:

- ✅ Editor de código C/C++ com realce de sintaxe, números de linha, auto-indent,
  auto-pares, bracket matching, undo/redo, pesquisa/substituição, autosave e
  recuperação após crash.
- ✅ Gestor de projetos e ficheiros: criar/renomear/mover/eliminar/duplicar,
  ZIP seguro (importar/exportar), pesquisa por nome e conteúdo, projetos recentes.
- ✅ Compilação de APKs de release via GitHub Actions (opcional, nuvem só a pedido).
- ⬜ Compilação C/C++ local no telemóvel (ETAPA 4 — estratégia documentada em
  [docs/TOOLCHAIN-STRATEGY.md](docs/TOOLCHAIN-STRATEGY.md); a app mostra o estado
  real, sem simulações).

## Instalar

Descarrega o APK mais recente em **Actions** (build de CI) ou em **Releases**
(APK de release): ver [docs/BUILD.md](docs/BUILD.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitetura e decisões técnicas |
| [docs/TOOLCHAIN-STRATEGY.md](docs/TOOLCHAIN-STRATEGY.md) | Como compilar C++ dentro do Android (análise séria) |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Estado por etapa + limitações conhecidas |
| [docs/BUILD.md](docs/BUILD.md) | Como obter/compilar APKs e assinar releases |
| [docs/SECURITY.md](docs/SECURITY.md) | Segurança de ficheiros, ZIP e credenciais |

## Licenças de terceiros

As dependências AndroidX/Material são Apache 2.0. A toolchain local (ETAPA 4)
usará LLVM/Clang (Apache 2.0 com exceções LLVM).
