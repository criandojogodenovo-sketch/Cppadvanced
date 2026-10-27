package com.cppmobilestudio.app.settings

import android.os.Bundle
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cppmobilestudio.app.BuildConfig
import com.cppmobilestudio.app.R
import com.cppmobilestudio.app.cloud.GitHubApi
import com.cppmobilestudio.app.cloud.TokenStore
import com.cppmobilestudio.app.projects.ProjectRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

/** Definições da app (Fase M): tema, editor, armazenamento. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = SettingsRepository(this)

        // Tema
        val rbSystem = findViewById<RadioButton>(R.id.themeSystem)
        val rbDark = findViewById<RadioButton>(R.id.themeDark)
        val rbLight = findViewById<RadioButton>(R.id.themeLight)
        when (settings.themeMode) {
            SettingsRepository.THEME_DARK -> rbDark.isChecked = true
            SettingsRepository.THEME_LIGHT -> rbLight.isChecked = true
            else -> rbSystem.isChecked = true
        }
        val applyTheme = {
            settings.themeMode = when {
                rbDark.isChecked -> SettingsRepository.THEME_DARK
                rbLight.isChecked -> SettingsRepository.THEME_LIGHT
                else -> SettingsRepository.THEME_SYSTEM
            }
            settings.applyNightMode()
        }
        rbSystem.setOnClickListener { applyTheme() }
        rbDark.setOnClickListener { applyTheme() }
        rbLight.setOnClickListener { applyTheme() }

        // Tamanho de letra
        val fontSizeValue = findViewById<TextView>(R.id.fontSizeValue)
        fontSizeValue.text = settings.fontSizeSp.toString()
        findViewById<android.view.View>(R.id.fontPlus).setOnClickListener {
            settings.fontSizeSp = settings.fontSizeSp + 1
            fontSizeValue.text = settings.fontSizeSp.toString()
        }
        findViewById<android.view.View>(R.id.fontMinus).setOnClickListener {
            settings.fontSizeSp = settings.fontSizeSp - 1
            fontSizeValue.text = settings.fontSizeSp.toString()
        }

        // Autosave
        val swAutosave = findViewById<MaterialSwitch>(R.id.swAutosave)
        swAutosave.isChecked = settings.autosaveEnabled
        swAutosave.setOnCheckedChangeListener { _, checked ->
            settings.autosaveEnabled = checked
        }

        // Realce
        val swHighlight = findViewById<MaterialSwitch>(R.id.swHighlight)
        swHighlight.isChecked = settings.highlightEnabled
        swHighlight.setOnCheckedChangeListener { _, checked ->
            settings.highlightEnabled = checked
        }

        // Recentes
        findViewById<android.view.View>(R.id.btnClearRecents).setOnClickListener {
            ProjectRepository(this).clearRecents()
            Toast.makeText(this, R.string.recents_cleared, Toast.LENGTH_SHORT).show()
        }

        // Token GitHub — encriptado (EncryptedSharedPreferences)
        setupTokenSection()

        findViewById<TextView>(R.id.versionInfo).text =
            getString(R.string.version_label) + ": " + BuildConfig.VERSION_NAME
    }

    private fun setupTokenSection() {
        val tokenStore = TokenStore(this)
        val tokenStatus = findViewById<TextView>(R.id.tokenStatus)
        val tokenInput = findViewById<TextInputEditText>(R.id.tokenInput)

        tokenStatus.text = getString(R.string.token_current, tokenStore.masked())

        findViewById<android.view.View>(R.id.btnTokenSave).setOnClickListener {
            val t = tokenInput.text?.toString()?.trim() ?: ""
            if (t.length >= 30) {
                tokenStore.save(t)
                tokenInput.setText("")
                tokenStatus.text = getString(R.string.token_current, tokenStore.masked())
                Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.token_invalid, Toast.LENGTH_LONG).show()
            }
        }

        findViewById<android.view.View>(R.id.btnTokenTest).setOnClickListener {
            val token = tokenStore.get()
            if (token == null) {
                Toast.makeText(this, R.string.token_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            tokenStatus.text = getString(R.string.loading)
            Thread {
                try {
                    val login = GitHubApi(token).authenticatedUser().optString("login")
                    runOnUiThread {
                        tokenStatus.text = getString(R.string.token_test_ok, login)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tokenStatus.text = getString(R.string.token_test_fail,
                            (e.message ?: "erro").take(60))
                    }
                }
            }.start()
        }

        findViewById<android.view.View>(R.id.btnTokenDelete).setOnClickListener {
            tokenStore.clear()
            tokenStatus.text = getString(R.string.token_current, tokenStore.masked())
            Toast.makeText(this, R.string.token_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
