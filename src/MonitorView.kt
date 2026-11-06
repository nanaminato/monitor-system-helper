package local.helperblock

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

internal class MonitorView(
    context: Context,
    back: () -> Unit,
    chooseApps: () -> Unit,
    addPackage: () -> Unit,
    start: () -> Unit,
    stop: () -> Unit,
    refresh: () -> Unit,
    private val setBlocked: (String, Boolean) -> Unit,
    enableLegacy: () -> Unit,
    disableLegacy: () -> Unit
) : ScrollView(context) {
    private val palette = context.palette
    private val countChip = context.chip("未选择", palette.textTertiary, palette.surfaceMuted)
    private val rules = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val loading = context.text("正在加载应用列表…", 12f, palette.textTertiary).apply {
        visibility = View.GONE
    }
    private val stateDot = context.dot(palette.textTertiary, 10)
    private val stateTitle = context.text("", 15f, palette.textPrimary, medium = true)
    private val statusDetail = context.text("", 12.5f, palette.textTertiary)

    init {
        setBackgroundColor(palette.background)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, context.dp(28))
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        root.addView(context.screenHeader(
            eyebrow = "管理与配置",
            title = "应用与规则",
            subtitle = "选择应用、设置逐包拦截，然后开始抓包",
            onBack = back
        ), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(applicationsCard(chooseApps, addPackage), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = context.dp(16)
            rightMargin = context.dp(16)
            topMargin = -context.dp(32)
        })

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), 0, context.dp(16), 0)
        }
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        body.addFull(captureCard(start, stop, refresh), top = 14)
        body.addFull(legacyCard(enableLegacy, disableLegacy), top = 14)
        body.addFull(context.text("名单与拦截规则在下次开始抓包时生效。", 11.5f, palette.textTertiary).apply {
            gravity = Gravity.CENTER
        }, top = 20)
    }

    private fun applicationsCard(chooseApps: () -> Unit, addPackage: () -> Unit): LinearLayout =
        context.card(18).apply {
            val head = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(context.sectionLabel("已选应用"), LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
                addView(countChip)
            }
            addView(head, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addFull(rules, top = 6)
            addFull(loading, top = 8)
            addFull(context.hairline(), top = 14)

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(context.filledButton("查找并选择应用") { chooseApps() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dp(50), 1f).apply {
                        marginEnd = context.dp(10)
                    }
                })
                addView(context.outlineButton("输入包名") { addPackage() }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(50)
                    )
                })
            }
            addFull(actions, top = 16)
        }

    private fun captureCard(start: () -> Unit, stop: () -> Unit, refresh: () -> Unit): LinearLayout =
        context.card(18).apply {
            val head = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(stateDot, LinearLayout.LayoutParams(context.dp(10), context.dp(10)).apply {
                    marginEnd = context.dp(8)
                })
                addView(stateTitle, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
                addView(context.linkButton("刷新") { refresh() })
            }
            addView(head, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addFull(statusDetail, top = 8)
            addFull(context.hairline(), top = 14)

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(context.filledButton("开始抓包") { start() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dp(50), 1f).apply {
                        marginEnd = context.dp(10)
                    }
                })
                addView(context.outlineButton("停止并保存") { stop() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dp(50), 1f)
                })
            }
            addFull(actions, top = 16)
            addFull(context.text("保存位置：下载 / SystemHelperBlocker", 11.5f, palette.textTertiary), top = 12)
        }

    private fun legacyCard(enable: () -> Unit, disable: () -> Unit): LinearLayout =
        context.card(18).apply {
            addFull(context.sectionLabel("独立拦截模式"), top = 0)
            addFull(context.text(
                "只丢弃 SystemHelper 的 IPv4/IPv6 流量，不影响其他应用；与抓包模式分开运行。",
                12.5f, palette.textSecondary
            ), top = 8)

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(context.outlineAccentButton("启用拦截") { enable() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dp(48), 1f).apply {
                        marginEnd = context.dp(10)
                    }
                })
                addView(context.outlineButton("停止拦截") { disable() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dp(48), 1f)
                })
            }
            addFull(actions, top = 16)
        }

    fun showSelection(names: Set<String>, blocked: Set<String>, labels: Map<String, String>) {
        val chosen = names.size
        countChip.text = if (chosen == 0) "未选择" else "$chosen 个"
        countChip.setTextColor(if (chosen == 0) palette.textTertiary else palette.accent)
        countChip.background = context.shape(
            if (chosen == 0) palette.surfaceMuted else palette.accentMuted, 8
        )

        rules.removeAllViews()
        if (names.isEmpty()) {
            rules.addView(context.text(
                "还没有选择应用。点击下方按钮按名称或包名搜索。", 12.5f, palette.textTertiary
            ), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(8) })
            return
        }
        names.forEachIndexed { index, name ->
            if (index > 0) rules.addView(context.hairline(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1)
            ))
            val label = labels[name] ?: name
            rules.addView(ruleRow(name, label, name in blocked))
        }
    }

    private fun ruleRow(name: String, label: String, checked: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(56)
            setPadding(0, context.dp(6), 0, context.dp(6))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(context.text(if (label == name) name else label, 14.5f, palette.textPrimary, medium = true))
            if (label != name) {
                addView(context.text(name, 11.5f, palette.textTertiary).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = context.dp(3) })
            }
        }
        val toggle = Switch(context).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(
                if (palette.dark) 0xFFE9EEF6.toInt() else 0xFFFFFFFF.toInt()
            )
            trackTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(palette.accent, palette.trackOff)
            )
            contentDescription = "拦截 $name"
            setOnCheckedChangeListener { _, value -> setBlocked(name, value) }
        }
        row.addView(column, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        row.addView(toggle)
        return row
    }

    fun showCatalogLoading(value: Boolean) {
        loading.visibility = if (value) View.VISIBLE else View.GONE
    }

    fun showStatus(message: String) {
        if (statusDetail.text == message) return
        statusDetail.text = message
        val state = captureState(message)
        stateTitle.text = state.title
        stateDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(state.color(palette))
        }
    }

    fun chooseApps(apps: Map<String, String>, selected: Set<String>, save: (Set<String>) -> Unit) {
        showAppPicker(context, apps, selected, save)
    }

    fun addPackage(accept: (String) -> Boolean) {
        val panel = PanelDialog(context, keyboard = true)
        val input = EditText(context).apply {
            setSingleLine()
            hint = "例如 com.mobiletools.systemhelper"
            textSize = 15f
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textTertiary)
            background = context.shape(palette.surfaceMuted, 14, 1, palette.border)
            setPadding(context.dp(14), context.dp(13), context.dp(14), context.dp(13))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val error = context.text("", 12f, palette.danger).apply { visibility = View.GONE }

        panel.content.apply {
            addFull(context.text("输入应用包名", 19f, palette.textPrimary, medium = true))
            addFull(context.text("用于添加未出现在应用列表中的应用。", 13f, palette.textSecondary), top = 8)
            addFull(input, top = 16)
            addFull(error, top = 8)

            val buttons = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(context.outlineButton("取消") { panel.dismiss() }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dp(50), 1f).apply {
                        marginEnd = context.dp(10)
                    }
                })
                addView(context.filledButton("添加") {
                    if (accept(input.text.toString())) panel.dismiss()
                    else {
                        error.text = "请输入已安装其他应用的包名"
                        error.visibility = View.VISIBLE
                    }
                }, LinearLayout.LayoutParams(0, context.dp(50), 1f))
            }
            addFull(buttons, top = 20)
        }
        panel.show()
        input.requestFocus()
        input.post {
            panel.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun confirmStart(start: () -> Unit) {
        context.confirmDialog(
            title = "开始记录通信",
            message = "将保存所选应用的原始通信包，文件可能含有明文敏感数据。" +
                "抓包 VPN 可能替换现有 VPN，包括本应用的独立拦截模式。",
            confirmLabel = "继续",
            onConfirm = start
        )
    }
}
