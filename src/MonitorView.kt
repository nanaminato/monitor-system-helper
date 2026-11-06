package local.helperblock

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

internal class MonitorView(
    context: Context,
    chooseApps: () -> Unit,
    addPackage: () -> Unit,
    start: () -> Unit,
    stop: () -> Unit,
    refresh: () -> Unit,
    private val setBlocked: (String, Boolean) -> Unit,
    enableLegacy: () -> Unit,
    disableLegacy: () -> Unit
) : ScrollView(context) {
    private val selection = TextView(context)
    private val status = TextView(context)
    private val rules = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val loading = TextView(context).apply { text = "正在加载应用列表…"; visibility = GONE }

    init {
        addView(verticalPanel(context).apply {
            addHeading("监控应用", 26f)
            addDescription("每个应用可单独设置拦截。名单和规则在下次开始抓包时生效。")
            addDescription("启用任一拦截规则时，也会拦截无法确认来源的包。")
            addSpaced(selection, 12)
            addButton("查找并选择应用", chooseApps)
            addSpaced(loading, 4)
            addButton("手动输入包名", addPackage, secondary = true)
            addSpaced(rules, 8)

            addHeading("抓包状态")
            addSpaced(status, 8)
            addButton("开始抓包", start)
            addButton("停止并保存", stop, secondary = true)
            addButton("刷新状态", refresh, secondary = true)
            addDescription("保存位置：下载 / SystemHelperBlocker")

            addHeading("独立 SystemHelper 拦截")
            addDescription("只拦截 SystemHelper。此模式与抓包模式分开运行。")
            addButton("启用独立拦截", enableLegacy, secondary = true)
            addButton("停止独立拦截", disableLegacy, secondary = true)
        })
    }

    fun showSelection(names: Set<String>, blocked: Set<String>, labels: Map<String, String>) {
        selection.text = if (names.isEmpty()) "尚未选择应用"
        else "已选 ${names.size} 个应用"
        rules.removeAllViews()
        for (name in names) {
            rules.addSpaced(Switch(context).apply {
                val label = labels[name]
                text = if (label == null || label == name) "拦截 $name" else "拦截 $label\n$name"
                minHeight = context.dp(48)
                isChecked = name in blocked
                setOnCheckedChangeListener { _, checked -> setBlocked(name, checked) }
            }, 6)
        }
    }

    fun showCatalogLoading(value: Boolean) {
        loading.visibility = if (value) VISIBLE else GONE
    }

    fun showStatus(message: String) {
        status.text = message
    }

    fun chooseApps(apps: Map<String, String>, selected: Set<String>, save: (Set<String>) -> Unit) {
        showAppPicker(context, apps, selected, save)
    }

    fun addPackage(accept: (String) -> Boolean) {
        val input = EditText(context).apply {
            setSingleLine()
            hint = "例如 com.mobiletools.systemhelper"
        }
        val dialog = AlertDialog.Builder(context).setTitle("添加应用包名")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (accept(input.text.toString())) dialog.dismiss()
            else input.error = "请输入要监控的其他应用的包名"
        }
    }

    fun confirmStart(start: () -> Unit) {
        AlertDialog.Builder(context).setTitle("开始记录通信")
            .setMessage("将保存所选应用的原始通信包。文件可能含有明文敏感数据。抓包 VPN 可能替换现有 VPN，包括本应用的拦截模式。")
            .setNegativeButton("取消", null)
            .setPositiveButton("继续") { _, _ -> start() }
            .show()
    }
}
