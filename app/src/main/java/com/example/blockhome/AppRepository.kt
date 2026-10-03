package com.example.blockhome

import android.content.Context
import android.content.pm.LauncherApps

class AppRepository(private val context: Context) {

    private val launcherApps: LauncherApps =
        context.getSystemService(LauncherApps::class.java)

    /** Blocking; call from a background thread. */
    fun load(): List<AppEntry> {
        val result = ArrayList<AppEntry>()
        for (user in launcherApps.profiles) {
            try {
                for (info in launcherApps.getActivityList(null, user)) {
                    if (info.applicationInfo.packageName == context.packageName) continue
                    result.add(
                        AppEntry(
                            label = info.label?.toString().orEmpty(),
                            component = info.componentName,
                            user = user,
                            icon = info.getBadgedIcon(0)
                        )
                    )
                }
            } catch (e: Exception) {
                // A profile can be locked or unavailable (e.g. private space); skip it.
            }
        }
        result.sortWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
        return result
    }

    fun launch(entry: AppEntry): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, null, null)
        true
    } catch (e: Exception) {
        false
    }
}
