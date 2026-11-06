package local.helperblock

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

class MonitorActivity : Activity() {
    private lateinit var selection: MonitorSelection
    private lateinit var screen: MonitorView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "应用通信监控"
        selection = MonitorSelection(this)
        screen = MonitorView(
            this,
            chooseApps = ::chooseApps,
            addPackage = ::addPackage,
            start = ::checkStart,
            stop = ::stopCapture,
            refresh = ::refreshStatus
        )
        setContentView(screen)
        screen.showSelection(selection.packages)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun chooseApps() {
        screen.chooseApps(selection.availableApps(), selection.packages) { names ->
            selection.replace(names)
            screen.showSelection(selection.packages)
        }
    }

    private fun addPackage() {
        screen.addPackage { rawName ->
            val accepted = selection.add(rawName)
            if (accepted) screen.showSelection(selection.packages)
            accepted
        }
    }

    private fun refreshStatus() {
        screen.showStatus(CaptureService.status)
    }

    private fun checkStart() {
        when {
            CaptureService.busy -> refreshStatus()
            selection.packages.isEmpty() -> screen.showStatus("请至少选择一个应用。")
            else -> screen.confirmStart(::requestVpn)
        }
    }

    private fun requestVpn() {
        val consent = VpnService.prepare(this)
        if (consent == null) startCapture() else startActivityForResult(consent, VPN_CONSENT)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != VPN_CONSENT) return
        if (result == RESULT_OK) startCapture() else screen.showStatus("未授予 VPN 权限。")
    }

    private fun startCapture() {
        val intent = Intent(this, CaptureService::class.java)
            .putStringArrayListExtra(CaptureService.PACKAGES, ArrayList(selection.packages))
        startForegroundService(intent)
        screen.showStatus("正在启动抓包。")
    }

    private fun stopCapture() {
        if (CaptureService.busy) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
        }
    }

    private companion object {
        const val VPN_CONSENT = 10
    }
}
