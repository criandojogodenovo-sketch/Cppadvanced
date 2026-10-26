package com.cppmobilestudio.app.console

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cppmobilestudio.app.R
import com.cppmobilestudio.app.build.BuildHistoryStore
import com.cppmobilestudio.app.build.ToolchainManager
import com.cppmobilestudio.app.docs.DocsActivity
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Consola e estado da toolchain (Fase C/D): mostra o estado REAL das ferramentas
 * no dispositivo e o histórico de builds. Não simula compilações.
 */
class ConsoleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val list = findViewById<LinearLayout>(R.id.toolchainList)
        for (st in ToolchainManager.status(this)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, dp(8), 0, dp(8))

            val name = TextView(this)
            name.text = if (st.available) "✓ ${st.name}" else "✗ ${st.name}"
            name.textSize = 14f
            name.setTextColor(getColor(if (st.available) R.color.success_green else R.color.error_red))

            val detail = TextView(this)
            detail.text = st.detail
            detail.textSize = 12f
            detail.setTextColor(getColor(R.color.text2_c))

            row.addView(name)
            row.addView(detail)
            list.addView(row)
        }

        val history = findViewById<TextView>(R.id.historyText)
        val records = BuildHistoryStore(this).load()
        history.text = if (records.isEmpty()) {
            getString(R.string.no_builds)
        } else {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            records.joinToString("\n\n") { r ->
                "[${fmt.format(Date(r.timestamp))}] ${r.project}: ${r.status}\n${r.summary}"
            }
        }

        findViewById<Button>(R.id.btnToolchainDocs).setOnClickListener {
            val i = Intent(this, DocsActivity::class.java)
            i.putExtra(DocsActivity.EXTRA_DOC, "TOOLCHAIN-STRATEGY.md")
            startActivity(i)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
