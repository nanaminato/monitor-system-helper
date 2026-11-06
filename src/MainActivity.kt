package local.helperblock

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(mainView(
            this,
            enable = ::requestVpn,
            disable = { stopService(Intent(this, BlockService::class.java)) },
            monitor = { startActivity(Intent(this, MonitorActivity::class.java)) }
        ))
    }

    private fun requestVpn() {
        val consent = VpnService.prepare(this)
        if (consent == null) enableBlocker() else startActivityForResult(consent, VPN_CONSENT)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == VPN_CONSENT && result == RESULT_OK) enableBlocker()
    }

    private fun enableBlocker() {
        if (CaptureService.busy) {
            Toast.makeText(this, "请先停止通信抓包。", Toast.LENGTH_SHORT).show()
            return
        }
        startForegroundService(Intent(this, BlockService::class.java))
    }

    private companion object {
        const val VPN_CONSENT = 1
    }
}
