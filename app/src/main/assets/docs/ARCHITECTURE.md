# C++ Mobile Studio — Arquitetura

> Última atualização: ETAPA 1–3 concluídas (ver `docs/ROADMAP.md` para o estado real de cada fase).

## 1. Visão geral

O C++ Mobile Studio é um ambiente de desenvolvimento C/C++ para Android, desenhado
para utilizadores que programam exclusivamente num telemóvel ARM64 (≈4 GB RAM, sem PC,
sem root). O princípio fundamental é **offline-first**: editar, organizar e (na ETAPA 4)
compilar funcionam sem Internet; a nuvem (GitHub Actions) é **opcional** e só é usada
quando o utilizador autoriza explicitamente uma operação remota.

```
┌───────────────────────────────────────────────────────────┐
│  UI (Kotlin + Material 3, View-based, sem Compose)        │
│  Dashboard · Editor · Explorador · Consola · Definições   │
├───────────────────────────────────────────────────────────┤
│  Camada de domínio (Kotlin puro, testável em JVM)         │
│  ProjectRepository · ProjectTemplates · TextSearchEngine  │
│  DiagnosticParser · UndoRedoManager · CppTokenizer        │
├───────────────────────────────────────────────────────────┤
│  Sistema de ficheiros (VfsLayer)                          │
│  LocalVfs (armazenamento privado da app)                  │
│  SafVfs (pastas escolhidas pelo utilizador via SAF)       │
│  SafePath (validação anti path-traversal)                 │
├───────────────────────────────────────────────────────────┤
│  Toolchain local (ETAPA 4)      │  Backend remoto opcional│
│  ToolchainManager (estado real) │  GitHub Actions (K)     │
└───────────────────────────────────────────────────────────┘
```

## 2. Decisões tecnológicas e justificação

| Componente | Escolha | Justificação |
|---|---|---|
| Linguagem da app | Kotlin 2.0.21 | Padrão atual do Android; interop total com JNI quando necessário. |
| UI | Views + Material 3 (XML) | Sem Compose: menor overhead de memória e de startup em dispositivos modestos (4 GB RAM), menos dependências, controle total do editor. |
| Editor de código | `EditText` personalizado (`CodeEditorView`) | Realce de sintaxe por spans, gutter com números de linha, auto-indent, auto-pares, undo/redo — tudo local, sem serviços externos. |
| Persistência | Ficheiros + `SharedPreferences` + `org.json` | Zero dependências extra; JSON nativo do Android. |
| Armazenamento | `getExternalFilesDir()` para projetos + SAF para pastas do utilizador | Sem permissões perigosas (RUNTIME); compatível com o sandbox Android. |
| Min SDK | 26 (Android 8.0) | Cobre a grande maioria dos telemóveis ativos; ícone adaptativo nativo. |
| Target SDK | 35 (Android 15) | Requisito Play Store 2025+; implica a restrição W^X documentada abaixo. |
| CI | GitHub Actions + JDK 17 (Temurin) + Gradle 8.10.2 + AGP 8.7.3 | Versões fixadas e mutuamente compatíveis; builds reproduzíveis. |

## 3. Módulos internos

- `ui` — Activities e layouts: dashboard de projetos, editor, explorador, consola,
  definições, documentação integrada (assets offline).
- `editor` — `CodeEditorView` (componente principal), `CppTokenizer` (scanner manual,
  sem regex, para desempenho), `SyntaxHighlighter` (spans), `UndoRedoManager`,
  `FindReplaceDialog`, paletas escura/clara.
- `projects` — modelo `Project` (`project.json` em `.cppstudio/`), repositório de
  projetos recentes, templates reais (consola, CMake multi-ficheiro, biblioteca, C).
- `files` — `VfsLayer` com duas implementações (`LocalVfs`, `SafVfs`), `SafePath`
  (normalização e proteção contra path traversal), `ZipUtils` (extração segura
  contra zip-slip, limites de tamanho e de entradas), `TextSearchEngine`.
- `build` — `ToolchainManager` (verifica o estado REAL da toolchain no dispositivo),
  `DiagnosticParser` (parser dos formatos clang/gcc `ficheiro:linha:coluna`),
  `BuildHistoryStore` (histórico persistente de builds).
- `settings` — preferências (tema, tamanho de fonte, autosave, recuperação).
- `docs` — documentação offline incluída nos assets da app.

## 4. Modelo de dados de projeto

Cada projeto vive em `<raiz>/<nome>/` e contém:

```
meu-projeto/
├── .cppstudio/
│   └── project.json      # metadados (ver abaixo)
├── main.cpp              # conforme o template
├── CMakeLists.txt        # nos templates CMake
└── README.md
```

`project.json`:

```json
{
  "name": "meu-projeto",
  "createdAt": "2026-10-27T10:00:00Z",
  "language": "cpp",
  "cppStandard": "C++17",
  "buildSystem": "cmake",
  "compiler": "clang-bundled (pendente, ETAPA 4)",
  "architecture": "arm64-v8a",
  "target": "host-android",
  "dependencies": [],
  "buildOptions": {},
  "configStatus": "template"
}
```

## 5. Estratégia de compilação local (resumo — detalhe em TOOLCHAIN-STRATEGY.md)

O Android (targetSdk ≥ 29) impede `exec()` de binários em armazenamento gravável
(política W^X). A solução tecnicamente correta, usada por apps como o CxxDroid, é
empacotar a toolchain como bibliotecas "falsas" (`libcppstudio-clang.so`, etc.) em
`jniLibs/` — o Android instala-as em `nativeLibraryDir/`, que é executável. Isto é
implementado na ETAPA 4. Até lá, `ToolchainManager` reporta honestamente que a
compilação local **não está disponível nesta build** — não existem botões falsos.

## 6. Segurança

- Validação de caminhos em todas as operações de ficheiros (`SafePath`).
- Extração ZIP protegida contra zip-slip, com limites de tamanho/entradas.
- Confirmação explícita para operações destrutivas.
- Autosave + ficheiros de recuperação (`.cppstudio/recovery/`) contra perda de dados.
- Nenhum token/keystore/segredo no código-fonte; builds assinadas de release via
  segredos do GitHub (ver `docs/BUILD.md`).
- Permissões Android: nenhuma perigosa necessária.

## 7. Testes

Testes unitários JVM reais (executados no CI) para lógica pura:
`SafePath`, `ZipUtils`, `DiagnosticParser`, `UndoRedoManager`, `CppTokenizer`,
`TextSearchEngine`. Os resultados do CI são a única fonte de verdade sobre o estado
dos testes — nunca afirmamos que passaram sem execução verificável.
