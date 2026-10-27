package com.cppmobilestudio.app.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda o token de acesso do GitHub de forma ENCRYPTADA no dispositivo
 * (EncryptedSharedPreferences com chave AES-256-GCM no Keystore do Android).
 *
 * Regras de segurança (docs/SECURITY.md):
 *  - o token NUNCA é escrito em código, projetos, APK, logs ou builds de CI;
 *  - o token NUNCA é enviado para o GitHub dos projetos gerados (os workflows
 *    usam o GITHUB_TOKEN efémero do próprio Actions);
 *  - a UI só mostra os últimos 4 caracteres.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            appContext,
            "cppstudio_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun get(): String? {
        val t = prefs.getString(KEY_TOKEN, null) ?: return null
        return t.takeIf { it.isNotBlank() }
    }

    fun has(): Boolean = !get().isNullOrBlank()

    /** Máscara segura para mostrar na UI: "••••••••XyZ9" (nunca o token inteiro). */
    fun masked(): String {
        val t = get() ?: return "—"
        return if (t.length <= 8) "••••" else "••••••••" + t.takeLast(4)
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_TOKEN = "github_pat"
    }
}
