package local.helperblock

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal class RequestAdapter(private val context: Context) : BaseExpandableListAdapter() {
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
        val row = recycled as? GroupRow ?: GroupRow(context)
        val item = groups[group]
        row.bind(item.app, item.packets.size, item.packets.first().source.blocked, expanded)
        return row
    }

    override fun getChildView(group: Int, child: Int, last: Boolean, recycled: View?, parent: ViewGroup): View {
        val row = recycled as? PacketRow ?: PacketRow(context)
        val packet = getChild(group, child)
        row.bind(packet, clock.format(Date(packet.time)))
        return row
    }
}

private class GroupRow(context: Context) : LinearLayout(context) {
    private val palette = context.palette
    private val avatar = AppAvatar(context)
    private val title = context.text("", 15f, palette.textPrimary, medium = true)
    private val detail = context.text("", 11.5f, palette.textTertiary)
    private val policy = context.text("", 11f, palette.success, medium = true)
    private val chevron = ChevronView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rippleOver(
            context.shape(palette.surface, 18, 1, palette.border),
            if (palette.dark) 0x1AFFFFFF else 0x0D101820
        )
        elevation = context.dpf(1f)
        setPadding(context.dp(12), context.dp(12), context.dp(10), context.dp(12))
        minimumHeight = context.dp(68)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(8) }

        detail.maxLines = 1
        detail.ellipsize = TextUtils.TruncateAt.END

        addView(avatar, LinearLayout.LayoutParams(context.dp(42), context.dp(42)).apply {
            marginEnd = context.dp(12)
        })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title)
            addView(detail, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(4) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(policy, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = context.dp(8)
            topMargin = context.dp(2)
        })
        addView(chevron, LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply {
            marginStart = context.dp(4)
        })
    }

    fun bind(app: AppRule?, packetCount: Int, blocked: Boolean, expanded: Boolean) {
        val titleText = app?.title ?: "无法识别的应用"
        val names = app?.packages?.joinToString(" · ")
        avatar.bind(app?.packages?.firstOrNull().orEmpty(), titleText)
        title.text = titleText
        detail.text = when {
            names == null -> "未解析到归属应用 · $packetCount 个包"
            names == titleText -> "$packetCount 个包"
            else -> "$names · $packetCount 个包"
        }
        policy.text = if (blocked) "拦截" else "放行"
        policy.setTextColor(if (blocked) palette.danger else palette.success)
        policy.background = context.shape(if (blocked) palette.dangerMuted else palette.successMuted, 8)
        policy.setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
        chevron.expanded = expanded
    }
}

private class PacketRow(context: Context) : LinearLayout(context) {
    private val palette = context.palette
    private val protocol = context.text("", 10f, palette.accent, medium = true)
    private val endpoint = context.text("", 12.5f, palette.textPrimary, mono = true)
    private val meta = context.text("", 11f, palette.textTertiary)
    private val size = context.text("", 11.5f, palette.textSecondary, medium = true)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = context.shape(palette.surface, 14, 1, palette.border)
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = context.dp(14)
            bottomMargin = context.dp(6)
        }

        protocol.gravity = Gravity.CENTER
        protocol.setPadding(context.dp(6), context.dp(3), context.dp(6), context.dp(3))
        endpoint.maxLines = 1
        endpoint.ellipsize = TextUtils.TruncateAt.MIDDLE

        addView(protocol, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = context.dp(10) })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(endpoint, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(meta, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(3) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(size, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = context.dp(10) })
    }

    fun bind(packet: RequestPacket, time: String) {
        val source = packet.source
        protocol.text = source.protocol
        val known = source.protocol == "TCP" || source.protocol == "UDP"
        protocol.setTextColor(if (known) palette.accent else palette.textSecondary)
        protocol.background = context.shape(
            if (known) palette.accentMuted else palette.surface, 8, 1, palette.border
        )
        endpoint.text = source.endpoint
        meta.text = "$time · ${if (source.blocked) "拦截" else "放行"}"
        size.text = bytes(packet.bytes)
    }

    private fun bytes(value: Int): String = when {
        value < 1024 -> "$value B"
        value < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", value / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
}
