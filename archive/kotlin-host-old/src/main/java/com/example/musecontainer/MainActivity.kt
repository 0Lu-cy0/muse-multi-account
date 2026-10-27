package com.example.musecontainer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var clonesContainer: LinearLayout
    private lateinit var nameInput: EditText
    private var importedApk: File? = null
    private var importedPackage: String? = null
    private var importedVersion: String? = null
    private val engine: EngineLauncher = StubEngineLauncher()

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = (uri.lastPathSegment ?: "imported.apk").takeLast(80)
            val dest = File(File(filesDir, "imports").also { it.mkdirs() }, name)
            contentResolver.openInputStream(uri)?.use { ins ->
                dest.outputStream().use { out -> ins.copyTo(out) }
            }
            val info = packageManager.getPackageArchiveInfo(dest.absolutePath, 0)
            importedApk = dest
            importedPackage = info?.packageName ?: "unknown"
            importedVersion = info?.versionName ?: ""
            statusText.text = "Imported: $name\npackage=${importedPackage} version=${importedVersion} " +
                "size=${dest.length() / 1024 / 1024} MB\n" +
                "NOTE: This APK was supplied by you. It is stored in app-private storage, " +
                "not bundled into this host app, and it was NOT modified or re-signed."
        } catch (e: Exception) {
            statusText.text = "Import failed: ${e.message}"
        }
    }

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(this, if (granted) "Notifications allowed" else "Notifications denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        clonesContainer = findViewById(R.id.clonesContainer)
        nameInput = findViewById(R.id.cloneNameInput)

        findViewById<Button>(R.id.btnPickApk).setOnClickListener {
            pickApk.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.btnCreateClone).setOnClickListener { createClone() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { requestBatteryExemption() }
        findViewById<Button>(R.id.btnKeepAlive).setOnClickListener { startKeepAlive() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshList() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        refreshList()
        updateBatteryStatus()
    }

    private fun createClone() {
        val apk = importedApk ?: run {
            Toast.makeText(this, "Pick an APK first", Toast.LENGTH_SHORT).show(); return
        }
        val clones = CloneStore.load(this)
        val name = nameInput.text.toString().ifBlank { "Clone ${clones.size + 1}" }
        val id = UUID.randomUUID().toString()
        val entry = CloneEntry(
            id, name, importedPackage ?: "unknown", importedVersion ?: "",
            apk.absolutePath, CloneStore.sha256(apk), active = false,
            System.currentTimeMillis()
        )
        val dir = CloneStore.dataDir(this, id)
        File(dir, "README.txt").writeText(
            "Host-side data dir for clone '$name' ($id).\n" +
            "Source package: ${entry.sourcePackage}\n" +
            "Real guest isolation requires a virtual engine (see ENGINE_EVALUATION.md).\n"
        )
        clones.add(entry)
        CloneStore.save(this, clones)
        nameInput.setText("")
        Toast.makeText(this, "Created $name", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun refreshList() {
        clonesContainer.removeAllViews()
        val clones = CloneStore.load(this)
        val active = clones.filter { it.active }
        val header = TextView(this).apply {
            text = "Clones: ${clones.size} total, ${active.size} active\n" +
                "Target for simultaneous online: 3-4. Active here means you marked " +
                "it active — it does NOT mean a guest Muse process is running yet."
            textSize = 14f
        }
        clonesContainer.addView(header)
        for (c in clones) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(TextView(this).apply {
                text = "${c.name} — ${c.sourcePackage} ${c.sourceVersionName} " +
                    "[${if (c.active) "ACTIVE" else "idle"}]\ndata: clones/${c.id}/"
            })
            val launchBtn = Button(this).apply { text = "Launch clone" }
            launchBtn.setOnClickListener {
                when (val r = engine.launchClone(c)) {
                    is EngineResult.NotIntegrated -> statusText.text = "LAUNCH BLOCKED (honest stub):\n${r.reason}"
                    is EngineResult.Launched -> statusText.text = "Launched: ${r.detail}"
                    is EngineResult.Failed -> statusText.text = "Launch failed: ${r.detail}"
                }
            }
            val toggleBtn = Button(this).apply {
                text = if (c.active) "Set idle" else "Set active"
            }
            toggleBtn.setOnClickListener {
                val current = CloneStore.load(this)
                // Hard performance cap: max 4 active. Beyond the cap, clones stay dormant.
                if (!c.active && current.count { it.active } >= 4) {
                    val msg = "Active cap reached (4). Set another clone to idle first — " +
                        "clones beyond the cap stay dormant (no process, no sync, no polling)."
                    statusText.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val updated = current.map {
                    if (it.id == c.id) it.copy(active = !it.active) else it
                }
                CloneStore.save(this, updated)
                refreshList()
            }
            row.addView(launchBtn)
            row.addView(toggleBtn)
            clonesContainer.addView(row)
        }
        if (clones.isEmpty()) {
            clonesContainer.addView(TextView(this).apply { text = "No clones yet. Pick an APK, then Create clone." })
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already exempt from battery optimisation", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            statusText.text = "Battery-exemption intent failed: ${e.message}. " +
                "Open Settings > Apps > Muse Container > Battery > Unrestricted manually."
        }
    }

    private fun updateBatteryStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val cur = statusText.text.toString()
        statusText.text = cur + "\nBattery optimisation exempt: ${pm.isIgnoringBatteryOptimizations(packageName)}"
    }

    private fun startKeepAlive() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Keep-alive service started (prototype only)", Toast.LENGTH_SHORT).show()
    }
}
