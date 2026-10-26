package com.cppmobilestudio.app

import android.app.Application
import com.cppmobilestudio.app.settings.SettingsRepository

/** Aplicação: aplica o tema guardado no arranque. */
class CppStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepository(this).applyNightMode()
    }
}
