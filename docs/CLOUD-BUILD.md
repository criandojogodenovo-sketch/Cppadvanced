# Build na nuvem (Fase 7) — "Compilar na nuvem"

Fluxo que leva um projeto do telemóvel a um APK instalável, **sem PC** e
**sem compilação local** (que ainda não existe — ver `docs/TOOLCHAIN-STRATEGY.md`).

## Como funciona (pipeline real, sem simulação)

```
[Telemóvel — C++ Mobile Studio]                [GitHub]
 1. Template AGDK gera projeto ─────┐
    (GameActivity + native_app_glue │
     + CMake + GLES 3.0 + wrapper)  │
 2. Token (encriptado) ─────────────┼─→ POST /user/repos      → cria repo cppstudio-<nome>
 3. Snapshot do projeto ────────────┼─→ POST /git/trees
                                    │   POST /git/commits
                                    │   POST/PATCH /git/refs   → push de 1 commit
 4. O push dispara o workflow ──────┼─→ .github/workflows/build.yml
                                    │     ./gradlew :app:assembleDebug (arm64-v8a)
 5. App monitoriza o run ───────────┼─→ GET /actions/runs (poll 15 s)
 6. Falhou? log de erros no app ────┼─→ GET /actions/jobs/{id}/logs
 7. Passou? descarrega artifact ────┼─→ GET /actions/artifacts/{id}/zip
 8. "Instalar APK" no app ──────────┘   ACTION_VIEW + FileProvider
```

## Segurança do token

- Guardado **apenas no telemóvel**, com `EncryptedSharedPreferences`
  (chave AES-256-GCM no Android Keystore — `cloud/TokenStore.kt`).
- Nunca é escrito em código, projetos, APKs, logs do app ou workflows.
- A UI só mostra os últimos 4 caracteres (`••••••••XyZ9`).
- Os projetos gerados **não contêm** o token: os workflows usam o
  `GITHUB_TOKEN` efémero do próprio Actions.
- Apagar o token: Definições → Token GitHub → Apagar.

## Template "Triângulo OpenGL ES 3.0 (AGDK)"

Projeto Android completo e independente, gerado em `<projetos>/<nome>/`:

| Ficheiro | Papel |
|---|---|
| `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` | Projeto Gradle moderno (AGP 8.7.3, Kotlin 2.0.21) |
| `gradlew` + `gradle/wrapper/*` | Wrapper completo (inclui o JAR do wrapper) |
| `app/build.gradle.kts` | `prefab=true`, `abiFilters arm64-v8a`, CMake 3.22.1 |
| `app/src/main/AndroidManifest.xml` | GameActivity em ecrã inteiro (paisagem) |
| `app/src/main/java/…/MainActivity.kt` | `GameActivity` + `System.loadLibrary` |
| `app/src/main/cpp/main.cpp` | Loop `android_main` (native_app_glue) + EGL + GLES 3.0 |
| `app/src/main/cpp/CMakeLists.txt` | `find_package(game-activity)` via Prefab |
| `app/src/main/assets/shaders/triangle.vert` / `.frag` | **Shaders em ficheiros separados** (lidos via AAssetManager) |
| `.github/workflows/build.yml` | CI: JDK 17, cache Gradle **e NDK**, APK debug arm64-v8a |
| `.gitignore` | Correto p/ Android: `build/`, `.cxx/`, `.gradle/`, `local.properties`, `*.apk`… |

## Estado da fase (honestidade acima de tudo)

**Feito (funciona de ponta a ponta):**
- Template AGDK completo materializado em ficheiros reais no telemóvel
- Push via API GitHub (tree + commit + ref — commits reais)
- Disparo e monitorização do workflow (poll de estado em tempo real)
- Log de erros do CI mostrado no app (jobs, passos falhados, tail de 150 linhas)
- Download do APK (artifact) para `<projeto>/cloud-builds/` + instalação
- Token encriptado; histórico real de builds na consola

**Placeholders declarados (não são funções falsas — são limites documentados):**
- Sincronização de push = snapshot integral (um commit por build, sem
  histórico incremental de edições intermédias)
- Filtro de ficheiros a subir é uma lista fixa (não analisa o `.gitignore`
  do projeto linha a linha)
- Sem alteração/recriação de repo (o app cria `cppstudio-<nome>` e mantém-no)
- Workflow só de debug/arm64-v8a (o template é o "primeiro teste", não o IDE final)

**Riscos conhecidos:**
- O código nativo do template (EGL/GLES + GameActivity) ainda **não foi
  compilado**: a primeira validação real acontece no Actions do repo gerado —
  é exatamente o ciclo previsto (log de erros no app → correção no editor)
- `EncryptedSharedPreferences` está em versão alpha (biblioteca oficial
  androidx; é a API-padrão para este caso)
- Repos criados como **públicos** para manter Actions ilimitado e grátis —
  aviso explícito ao utilizador na documentação
- Limites da API: 5000 pedidos/hora, artifacts expiram (90 dias por defeito)
- Primeira build demora ~5–10 min (download do NDK; depois fica em cache)

## Próximas fases

- ETAPA 4: clang/LLVM embutido (`libcppstudio-clang.so` em `jniLibs`) →
  compilação local real; o botão "Executar" deixa de avisar
- ETAPA 5: CMake local para projetos multi-ficheiro
- ETAPA 7 (continuação): templates SDL2/OpenGL ES com a mesma infraestrutura
