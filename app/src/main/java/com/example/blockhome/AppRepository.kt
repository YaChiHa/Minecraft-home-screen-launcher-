package com.example.blockhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.Bundle

class AppRepository(private val context: Context) {

    private val launcherApps: LauncherApps =
        context.getSystemService(LauncherApps::class.java)

    private val packageManager: PackageManager =
        context.packageManager

    /**
     * Loads user-launchable activities.
     *
     * LauncherApps is preferred because it understands Android launcher
     * profiles. PackageManager provides a fallback for normal launchable
     * apps that a device may not return consistently through LauncherApps.
     *
     * Blocking; call from a background thread.
     */
    fun load(): List<AppEntry> {
        val result = LinkedHashMap<AppKey, AppEntry>()

        loadFromLauncherApps(result)
        loadFromPackageManager(result)

        return result.values.sortedWith { a, b ->
            a.label.compareTo(b.label, ignoreCase = true)
        }
    }

    private fun loadFromLauncherApps(
        result: MutableMap<AppKey, AppEntry>
    ) {
        for (user in launcherApps.profiles) {
            try {
                for (info in launcherApps.getActivityList(null, user)) {
                    if (info.applicationInfo.packageName == context.packageName) {
                        continue
                    }

                    val entry = AppEntry(
                        label = info.label?.toString().orEmpty(),
                        component = info.componentName,
                        user = user,
                        icon = info.getBadgedIcon(0)
                    )

                    result[AppKey(entry.component, entry.user)] = entry
                }
            } catch (_: Exception) {
                // A profile can be locked or temporarily unavailable.
            }
        }
    }

    private fun loadFromPackageManager(
        result: MutableMap<AppKey, AppEntry>
    ) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        try {
            val activities = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL
            )

            val user = Process.myUserHandle()

            for (info in activities) {
                val component = ComponentName(
                    info.activityInfo.packageName,
                    info.activityInfo.name
                )

                if (component.packageName == context.packageName) {
                    continue
                }

                val key = AppKey(component, user)

                // Prefer the LauncherApps version when it already exists.
                if (result.containsKey(key)) {
                    continue
                }

                result[key] = AppEntry(
                    label = info.loadLabel(packageManager)
                        ?.toString()
                        .orEmpty(),
                    component = component,
                    user = user,
                    icon = info.loadIcon(packageManager)
                )
            }
        } catch (_: Exception) {
            // Discovery failure should never crash the launcher.
        }
    }

    fun launch(entry: AppEntry, options: Bundle? = null): Boolean = try {
        launcherApps.startMainActivity(
            entry.component,
            entry.user,
            null,
            options
        )
        true
    } catch (_: Exception) {
        false
    }

    private data class AppKey(
        val component: ComponentName,
        val user: android.os.UserHandle
    )
}
