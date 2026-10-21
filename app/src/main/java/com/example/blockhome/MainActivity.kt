package com.example.blockhome

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blockhome.ui.BlockyBackgroundView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var launcherApps: LauncherApps
    private lateinit var homeAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var drawer: View
    private lateinit var search: EditText
    private lateinit var setDefaultButton: Button
    private lateinit var backgroundView: BlockyBackgroundView
    private lateinit var preferences: SharedPreferences
    private lateinit var soundManager: SoundManager
private lateinit var soundToggleButton: Button
    private lateinit var soundVolumeSeekBar: SeekBar
    private lateinit var soundVolumeLabel: TextView

    private var allApps: List<AppEntry> = emptyList()
    private var loadJob: Job? = null

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateDefaultButton()
        }

    private val backgroundPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not support persistable permissions.
                // The URI can still work for the current session.
            }

            preferences.edit()
                .putString(KEY_BACKGROUND_URI, uri.toString())
                .apply()

            backgroundView.setCustomBackground(uri)
            Toast.makeText(
                this,
                R.string.background_saved,
                Toast.LENGTH_SHORT
            ).show()
        }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleReload()

        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleReload()

        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleReload()

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = scheduleReload()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = scheduleReload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

soundManager = SoundManager(applicationContext)

val soundEnabled = preferences.getBoolean(KEY_SOUND_ENABLED, true)
soundManager.setEnabled(soundEnabled)
soundManager.setVolume(preferences.getInt(KEY_SOUND_VOLUME, DEFAULT_SOUND_VOLUME))

repo = AppRepository(applicationContext)
        launcherApps = getSystemService(LauncherApps::class.java)

        backgroundView = findViewById(R.id.background_view)
        applySavedTheme()
        loadSavedBackground()

        val content = findViewById<View>(R.id.content_layer)

        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )

            v.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )

            insets
        }

        drawer = findViewById(R.id.drawer)
        search = findViewById(R.id.search)
        setDefaultButton = findViewById(R.id.set_default_button)

        homeAdapter = AppAdapter { launchApp(it) }
        drawerAdapter = AppAdapter { launchApp(it) }

        findViewById<RecyclerView>(R.id.home_grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, COLUMNS)
            adapter = homeAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
        }

        findViewById<RecyclerView>(R.id.drawer_grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, COLUMNS)
            adapter = drawerAdapter
            setHasFixedSize(true)
        }

        findViewById<Button>(R.id.drawer_button).setOnClickListener {
            soundManager.playClick()
            openDrawer()
        }
        findViewById<Button>(R.id.close_drawer_button).setOnClickListener {
    soundManager.playClick()
    closeDrawer()
        }

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            soundManager.playClick()
            openSettings()
        }

        findViewById<Button>(R.id.choose_theme_button).setOnClickListener {
            soundManager.playClick()
            showThemePicker()
        }

        findViewById<Button>(R.id.choose_background_button).setOnClickListener {
            soundManager.playClick()
            chooseBackground()
        }

        findViewById<Button>(R.id.reset_background_button).setOnClickListener {
            soundManager.playClick()
            resetBackground()
        }
        soundToggleButton = findViewById(R.id.sound_toggle_button)
updateSoundButton()

soundToggleButton.setOnClickListener {
    soundManager.playClick()
    val enabled = !soundManager.isEnabled()

    soundManager.setEnabled(enabled)

    preferences.edit()
        .putBoolean(KEY_SOUND_ENABLED, enabled)
        .apply()

    updateSoundButton()
}

soundVolumeSeekBar = findViewById(R.id.sound_volume_seekbar)
soundVolumeLabel = findViewById(R.id.sound_volume_label)
soundVolumeSeekBar.max = 100
soundVolumeSeekBar.progress = soundManager.getVolume()
updateSoundVolumeLabel(soundManager.getVolume())

soundVolumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        soundManager.setVolume(progress)
        updateSoundVolumeLabel(progress)
        if (fromUser) {
            preferences.edit().putInt(KEY_SOUND_VOLUME, progress).apply()
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
})
        findViewById<Button>(R.id.back_settings_button).setOnClickListener {
    soundManager.playClick()
    closeSettings()
        }

        setDefaultButton.setOnClickListener {
            soundManager.playClick()
            requestHomeRole()
        }

        search.doOnTextChanged { text, _, _, _ ->
            applyFilter(text?.toString().orEmpty())
        }

        launcherApps.registerCallback(
            packageCallback,
            Handler(Looper.getMainLooper())
        )

        reload(0L)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        drawer.visibility == View.VISIBLE -> closeDrawer()
                        findViewById<View>(R.id.settings_panel).visibility == View.VISIBLE ->
                            closeSettings()
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        if (::launcherApps.isInitialized) {
            launcherApps.unregisterCallback(packageCallback)
        }

        if (::backgroundView.isInitialized) {
            backgroundView.release()
        }

        if (::soundManager.isInitialized) {
            soundManager.release()
        }

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultButton()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (
            intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            closeDrawer()
            closeSettings()
        }
    }

    private fun loadSavedBackground() {
        val savedUri = preferences.getString(KEY_BACKGROUND_URI, null)

        if (savedUri.isNullOrEmpty()) {
            backgroundView.setDefaultBackground()
            return
        }

        try {
            backgroundView.setCustomBackground(Uri.parse(savedUri))
        } catch (_: Exception) {
            preferences.edit()
                .remove(KEY_BACKGROUND_URI)
                .apply()

            backgroundView.setDefaultBackground()
        }
    }

    private fun showThemePicker() {
        val themes = arrayOf("Grass & Dirt", "Stone", "Diamond", "Amethyst", "Glass", "Nether")
        val current = preferences.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        val selected = themes.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_theme)
            .setSingleChoiceItems(themes, selected) { dialog, which ->
                val chosen = themes[which]
                preferences.edit().putString(KEY_THEME, chosen).apply()
                applyTheme(chosen)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applySavedTheme() {
        applyTheme(preferences.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME)
    }

    private fun applyTheme(theme: String) {
        val colors = when (theme) {
            "Stone" -> intArrayOf(Color.rgb(38, 38, 38), Color.rgb(165, 165, 165), Color.rgb(95, 95, 95), Color.rgb(50, 50, 50))
            "Diamond" -> intArrayOf(Color.rgb(0, 55, 70), Color.rgb(110, 245, 255), Color.rgb(0, 160, 190), Color.rgb(0, 90, 110))
            "Amethyst" -> intArrayOf(Color.rgb(45, 20, 65), Color.rgb(220, 170, 255), Color.rgb(135, 70, 190), Color.rgb(75, 35, 110))
            "Glass" -> intArrayOf(0x66303030, 0x99D8F5FF.toInt(), 0x6675C8E8, 0x667A9DB0)
            "Nether" -> intArrayOf(Color.rgb(65, 12, 12), Color.rgb(255, 120, 90), Color.rgb(170, 35, 25), Color.rgb(95, 15, 20))
            else -> intArrayOf(Color.rgb(30, 58, 18), Color.rgb(138, 214, 103), Color.rgb(78, 155, 47), Color.rgb(75, 75, 75))
        }
        val buttonIds = intArrayOf(R.id.settings_button, R.id.set_default_button, R.id.drawer_button, R.id.choose_theme_button, R.id.choose_background_button, R.id.reset_background_button, R.id.sound_toggle_button, R.id.back_settings_button, R.id.close_drawer_button)
        buttonIds.forEach { id -> findViewById<View?>(id)?.background = blockLayer(colors[0], colors[1], colors[2]) }
        findViewById<View>(R.id.settings_panel).background = blockLayer(colors[0], colors[1], colors[3])
        findViewById<View>(R.id.drawer).background = blockLayer(colors[0], colors[1], colors[3])
        findViewById<View>(R.id.home_panel).background = blockLayer(colors[0], colors[1], colors[3])
        findViewById<View>(R.id.search).setBackgroundColor(colors[3])
    }

    private fun blockLayer(shadow: Int, edge: Int, face: Int): android.graphics.drawable.Drawable {
        val layers = android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(shadow) },
            GradientDrawable().apply { setColor(edge) },
            GradientDrawable().apply { setColor(face) }
        ))
        layers.setLayerInset(1, 3, 3, 3, 3)
        layers.setLayerInset(2, 6, 6, 3, 6)
        return layers
    }

    private fun chooseBackground() {
        backgroundPicker.launch(
            arrayOf(
                "image/jpeg",
                "image/png",
                "image/webp"
            )
        )
    }

    private fun resetBackground() {
        preferences.edit()
            .remove(KEY_BACKGROUND_URI)
            .apply()

        backgroundView.setDefaultBackground()

        Toast.makeText(
            this,
            R.string.background_reset,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openSettings() {
        findViewById<View>(R.id.settings_panel).visibility = View.VISIBLE
    }

    private fun closeSettings() {
        findViewById<View>(R.id.settings_panel).visibility = View.GONE
    }
    private fun updateSoundButton() {
        soundToggleButton.text =
            if (soundManager.isEnabled()) {
                getString(R.string.sound_on)
            } else {
                getString(R.string.sound_off)
            }
    }

    private fun updateSoundVolumeLabel(volume: Int) {
        soundVolumeLabel.text = getString(R.string.sound_volume_format, volume)
    }

    private fun scheduleReload() {
        reload(RELOAD_DEBOUNCE_MS)
    }

    private fun reload(delayMs: Long) {
        loadJob?.cancel()

        loadJob = lifecycleScope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }

            val apps = withContext(Dispatchers.Default) {
                repo.load()
            }

            allApps = apps

            homeAdapter.submitList(
                getHomeApps(apps)
            )

            applyFilter(
                search.text?.toString().orEmpty()
            )
        }
    }

    private fun getHomeApps(apps: List<AppEntry>): List<AppEntry> {
        val usage = preferences.all
            .filterKeys { it.startsWith(KEY_APP_USAGE_PREFIX) }
            .mapNotNull { (key, value) ->
                val parts = (value as? String)?.split('|') ?: return@mapNotNull null
                if (parts.size != 2) return@mapNotNull null
                val count = parts[0].toIntOrNull() ?: return@mapNotNull null
                val lastUsed = parts[1].toLongOrNull() ?: return@mapNotNull null
                key.removePrefix(KEY_APP_USAGE_PREFIX) to (count to lastUsed)
            }.toMap()

        return apps.sortedWith(
            compareByDescending<AppEntry> { usage[it.component.flattenToString()]?.first ?: 0 }
                .thenByDescending { usage[it.component.flattenToString()]?.second ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        ).take(HOME_SLOTS)
    }

    private fun recordAppLaunch(entry: AppEntry) {
        val key = KEY_APP_USAGE_PREFIX + entry.component.flattenToString()
        val previous = preferences.getString(key, null)?.split('|')
        val count = previous?.getOrNull(0)?.toIntOrNull() ?: 0
        preferences.edit()
            .putString(key, (count + 1).toString() + "|" + System.currentTimeMillis())
            .apply()
    }

    private fun applyFilter(query: String) {
        val q = query.trim()

        drawerAdapter.submitList(
            if (q.isEmpty()) {
                allApps
            } else {
                allApps.filter {
                    it.label.contains(
                        q,
                        ignoreCase = true
                    )
                }
            }
        )
    }

    private fun launchApp(entry: AppEntry) {
        soundManager.playClick()
        if (repo.launch(entry)) {
            recordAppLaunch(entry)
            homeAdapter.submitList(getHomeApps(allApps))
            closeDrawer()
        } else {
            Toast.makeText(
                this,
                R.string.cannot_launch,
                Toast.LENGTH_SHORT
            ).show()

            reload(0L)
        }
    }

    private fun openDrawer() {
        drawer.visibility = View.VISIBLE
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) {
            return
        }

        search.setText("")

        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(
                search.windowToken,
                0
            )

        drawer.visibility = View.GONE
    }

    private fun updateDefaultButton() {
        val rm = getSystemService(RoleManager::class.java)

        val held =
            rm != null &&
                rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                rm.isRoleHeld(RoleManager.ROLE_HOME)

        setDefaultButton.visibility =
            if (held) View.GONE else View.VISIBLE
    }

    private fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)

        try {
            if (
                rm != null &&
                rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                roleLauncher.launch(
                    rm.createRequestRoleIntent(
                        RoleManager.ROLE_HOME
                    )
                )
            } else {
                startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS)
                )
            }
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    R.string.cannot_launch,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
private companion object {
    const val COLUMNS = 4
    const val HOME_SLOTS = 8
    const val RELOAD_DEBOUNCE_MS = 300L

    const val PREFS_NAME = "blockhome_preferences"
    const val KEY_BACKGROUND_URI = "background_uri"
    const val KEY_THEME = "launcher_theme"
    const val DEFAULT_THEME = "Grass & Dirt"
    const val KEY_SOUND_ENABLED = "sound_enabled"
    const val KEY_SOUND_VOLUME = "sound_volume"
    const val KEY_APP_USAGE_PREFIX = "app_usage_"
    const val DEFAULT_SOUND_VOLUME = 70
}
}
