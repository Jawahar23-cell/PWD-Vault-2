package com.example.passwordvault

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One stored credential. Only [label] is plaintext (so the list can render without
 * authenticating). [ivBase64] + [cipherBase64] hold the AES-GCM encrypted secret.
 * [timestamp] tracks when this entry was last modified.
 */
data class VaultEntry(
    val id: String,
    val label: String,
    val ivBase64: String,
    val cipherBase64: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class VaultStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<VaultEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VaultEntry(
                id = o.getString("id"),
                label = o.getString("label"),
                ivBase64 = o.getString("iv"),
                cipherBase64 = o.getString("cipher"),
                timestamp = o.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }

    fun save(entries: List<VaultEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("label", e.label)
                put("iv", e.ivBase64)
                put("cipher", e.cipherBase64)
                put("timestamp", e.timestamp)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "vault_store"
        private const val KEY = "entries"
    }
}
