package local.helperblock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import java.text.Collator

internal fun showAppPicker(
    context: Context,
    apps: Map<String, String>,
    selected: Set<String>,
    save: (Set<String>) -> Unit
) {
    val palette = context.palette
    val chosen = selected.toMutableSet()
    val collator = Collator.getInstance()
    val names = apps.keys.sortedWith(
        compareByDescending<String> { it in selected }
            .thenBy(collator) { apps.getValue(it) }
            .thenBy { it }
    )

    val panel = PanelDialog(context)
    val title = context.text("选择应用", 20f, palette.textPrimary, medium = true)
    val summary = context.text("", 12f, palette.textTertiary)
    val search = EditText(context).apply {
        setSingleLine()
        hint = "搜索应用名称或包名"
        textSize = 15f
        setTextColor(palette.textPrimary)
        setHintTextColor(palette.textTertiary)
        background = context.shape(palette.surfaceMuted, 14, 1, palette.border)
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
    }
    var visible = emptyList<String>()
    val list = ListView(context).apply {
        divider = ColorDrawable(Color.TRANSPARENT)
        dividerHeight = context.dp(6)
        setSelector(ColorDrawable(Color.TRANSPARENT))
        clipToPadding = false
    }

    fun updateSummary() {
        summary.text = if (visible.isEmpty()) {
            "没有匹配的应用 · 已选 ${chosen.size} 个"
        } else {
            "显示 ${visible.size} 个应用 · 已选 ${chosen.size} 个"
        }
    }

    val adapter = object : BaseAdapter() {
        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): Any = visible[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val row = recycled as? AppPickerRow ?: AppPickerRow(context)
            val name = visible[position]
            row.bind(apps.getValue(name), name, name in chosen)
            return row
        }
    }

    fun filter(query: String) {
        val term = query.trim()
        visible = names.filter {
            it.contains(term, ignoreCase = true) || apps.getValue(it).contains(term, ignoreCase = true)
        }
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    list.adapter = adapter
    list.setOnItemClickListener { _, _, position, _ ->
        val name = visible[position]
        if (!chosen.add(name)) chosen.remove(name)
        adapter.notifyDataSetChanged()
        updateSummary()
    }
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            filter(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) = Unit
    })

    panel.content.apply {
        addFull(title)
        addFull(summary, top = 6)
        addFull(search, top = 14)
        addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            minOf(context.dp(360), context.resources.displayMetrics.heightPixels / 2)
        ).apply { topMargin = context.dp(14) })

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(context.outlineButton("取消") { panel.dismiss() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, context.dp(50), 1f).apply {
                    marginEnd = context.dp(10)
                }
            })
            addView(context.filledButton("保存并抓包") {
                save(chosen.toSet())
                panel.dismiss()
            }, LinearLayout.LayoutParams(0, context.dp(50), 1f))
        }
        addFull(buttons, top = 16)
    }

    filter("")
    panel.show()
}

private class AppPickerRow(context: Context) : LinearLayout(context) {
    private val palette = context.palette
    private val marker = context.text("", 11f, palette.onAccent, medium = true).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(context.dp(22), context.dp(22))
        includeFontPadding = false
    }
    private val label = context.text("", 14.5f, palette.textPrimary, medium = true)
    private val packageName = context.text("", 11.5f, palette.textTertiary)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(12), context.dp(11), context.dp(12), context.dp(11))
        minimumHeight = context.dp(56)
        packageName.maxLines = 1
        packageName.ellipsize = TextUtils.TruncateAt.MIDDLE

        addView(marker, LinearLayout.LayoutParams(context.dp(22), context.dp(22)).apply {
            marginEnd = context.dp(12)
        })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(label)
            addView(packageName, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(3) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    fun bind(appLabel: String, appPackage: String, checked: Boolean) {
        marker.text = if (checked) "\u2713" else ""
        marker.background = if (checked) {
            context.shape(palette.accent, 7)
        } else {
            context.shape(Color.TRANSPARENT, 7, 2, palette.border)
        }
        label.text = appLabel
        packageName.text = appPackage
        packageName.visibility = if (appLabel == appPackage) View.GONE else View.VISIBLE
        background = rippleOver(
            context.shape(if (checked) palette.accentMuted else Color.TRANSPARENT, 14),
            if (palette.dark) 0x1AFFFFFF else 0x0D101820
        )
    }
}
