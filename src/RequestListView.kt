package local.helperblock

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

internal class MainView(context: Context, manage: () -> Unit) : LinearLayout(context) {
    private val status = TextView(context)
    private val empty = TextView(context).apply {
        text = "暂无请求包。请在管理页选择应用并开始抓包。"
    }
    private val requests = ExpandableListView(context)
    private val adapter = RequestAdapter(context)

    init {
        orientation = VERTICAL
        addView(verticalPanel(context).apply {
            addHeading("捕获的请求包", 26f)
            addSpaced(status, 8)
            addDescription("按应用分组 · 显示最近 500 个出站包")
            addButton("管理应用与拦截规则", manage)
            addSpaced(empty, 12)
        })
        requests.setAdapter(adapter)
        addView(requests, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun showStatus(message: String) {
        if (status.text != message) status.text = message
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
    }
}

private class RequestAdapter(private val context: Context) : BaseExpandableListAdapter() {
    private data class Group(val app: AppRule?, val packets: List<RequestPacket>)
    private val clock = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    private var groups = emptyList<Group>()

    fun update(packets: List<RequestPacket>) {
        groups = packets.groupBy { it.source.app }
            .map { (app, rows) -> Group(app, rows) }
            .sortedWith(compareBy<Group> { it.app == null }.thenBy { it.app?.packages?.firstOrNull() })
        notifyDataSetChanged()
    }

    override fun getGroupCount(): Int = groups.size
    override fun getChildrenCount(group: Int): Int = groups[group].packets.size
    override fun getGroup(group: Int): Any = groups[group]
    override fun getChild(group: Int, child: Int): RequestPacket = groups[group].packets[child]
    override fun getGroupId(group: Int): Long = groups[group].app?.uid?.toLong() ?: -1L
    override fun getChildId(group: Int, child: Int): Long = child.toLong()
    override fun hasStableIds(): Boolean = false
    override fun isChildSelectable(group: Int, child: Int): Boolean = false

    override fun getGroupView(group: Int, expanded: Boolean, recycled: View?, parent: ViewGroup): View {
        val item = groups[group]
        return row(recycled, true).apply {
            val title = item.app?.title ?: "无法识别的应用"
            val names = item.app?.packages?.joinToString(" / ")
            val policy = if (item.packets.first().source.blocked) "拦截" else "放行"
            text = if (names == null || title == names) "$title\n$policy · ${item.packets.size} 个包"
                else "$title\n$names\n$policy · ${item.packets.size} 个包"
        }
    }

    override fun getChildView(group: Int, child: Int, last: Boolean, recycled: View?, parent: ViewGroup): View {
        val packet = getChild(group, child)
        return row(recycled, false).apply {
            val source = packet.source
            text = "${source.protocol}  ${source.endpoint}\n${clock.format(Date(packet.time))} · ${packet.bytes} B"
        }
    }

    private fun row(recycled: View?, heading: Boolean): TextView =
        (recycled as? TextView ?: TextView(context)).apply {
            setPadding(context.dp(if (heading) 42 else 54), context.dp(10), context.dp(16), context.dp(10))
            minHeight = context.dp(48)
            textSize = if (heading) 15f else 14f
            setTypeface(null, if (heading) Typeface.BOLD else Typeface.NORMAL)
        }
}
