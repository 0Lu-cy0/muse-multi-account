package com.example.musecontainer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A named clone. The Muse APK itself is NEVER copied into this app's bundle:
 *  it is imported at runtime from a file the user supplies, and only its
 *  path + metadata are recorded here. Each clone gets its own data dir
 *  under filesDir/clones/<id>/ . Real process/data isolation for a guest
 *  app only happens once a virtual engine is integrated; see EngineLauncher. */
data class CloneEntry(
    val id: String,
    val name: String,
    val sourcePackage: String,
    val sourceVersionName: String,
    val sourceApkPath: String,
    val sourceSha256: String,
    val active: Boolean,
    val createdAtMs: Long
)

object CloneStore {
    private fun file(context: Context) = File(context.filesDir, "clones.json")

    fun load(context: Context): MutableList<CloneEntry> {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CloneEntry(
                    o.getString("id"), o.getString("name"),
                    o.getString("sourcePackage"), o.optString("sourceVersionName", ""),
                    o.getString("sourceApkPath"), o.optString("sourceSha256", ""),
                    o.optBoolean("active", false), o.optLong("createdAtMs", 0L)
                )
            }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun save(context: Context, clones: List<CloneEntry>) {
        val arr = JSONArray()
        for (c in clones) {
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name)
                put("sourcePackage", c.sourcePackage)
                put("sourceVersionName", c.sourceVersionName)
                put("sourceApkPath", c.sourceApkPath)
                put("sourceSha256", c.sourceSha256)
                put("active", c.active); put("createdAtMs", c.createdAtMs)
            })
        }
        file(context).writeText(arr.toString())
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            var n: Int
            while (ins.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun dataDir(context: Context, cloneId: String): File =
        File(context.filesDir, "clones/$cloneId").also { it.mkdirs() }
}
