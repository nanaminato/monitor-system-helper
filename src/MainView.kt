package local.helperblock

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ExpandableListView
import android.widget.LinearLayout

internal class MainView(context: Context, manage: () -> Unit) : LinearLayout(context) {
    private val palette = context.palette
    private val stateDot = context.dot(palette.textTertiary, 10)
    private val stateTitle = context.text("", 15f, palette.textPrimary, medium = true)
    private val stateDetail = context.text("", 12.5f, palette.textTertiary)
    private val metricHost = LinearLayout(context).apply { orientation = VERTICAL }
    private val empty = emptyCard()
    private val requests = ExpandableListView(context)
    private val adapter = RequestAdapter(context)
    private var lastMetrics: Triple<Int, Int, Int>? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        clipChildren = false
        clipToPadding = false

        addView(context.screenHeader(
            eyebrow = "SYSTEMHELPER BLOCKER",
            title = "通信监控",
            subtitle = "按应用记录出站请求 · 可查看明文 HTTP"
        ), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(statusCard(manage), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(16)
            rightMargin = context.dp(16)
            topMargin = -context.dp(32)
        })

        val section = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(4), context.dp(22), context.dp(4), context.dp(10))
            addView(context.sectionLabel("请求包"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(context.text("按应用分组", 12f, palette.textTertiary))
        }
        addView(section, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(16)
            rightMargin = context.dp(16)
        })

        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(16)
            rightMargin = context.dp(16)
        })

        requests.apply {
            setAdapter(this@MainView.adapter)
            setOnChildClickListener { _, _, group, child, _ ->
                showHttpPreview(context, this@MainView.adapter.getChild(group, child))
            }
            setGroupIndicator(null)
            divider = null
            dividerHeight = 0
            setChildDivider(ColorDrawable(0x00000000))
            setSelector(ColorDrawable(0x00000000))
            setPadding(context.dp(16), 0, context.dp(16), context.dp(20))
            clipToPadding = false
        }
        addView(requests, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun showStatus(message: String) {
        if (stateDetail.text == message) return
        stateDetail.text = message
        val state = captureState(message)
        stateTitle.text = state.title
        stateDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(state.color(palette))
        }
    }

    fun showPackets(packets: List<RequestPacket>) {
        val oldGroups = (0 until adapter.groupCount).associate {
            adapter.getGroupId(it) to requests.isGroupExpanded(it)
        }
        adapter.update(packets)
        for (index in 0 until adapter.groupCount) {
            val expand = oldGroups[adapter.getGroupId(index)] ?: (index == 0)
            if (expand && !requests.isGroupExpanded(index)) requests.expandGroup(index)
            if (!expand && requests.isGroupExpanded(index)) requests.collapseGroup(index)
        }
        empty.visibility = if (packets.isEmpty()) View.VISIBLE else View.GONE
        requests.visibility = if (packets.isEmpty()) View.GONE else View.VISIBLE

        val apps = packets.mapNotNull { it.source.app?.uid }.distinct().size
        val blocked = packets.count { it.source.blocked }
        val metrics = Triple(packets.size, apps, blocked)
        if (metrics != lastMetrics) {
            lastMetrics = metrics
            metricHost.removeAllViews()
            metricHost.addView(context.metricRow(
                packets.size.toString(), apps.toString(), blocked.toString()
            ))
        }
    }

    private fun statusCard(manage: () -> Unit): LinearLayout {
        val card = context.card(18)
        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(stateDot, LayoutParams(context.dp(10), context.dp(10)).apply {
                rightMargin = context.dp(8)
            })
            addView(stateTitle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(head, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addFull(stateDetail, top = 6)
        card.addFull(metricHost, top = 14)
        metricHost.addView(context.metricRow("0", "0", "0"))
        card.addFull(context.filledButton("管理应用与拦截规则") { manage() }, top = 18)
        return card
    }

    private fun emptyCard(): LinearLayout = context.card(22).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        val ring = BarsGlyph(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.surfaceMuted)
                setStroke(context.dp(1), palette.border)
            }
            layoutParams = LayoutParams(context.dp(52), context.dp(52)).apply { bottomMargin = context.dp(14) }
        }
        addView(ring)
        addView(context.text("还没有捕获到请求", 15f, palette.textPrimary, medium = true), LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ))
        addView(context.text("在管理页选择应用并开始抓包后，这里会按应用显示实时流量。", 12.5f, palette.textTertiary).apply {
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
    }
}
