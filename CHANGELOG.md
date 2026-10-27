# Changelog

Todas as mudanças notáveis são registadas aqui. Formato baseado em Keep a Changelog.

## [0.4.0-alpha] — Fase 7 (build na nuvem + primeiro teste OpenGL)

### Adicionado
- Template **“Triângulo OpenGL ES 3.0 (AGDK)”**: projeto Android COMPLETO gerado
  no telemóvel — GameActivity (AGDK) + native_app_glue, CMakeLists.txt,
  app/build.gradle.kts com prefab/abiFilters arm64-v8a, MainActivity, shaders
  separados (.vert/.frag lidos via AAssetManager), .gitignore correto,
  gradle wrapper completo e workflow próprio (`.github/workflows/build.yml`)
  com cache de Gradle e NDK.
- **“Compilar na nuvem”**: commit + push do projeto via API do GitHub
  (blobs → tree → commit → ref), disparo e monitorização do workflow em tempo
  real, log de erros do CI no app (jobs/passos/tail), download do APK
  (artifact) e instalação via FileProvider (`docs/CLOUD-BUILD.md`).
- **Token GitHub encriptado** no dispositivo (EncryptedSharedPreferences,
  AES-256-GCM no Keystore) com secção nova nas Definições (guardar/testar/
  apagar, máscara ••••XyZ9).
- Aviso honesto do “Compilar/Executar”: afirma claramente que a compilação
  local ainda NÃO existe (ETAPA 4) e oferece o atalho “Compilar na nuvem”.

### Segurança
- O token nunca sai do telemóvel: não é escrito em código, projetos, APKs,
  logs ou workflows; os projetos gerados usam o GITHUB_TOKEN efémero do Actions.
- Nova permissão INTERNET (apenas para o build na nuvem) e
  REQUEST_INSTALL_PACKAGES (instalação do APK gerado).

### Notas
- `docs/CLOUD-BUILD.md` documenta estado real: feito, placeholders declarados
  e riscos (incluindo validação do código nativo AGDK no Actions).

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
