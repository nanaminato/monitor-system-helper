package local.helperblock

import android.content.Context

internal class MonitorSelection(private val context: Context) {
    private val preferences = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    val packages: Set<String>
        get() = preferences.getStringSet("packages", emptySet()).orEmpty().toSortedSet()

    fun availableApps(): Map<String, String> {
        val manager = context.packageManager
        val apps = sortedMapOf<String, String>()
        for (app in manager.getInstalledApplications(0)) {
            apps[app.packageName] = app.loadLabel(manager).toString()
        }
        for (name in packages) apps.putIfAbsent(name, name)
        apps.remove(context.packageName)
        return apps
    }

    fun replace(names: Set<String>) {
        preferences.edit().putStringSet("packages", names.toSet()).apply()
    }

    fun add(rawName: String): Boolean {
        val name = rawName.trim()
        if (name == context.packageName || !PACKAGE_NAME.matches(name)) return false
        replace(packages + name)
        return true
    }

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
    }
}
