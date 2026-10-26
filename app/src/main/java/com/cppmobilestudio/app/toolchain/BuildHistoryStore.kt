package com.cppmobilestudio.app.toolchain

import android.content.Context
import com.cppmobilestudio.app.files.FileOps
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Histórico persistente de builds (Fase C): guardado em filesDir/builds.json.
 */
class BuildHistoryStore(context: Context) {

    private val file = File(context.filesDir, "builds.json")

    data class BuildRecord(
        val timestamp: Long,
        val project: String,
        val status: String,      // "ok" | "error" | "cancelled" | "unavailable"
        val summary: String
    )

    fun add(record: BuildRecord) {
        val all = ArrayList(load())
        all.add(0, record)
        while (all.size > 100) all.removeAt(all.size - 1)
        save(all)
    }

    fun load(): List<BuildRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BuildRecord(
                    timestamp = o.getLong("t"),
                    project = o.getString("p"),
                    status = o.getString("s"),
                    summary = o.getString("m")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(records: List<BuildRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(
                JSONObject()
                    .put("t", r.timestamp)
                    .put("p", r.project)
                    .put("s", r.status)
                    .put("m", r.summary)
            )
        }
        try {
            FileOps.writeAtomic(file, arr.toString())
        } catch (_: Exception) {
        }
    }
}
