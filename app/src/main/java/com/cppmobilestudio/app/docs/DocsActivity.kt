package com.cppmobilestudio.app.docs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cppmobilestudio.app.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * Documentação integrada, lida dos assets — funciona 100% offline (Fase M).
 */
class DocsActivity : AppCompatActivity() {

    private var docs: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_docs)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        docs = try {
            assets.list("docs")?.filter { it.endsWith(".md") }?.sorted() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val adapter = DocsAdapter(docs) { doc -> showDoc(doc) }
        val list = findViewById<RecyclerView>(R.id.docsList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val requested = intent.getStringExtra(EXTRA_DOC)
        if (requested != null && docs.contains(requested)) {
            showDoc(requested)
        }
    }

    private fun showDoc(name: String) {
        val content = try {
            assets.open("docs/$name").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Erro a ler o documento: ${e.message}"
        }
        val tv = findViewById<TextView>(R.id.docContent)
        val list = findViewById<RecyclerView>(R.id.docsList)
        tv.visibility = View.VISIBLE
        list.visibility = View.GONE
        supportActionBar?.title = name
        tv.text = content
    }

    override fun onSupportNavigateUp(): Boolean {
        if (findViewById<View>(R.id.docContent).visibility == View.VISIBLE) {
            findViewById<View>(R.id.docContent).visibility = View.GONE
            findViewById<RecyclerView>(R.id.docsList).visibility = View.VISIBLE
            supportActionBar?.title = getString(R.string.docs_title)
            return true
        }
        finish()
        return true
    }

    private inner class DocsAdapter(
        private val items: List<String>,
        val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<DocsAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val meta: TextView = v.findViewById(R.id.meta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_entry, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val doc = items[position]
            holder.name.text = doc
            holder.meta.text = "documento"
            holder.itemView.setOnClickListener { onClick(doc) }
        }
    }

    companion object {
        const val EXTRA_DOC = "extra_doc"
    }
}
