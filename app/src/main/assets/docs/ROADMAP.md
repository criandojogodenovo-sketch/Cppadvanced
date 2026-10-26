# ROADMAP — Estado real por etapa

Legenda: ✅ implementado · 🟡 parcialmente implementado · ⬜ pendente

| Etapa | Âmbito | Estado |
|---|---|---|
| 1 | Análise de requisitos, riscos e arquitetura | ✅ `docs/ARCHITECTURE.md`, `docs/TOOLCHAIN-STRATEGY.md` |
| 2 | Projeto Android completo: editor, explorador, persistência | ✅ Editor com realce C/C++, números de linha, auto-indent, auto-pares, bracket matching, undo/redo, find/replace, autosave + recuperação de crash; explorador com criar/renomear/mover/eliminar/duplicar, ZIP seguro, pesquisa por nome e conteúdo; dashboard de projetos, templates reais, definições, docs offline |
| 3 | Workflow GitHub Actions de build/teste/release APK | ✅ `.github/workflows/android-ci.yml` + `.github/workflows/android-release.yml` |
| 4 | Compilador C/C++ local (clang empacotado em jniLibs) | ⬜ Estratégia documentada em `docs/TOOLCHAIN-STRATEGY.md`; `ToolchainManager` já reporta estado real |
| 5 | CMake local multi-ficheiro | ⬜ |
| 6 | Gestor de bibliotecas | ⬜ |
| 7 | Templates SDL2/SDL3 + OpenGL ES | ⬜ |
| 8 | Vulkan (módulo independente) | ⬜ |
| 9 | Preparação Linux/OpenGL desktop | ⬜ |
| 10 | Builds remotos opcionais (APK via GitHub Actions a partir da app) | 🟡 Workflows prontos no repo; integração na app na ETAPA 10 |
| 11 | Testes de estabilidade/desempenho/segurança | 🟡 Testes unitários JVM do núcleo; testes de integração pendentes |
| 12 | Documentação final e limitações conhecidas | 🟡 Em evolução |

## Critérios de aceitação da primeira versão funcional

1. Abrir a app sem Internet — ✅
2. Criar um projeto C++ — ✅
3. Criar e editar main.cpp — ✅
4. Criar outros ficheiros e pastas — ✅
5. Guardar e reabrir o projeto — ✅
6. Compilar realmente com compilador local — ⬜ ETAPA 4 (estado real visível na app)
7. Mostrar erros reais da compilação — ⬜ ETAPA 4 (`DiagnosticParser` já pronto)
8. Executar o programa localmente — ⬜ ETAPA 4
9. Continuar a funcionar com a Internet desligada — ✅
10. Gerar APK de release via GitHub Actions — ✅ (workflow pronto; ver docs/BUILD.md)

## Limitações conhecidas (honestidade primeiro)

- A compilação local não existe nesta build — reportada como tal pela app.
- `SafVfs` (pastas externas via SAF) suporta operações básicas; algumas operações
  avançadas (mover entre árvores) fazem copiar+apagar.
- O realce de sintaxe é desativado automaticamente em ficheiros > 300 000 caracteres
  (proteção de desempenho), com aviso visível na UI.
- Bracket matching não considera contexto de strings/comentários (limitação documentada).
