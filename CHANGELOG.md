# Changelog

Todas as mudanças notáveis são registadas aqui. Formato baseado em Keep a Changelog.

## [0.3.0-alpha] — ETAPAS 1–3

### Adicionado
- Arquitetura documentada (`docs/ARCHITECTURE.md`) e estratégia séria de toolchain
  local (`docs/TOOLCHAIN-STRATEGY.md`) com a análise da política W^X do Android.
- Editor de código C/C++ (Fase A): realce de sintaxe próprio (scanner manual),
  números de linha, auto-indent, auto-pares com skip-over, bracket matching,
  undo/redo com agrupamento, pesquisa/substituição (com regex), ir para linha,
  ajuste de letra, temas escuro/claro, autosave e recuperação após crash.
- Gestor de projetos e ficheiros (Fase B): criar/abrir projetos com templates
  reais (consola C++17, CMake multi-ficheiro, biblioteca estática, C), criar/
  renomear/duplicar/eliminar ficheiros e pastas, importar/exportar ZIP com
  proteção zip-slip, pesquisa por nome e conteúdo, projetos recentes,
  pastas externas via SAF, definições do projeto persistidas em project.json.
- ToolchainManager com estado REAL (sem simulações) e histórico de builds.
- CI: workflow de testes + APK debug; workflow de release APK (manual/tags),
  com assinatura por segredos opcionais (`docs/BUILD.md`).
- Testes unitários JVM: SafePath, ZipUtils (zip-slip/bomba), DiagnosticParser,
  UndoRedoManager, CppTokenizer, TextSearchEngine.

### Segurança
- Validação de caminhos e nomes, confirmação de eliminação, extração ZIP
  com limites, gravação atómica, zero credenciais no código.

### Notas
- A compilação C/C++ local chega na ETAPA 4 — a app mostra o estado real.
