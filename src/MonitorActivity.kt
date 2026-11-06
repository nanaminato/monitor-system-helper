package local.helperblock

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

class MonitorActivity : Activity() {
    private lateinit var selection: MonitorSelection
    private lateinit var screen: MonitorView
    private var appCatalog: Map<String, String>? = null
    private var loadingApps = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        applyChrome()
        selection = MonitorSelection(this)
        screen = MonitorView(
            this,
            back = { finish() },
            chooseApps = ::chooseApps,
            addPackage = ::addPackage,
            start = ::checkStart,
            stop = ::stopCapture,
            refresh = ::refreshStatus,
            setBlocked = ::setBlocked,
            removeApp = ::removeApp,
            enableLegacy = ::requestLegacyVpn,
            disableLegacy = { stopService(Intent(this, BlockService::class.java)) }
        )
        setContentView(screen)
        showRules()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun chooseApps() {
        appCatalog?.let {
            showPicker(it)
            return
        }
        if (loadingApps) return
        loadingApps = true
        screen.showCatalogLoading(true)
        Thread({
            val apps = runCatching { selection.availableApps() }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                loadingApps = false
                screen.showCatalogLoading(false)
                apps.onSuccess {
                    appCatalog = it
                    showPicker(it)
                }.onFailure { screen.showStatus("无法加载应用列表。") }
            }
        }, "app-catalog").start()
    }

    private fun showPicker(apps: Map<String, String>) {
        val names = selection.packages
        screen.chooseApps(apps + names.filterNot(apps::containsKey).associateWith { it }, names) {
            selection.replace(it)
            showRules()
            if (it != names || !CaptureService.busy) applySelection(startIfIdle = true)
        }
    }

    private fun showRules() {
        screen.showSelection(selection.packages, selection.blocked, appCatalog.orEmpty())
    }

    private fun addPackage() {
        screen.addPackage { rawName ->
            val before = selection.packages
            val accepted = selection.add(rawName)
            if (accepted) {
                showRules()
                if (selection.packages != before) applySelection(startIfIdle = true)
            }
            accepted
        }
    }

    private fun removeApp(name: String) {
        selection.replace(selection.packages - name)
        showRules()
        applySelection(startIfIdle = false)
    }

    private fun setBlocked(name: String, blocked: Boolean) {
        selection.setBlocked(name, blocked)
        applySelection(startIfIdle = false)
    }

    private fun applySelection(startIfIdle: Boolean) {
        when {
            selection.packages.isEmpty() -> stopCapture()
            CaptureService.busy || startIfIdle -> requestVpn()
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
        if (request == LEGACY_CONSENT && result == RESULT_OK) enableLegacy()
        if (request != VPN_CONSENT) return
        if (result == RESULT_OK) startCapture() else screen.showStatus("未授予 VPN 权限。")
    }

    private fun startCapture() {
        if (selection.packages.isEmpty()) return
        val intent = Intent(this, CaptureService::class.java)
            .putStringArrayListExtra(CaptureService.PACKAGES, ArrayList(selection.packages))
            .putStringArrayListExtra(CaptureService.BLOCKED_PACKAGES, ArrayList(selection.blocked))
        startForegroundService(intent)
        screen.showStatus(if (CaptureService.busy) "正在保存当前抓包并应用新规则。" else "正在启动抓包。")
    }

    private fun stopCapture() {
        if (CaptureService.busy) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
        }
    }

    private fun requestLegacyVpn() {
        if (CaptureService.busy) {
            screen.showStatus("请先停止通信抓包。")
            return
        }
        val consent = VpnService.prepare(this)
        if (consent == null) enableLegacy() else startActivityForResult(consent, LEGACY_CONSENT)
    }

    private fun enableLegacy() {
        if (!CaptureService.busy) startForegroundService(Intent(this, BlockService::class.java))
    }

    private companion object {
        const val VPN_CONSENT = 10
        const val LEGACY_CONSENT = 11
    }
}
