package com.example.blockhome

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
import android.widget.Button
import android.widget.EditText
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

        repo = AppRepository(applicationContext)
        launcherApps = getSystemService(LauncherApps::class.java)

        backgroundView = findViewById(R.id.background_view)
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
            openDrawer()
        }

        findViewById<Button>(R.id.settings_button).setOnClickListener {
            openSettings()
        }

        findViewById<Button>(R.id.choose_background_button).setOnClickListener {
            chooseBackground()
        }

        findViewById<Button>(R.id.reset_background_button).setOnClickListener {
            resetBackground()
        }

        setDefaultButton.setOnClickListener {
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
                apps.take(HOME_SLOTS)
            )

            applyFilter(
                search.text?.toString().orEmpty()
            )
        }
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
        if (repo.launch(entry)) {
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
    }
}
