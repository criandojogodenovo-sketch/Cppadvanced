# Triângulo OpenGL ES 3.0 (AGDK)

Projeto Android completo gerado pelo **C++ Mobile Studio**:
um triângulo GLES 3.0 a correr com **GameActivity** (AGDK) + **native_app_glue**,
compilado nativamente com CMake/Clang para **arm64-v8a**.

## Estrutura

| Caminho | Papel |
|---|---|
| `app/src/main/cpp/main.cpp` | Loop de jogo (native_app_glue), EGL + GLES 3.0 |
| `app/src/main/cpp/CMakeLists.txt` | Build nativa; liga `game-activity::game-activity` (Prefab) |
| `app/src/main/assets/shaders/triangle.vert` | Vertex shader (ficheiro separado) |
| `app/src/main/assets/shaders/triangle.frag` | Fragment shader (ficheiro separado) |
| `app/src/main/java/.../MainActivity.kt` | GameActivity (carrega a lib nativa) |
| `.github/workflows/build.yml` | CI: APK debug arm64-v8a com cache |

## Como compilar

### Na nuvem (recomendado a partir do C++ Mobile Studio)
1. No C++ Mobile Studio: **menu → Compilar na nuvem…**
2. O app faz push deste projeto para o teu GitHub → o workflow cria o APK debug
3. Descarrega e instala o APK pelo botão no app

### Manualmente
```bash
./gradlew :app:assembleDebug
# APK em app/build/outputs/apk/debug/
```

## Notas técnicas

- `EGL_OPENGL_ES3_BIT` + `EGL_CONTEXT_CLIENT_VERSION 3` → contexto ES 3.0 real
- Os shaders são lidos em runtime via `AAssetManager` (sem hardcode no C++)
- `glVertexAttribPointer` com stride 20 bytes: posição (2 floats) + cor (3 floats)
- O render para/retoma com `APP_CMD_INIT_WINDOW` / `APP_CMD_TERM_WINDOW`

Se algo falhar na nuvem, o log de erros aparece no próprio C++ Mobile Studio.
