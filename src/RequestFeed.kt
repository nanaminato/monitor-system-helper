package local.helperblock

import android.net.ConnectivityManager
import android.system.OsConstants
import java.net.InetAddress
import java.net.InetSocketAddress

internal data class AppRule(val uid: Int, val title: String, val packages: List<String>, val blocked: Boolean)

internal data class RequestSource(
    val app: AppRule?,
    val endpoint: String,
    val protocol: String,
    val blocked: Boolean
)

internal data class RequestPacket(val time: Long, val source: RequestSource, val bytes: Int)

internal object RequestFeed {
    private const val LIMIT = 500
    private val packets = ArrayDeque<RequestPacket>(LIMIT)
    private var revision = 0L

    @Synchronized fun clear() {
        packets.clear()
        revision++
    }

    @Synchronized fun add(packet: RequestPacket) {
        if (packets.size == LIMIT) packets.removeFirst()
        packets.addLast(packet)
        revision++
    }

    @Synchronized fun snapshot(after: Long): Pair<Long, List<RequestPacket>>? =
        if (after == revision) null else revision to packets.toList().asReversed()
}

/** Called only by the native packet worker. Rule values are fixed for the session. */
internal class RequestClassifier(
    private val connectivity: ConnectivityManager?,
    private val rules: Map<Int, AppRule>
) {
    val blockUnknown = rules.values.any { it.blocked }
    private val unknown = RequestSource(null, "未解析目标", "IP", blockUnknown)
    private val flows = arrayOfNulls<RequestSource>(1024)

    fun classify(slot: Int, protocol: Int, source: ByteArray, sourcePort: Int,
                 destination: ByteArray, destinationPort: Int): Boolean {
        val remote = InetAddress.getByAddress(destination)
        val uid = if (protocol == OsConstants.IPPROTO_TCP || protocol == OsConstants.IPPROTO_UDP) {
            try {
                connectivity?.getConnectionOwnerUid(
                    protocol, InetSocketAddress(InetAddress.getByAddress(source), sourcePort),
                    InetSocketAddress(remote, destinationPort)
                )
            } catch (_: SecurityException) {
                null
            }
        } else null
        val app = rules[uid]
        val address = remote.hostAddress
        val endpoint = if (destinationPort == 0) address
            else if (destination.size == 16) "[$address]:$destinationPort" else "$address:$destinationPort"
        val transport = when (protocol) {
            OsConstants.IPPROTO_TCP -> "TCP"
            OsConstants.IPPROTO_UDP -> "UDP"
            else -> "IP/$protocol"
        }
        val blocked = app?.blocked ?: blockUnknown
        flows[slot] = RequestSource(app, endpoint.orEmpty(), transport, blocked)
        return blocked
    }

    fun observe(slot: Int, bytes: Int) {
        RequestFeed.add(RequestPacket(System.currentTimeMillis(), flows.getOrNull(slot) ?: unknown, bytes))
    }
}
