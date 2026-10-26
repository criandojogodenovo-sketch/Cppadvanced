# Estratégia de Toolchain C/C++ Local (ETAPA 4 — análise técnica)

Este documento analisa, com rigor, como compilar C/C++ **dentro do próprio
telemóvel**, respeitando as restrições do Android. É a base da ETAPA 4.

## 1. O problema central: a política W^X do Android

Desde o Android 10 (API 29), para apps com `targetSdkVersion >= 29`:

- **`exec()` de ficheiros no armazenamento da app (`filesDir`, `cacheDir`,
  armazenamento externo da app) está BLOQUEADO** (`selinux mmc_execdenial`,
  `W^X`: um ficheiro não pode ser simultaneamente gravável e executável).
- Consequência: **não é possível** descarregar um `clang` para uma pasta e
  executá-lo com `Runtime.exec()`. Qualquer app que diga o contrário está
  a mentir ou usa `targetSdk <= 28`.
- O que **continua executável**: ficheiros instalados em
  `ApplicationInfo.nativeLibraryDir` (`/data/app/…/lib/arm64/`), ou seja,
  binários empacotados no APK como `jniLibs` com nome `lib*.so`.

## 2. Opções comparadas

| Opção | Técnica | Viável? | Notas |
|---|---|---|---|
| A. Binários Linux x86/qualquer via Termux | exec de binários de outra app | ❌ | Sandbox impede; requer root ou shell ADB. |
| B. Toolchain dinâmica descarregada para `filesDir` | exec de ficheiro gravável | ❌ | Bloqueado pela política W^X (API 29+). |
| C. **Toolchain empacotada em `jniLibs` do APK** (`libcppstudio-clang.so` → executável clang) | exec em `nativeLibraryDir` (só leitura + executável) | ✅ | É a abordagem do CxxDroid e de outras IDEs móveis. Custo: APK grande. |
| D. Compilação remota (GitHub Actions) | nuvem, sempre sob autorização | ✅ (opcional) | Complemento, nunca substituto. |
| E. Interpretar/emular (ex.: TinyCC JIT) | compilador embutido como biblioteca nativa da app | ⚠️ parcial | TinyCC suporta C (não C++17 completo); útil como fallback de emergência, não como base. |

**Decisão: Opção C (jniLibs) como base da ETAPA 4, com Opção D para APKs.**

## 3. Desenho da Opção C (implementação futura, ETAPA 4)

1. Obter/compilar **clang + lld + libc++ estática** para o alvo
   "Linux aarch64 sem libc/bionic host" que corra dentro do processo da app:
   - Fonte recomendada: construir LLVM/Clang no GitHub Actions (runner linux-x64,
     cross para aarch64) ou reutilizar uma build oficial da LLVM.
   - A toolchain inclui: `clang` (driver), `lld`, headers e `libc++` estática.
2. Empacotar cada executável como `app/src/main/jniLibs/arm64-v8a/libcppstudio-clang.so`
   (o nome `lib…so` é obrigatório para oinstalador os colocar em `nativeLibraryDir`).
3. Em runtime, a app constrói o comando:
   `"$nativeLibraryDir/libcppstudio-clang.so" -std=c++17 main.cpp -o "$filesDir/out/app" ...`
   — e para **executar o programa resultante**:
   - O binário produzido pelo clang em `filesDir` NÃO é executável diretamente (W^X).
   - Solução: compilar para um executável e lançá-lo através de um *loader* que
     vive em `nativeLibraryDir` (`libcppstudio-runner.so` que faz `execve` do
     binário alvo após o carregar, ou usar `dlopen` + entrypoint), ou
   - Alternativa robusta: compilar o programa do utilizador para uma
     **biblioteca partilhada** e executá-la via o runner nativo com `dlopen`.
4. Headers e sysroot: empacotados como assets comprimidos, extraídos uma única
   vez para `filesDir/sysroot` (leitura apenas para o compilador — compilar
   `#include`s não exige que sejam executáveis).

### Limitações conhecidas e honestas desta opção

- Tamanho do APK cresce tipicamente **150–400 MB** com clang+lld+libc++.
- Memória: clang no telemóvel com 4 GB RAM compila projetos pequenos/médios;
  unidades de tradução gigantes podem falhar por OOM (limites configuráveis).
- Não há garantia de 100% de compatibilidade com todos os recursos do NDK.
- O tempo de compilação é superior ao de um PC (ordens de grandeza: 2–10×).

## 4. Distinguir os quatro alvos de compilação (NUNCA misturar na UI)

| Alvo | O que é | Como |
|---|---|---|
| **Host Android** (ETAPA 4) | Binário ARM64 Linux/bionic que corre no telemóvel | clang empacotado (Opção C) |
| **Android NDK** | Biblioteca/APK Android completo | NDK no GitHub Actions (remoto) |
| **Linux desktop** | Binário x86-64 Linux | GitHub Actions runner (remoto) |
| **APK Android completo** | App instalável | GitHub Actions com Gradle+NDK (remoto) |

A UI apresenta cada um separadamente, com o seu próprio estado e requisitos.

## 5. Estado atual (verdade, sem simulação)

Nas builds das ETAPAS 1–3 **não há toolchain local empacotada**.
`ToolchainManager` verifica de facto `nativeLibraryDir` e reporta:

- `clang-bundled`: **não disponível nesta build** (previsto na ETAPA 4);
- `cmake-bundled`: **não disponível nesta build** (previsto na ETAPA 5);
- compilação remota: disponível apenas com autorização explícita (ETAPA 10).

Não existe nenhum botão que simule compilação. O botão "Compilar" apresenta
este estado real e liga à documentação.
