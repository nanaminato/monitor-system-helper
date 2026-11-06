package local.helperblock

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import java.io.IOException

class CaptureService : VpnService() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private lateinit var classifier: RequestClassifier
    private var pendingSession: Intent? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        if (busy) {
            pendingSession = intent
            status = "正在保存当前抓包并应用新规则。"
            running = false
        } else {
            startSession(intent)
        }
        return START_NOT_STICKY
    }

    private fun startSession(intent: Intent) {
        startForeground(2, captureNotification(this))
        try {
            val packages = intent.getStringArrayListExtra(PACKAGES).orEmpty()
            require(packages.isNotEmpty() && packageName !in packages) {
                "请选择至少一个其他应用。"
            }
            System.loadLibrary("capture")
            val blocked = intent.getStringArrayListExtra(BLOCKED_PACKAGES).orEmpty().toSet()
            val apps = packages.map { name ->
                try {
                    packageManager.getApplicationInfo(name, 0)
                } catch (error: PackageManager.NameNotFoundException) {
                    throw IllegalArgumentException("应用未安装或不可用：$name", error)
                }
            }
            val rules = apps.groupBy { it.uid }.mapValues { (uid, entries) ->
                val names = entries.map { it.packageName }
                val title = entries.joinToString(" / ") { it.loadLabel(packageManager).toString() }
                AppRule(uid, title, names, names.any { it in blocked })
            }
            classifier = RequestClassifier(getSystemService(ConnectivityManager::class.java), rules)
            val tunnel = establishTunnel(packages)
            stopService(Intent(this, BlockService::class.java))

            busy = true
            running = true
            status = "正在初始化抓包。"
            RequestFeed.clear()
            Thread({ record(tunnel) }, "packet-capture").start()
        } catch (error: IOException) {
            failStart(error)
        } catch (error: RuntimeException) {
            failStart(error)
        } catch (error: UnsatisfiedLinkError) {
            failStart(error)
        }
    }

    private fun establishTunnel(packages: List<String>): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("SystemHelper 通信监控")
            .setMtu(1500)
            .addAddress("192.0.2.1", 32)
            .addAddress("2001:db8::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
        for (name in packages) {
            try {
                builder.addAllowedApplication(name)
            } catch (error: PackageManager.NameNotFoundException) {
                throw IllegalArgumentException("应用未安装或不可用：$name", error)
            }
        }
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        val link = connectivity?.getLinkProperties(network)
        for (server in link?.dnsServers.orEmpty()) {
            if (!server.isLinkLocalAddress) builder.addDnsServer(server)
        }
        return builder.establish() ?: throw IOException("VPN 授权不可用。")
    }

    private fun failStart(error: Throwable) {
        status = "无法开始抓包：${error.message}"
        Toast.makeText(this, status, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun record(tunnel: ParcelFileDescriptor) {
        val result = try {
            val path = tunnel.use { input ->
                PcapStore(contentResolver).write { output ->
                    main.post {
                        if (running) status = "正在后台抓包。停止后可在下载目录查看 PCAP。"
                    }
                    if (capture(input.fd, output, classifier.blockUnknown) != 0) {
                        throw IOException("隧道或 PCAP 写入出错。")
                    }
                }
            }
            "已保存\n$path"
        } catch (error: IOException) {
            "抓包结束：${error.message}"
        } catch (error: RuntimeException) {
            "抓包结束：${error.message}"
        } catch (error: UnsatisfiedLinkError) {
            "抓包结束：${error.message}"
        }
        main.post {
            running = false
            busy = false
            status = result
            val next = pendingSession
            pendingSession = null
            if (next == null) stopSelf() else startSession(next)
        }
    }

    // Called by capture.c on the packet worker thread.
    private fun shouldCapture(): Boolean = running

    private fun classifyFlow(slot: Int, protocol: Int, source: ByteArray, sourcePort: Int,
                             destination: ByteArray, destinationPort: Int): Boolean =
        classifier.classify(slot, protocol, source, sourcePort, destination, destinationPort)

    private fun observePacket(slot: Int, bytes: Int, payload: ByteArray?, response: Boolean) =
        classifier.observe(slot, bytes, payload, response)

    private external fun capture(tunnel: Int, output: Int, blockUnknown: Boolean): Int

    private fun stopCapture() {
        pendingSession = null
        if (busy) status = "正在停止抓包并保存文件。" else stopSelf()
        running = false
    }

    override fun onRevoke() {
        running = false
        main.post(::stopCapture)
    }

    override fun onDestroy() {
        pendingSession = null
        running = false
        super.onDestroy()
    }

    companion object {
        internal const val STOP = "local.helperblock.STOP_CAPTURE"
        internal const val PACKAGES = "packages"
        internal const val BLOCKED_PACKAGES = "blocked_packages"
        internal var busy = false
            private set
        internal var status = "当前没有运行抓包。"
            private set
    }
}
