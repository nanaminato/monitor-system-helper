package local.helperblock

import android.content.Context

internal class MonitorSelection(private val context: Context) {
    private val preferences = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    val packages: Set<String>
        get() = preferences.getStringSet("packages", emptySet()).orEmpty().toSortedSet()

    val blocked: Set<String>
        get() = preferences.getStringSet("blocked_packages", emptySet()).orEmpty()

    fun setBlocked(name: String, enabled: Boolean) {
        val updated = if (enabled) blocked + name else blocked - name
        preferences.edit().putStringSet("blocked_packages", updated).apply()
    }

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
        preferences.edit().putStringSet("packages", names.toSet())
            .putStringSet("blocked_packages", blocked intersect names).apply()
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
