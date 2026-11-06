package local.helperblock

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

internal class MonitorView(
    context: Context,
    chooseApps: () -> Unit,
    addPackage: () -> Unit,
    start: () -> Unit,
    stop: () -> Unit,
    refresh: () -> Unit
) : ScrollView(context) {
    private val selection = TextView(context)
    private val status = TextView(context)

    init {
        addView(verticalPanel(context).apply {
            addView(selection)
            addButton("选择应用", chooseApps)
            addButton("按包名添加", addPackage)
            addView(status)
            addButton("开始抓包", start)
            addButton("停止抓包", stop)
            addButton("刷新抓包状态", refresh)
        })
    }

    fun showSelection(names: Set<String>) {
        selection.text = if (names.isEmpty()) "尚未选择应用" else names.joinToString("\n", "已选择\n")
    }

    fun showStatus(message: String) {
        status.text = message
    }

    fun chooseApps(apps: Map<String, String>, selected: Set<String>, save: (Set<String>) -> Unit) {
        val names = apps.keys.toList()
        val labels = names.map { "${apps.getValue(it)}\n$it" }.toTypedArray()
        val checked = BooleanArray(names.size) { names[it] in selected }
        AlertDialog.Builder(context).setTitle("选择要监控的应用")
            .setMultiChoiceItems(labels, checked) { _, index, selectedNow -> checked[index] = selectedNow }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                save(names.filterIndexed { index, _ -> checked[index] }.toSet())
            }
            .show()
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
