# Compilar e obter APKs (GitHub Actions)

A app é **100% offline** para editar e gerir projetos. A nuvem só é usada para
gerar APKs — e sempre a pedido do utilizador.

## 1. Builds automáticas (CI)

O workflow `.github/workflows/android-ci.yml` corre em cada push/PR:

1. Instala JDK 17 (Temurin) e Gradle 8.10.2 (com cache).
2. Corre os testes unitários: `./gradlew :app:testDebugUnitTest`.
3. Compila o APK debug: `./gradlew :app:assembleDebug`.
4. Publica o APK como **artefacto** do workflow.

Para descarregar: página do repo → **Actions** → build mais recente →
secção **Artifacts** → `app-debug-apk`. Transferir o ZIP, extrair, instalar
o APK (ativar "Instalar apps desconhecidas" se pedido).

## 2. APK de release

O workflow `.github/workflows/android-release.yml` corre:

- **Manualmente**: Actions → "Android Release Build" → Run workflow;
- **Por tag**: `git tag v0.1.0 && git push origin v0.1.0`.

O que faz:
1. Compila `assembleRelease` (e `assembleDebug`).
2. Assina o APK de release com um keystore gerado em CI (apk instalável para
   testes) — ou com o teu keystore de produção via segredos (abaixo).
3. Publica artefactos e, em pushes de tag, cria uma **GitHub Release** com os APKs.

## 3. Assinatura de produção (opcional, via segredos — NUNCA no código)

Para uma release assinada com o teu keystore:

1. Cria o keystore no telemóvel (ex.: com uma app keystore generator) ou noutro PC.
2. Repo → **Settings → Secrets and variables → Actions** e cria:
   - `KEYSTORE_BASE64` — o ficheiro `.jks` codificado em base64;
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. O workflow deteta os segredos e assina a release com eles; sem segredos,
   usa assinatura debug (claramente marcada como build de teste).

> ⚠️ Segurança: nunca escrevas passwords, keystores ou tokens no código-fonte,
> nos logs ou no APK. O keystore gerado em CI serve apenas para testes locais
> de instalação; para distribuição estável, mantém um keystore teu e fixa a
> mesma chave entre releases.

## 4. Compilar localmente (num PC, se um dia tiveres)

```bash
git clone https://github.com/criandojogodenovo-sketch/Cppadvanced.git
cd Cppadvanced
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # testes unitários
```

## 5. Versões fixadas

- Gradle 8.10.2 (wrapper) · AGP 8.7.3 · Kotlin 2.0.21 · JDK 17
- compileSdk 35 · minSdk 26 · targetSdk 35
