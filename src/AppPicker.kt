package local.helperblock

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.Collator

internal fun showAppPicker(
    context: Context,
    apps: Map<String, String>,
    selected: Set<String>,
    save: (Set<String>) -> Unit
) {
    val chosen = selected.toMutableSet()
    val collator = Collator.getInstance()
    val names = apps.keys.sortedWith(
        compareByDescending<String> { it in selected }
            .thenBy(collator) { apps.getValue(it) }
            .thenBy { it }
    )
    val panel = verticalPanel(context).apply { isFocusableInTouchMode = true }
    val search = EditText(context).apply {
        setSingleLine()
        hint = "搜索应用名称或包名"
    }
    val count = TextView(context)
    val list = ListView(context).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
    panel.addSpaced(search, 0)
    panel.addSpaced(count, 8)
    panel.addView(list, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        minOf(context.dp(380), context.resources.displayMetrics.heightPixels / 2)
    ).apply { topMargin = context.dp(8) })

    var visible = emptyList<String>()
    fun updateCount() {
        count.text = if (visible.isEmpty()) "没有匹配的应用 · 已选 ${chosen.size} 个"
        else "显示 ${visible.size} 个应用 · 已选 ${chosen.size} 个"
    }
    fun filter(query: String) {
        val term = query.trim()
        visible = names.filter {
            it.contains(term, ignoreCase = true) || apps.getValue(it).contains(term, ignoreCase = true)
        }
        list.clearChoices()
        list.adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_multiple_choice,
            visible.map { name ->
                val label = apps.getValue(name)
                if (label == name) name else "$label\n$name"
            }
        )
        visible.forEachIndexed { index, name -> list.setItemChecked(index, name in chosen) }
        list.setSelection(0)
        updateCount()
    }
    list.setOnItemClickListener { _, _, index, _ ->
        val name = visible[index]
        if (list.isItemChecked(index)) chosen.add(name) else chosen.remove(name)
        updateCount()
    }
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            filter(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) = Unit
    })

    filter("")
    panel.requestFocus()
    AlertDialog.Builder(context).setTitle("查找并选择应用")
        .setView(panel)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存选择") { _, _ -> save(chosen.toSet()) }
        .create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            show()
        }
}
