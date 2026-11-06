package local.helperblock

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.widget.Toast
import java.io.IOException

class BlockService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (tunnel != null) return START_NOT_STICKY
        startForeground(1, blockerNotification(this))

        try {
            // The TUN interface has no forwarding path. Its queue drops excess packets.
            tunnel = Builder()
                .setSession("SystemHelper Blocker")
                .addAllowedApplication("com.mobiletools.systemhelper")
                .addAddress("192.0.2.1", 32)
                .addAddress("2001:db8::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("192.0.2.53")
                .addDnsServer("2001:db8::53")
                .establish()
            if (tunnel == null) fail("VPN authorization unavailable")
            else Toast.makeText(this, "SystemHelper blocker active", Toast.LENGTH_SHORT).show()
        } catch (error: PackageManager.NameNotFoundException) {
            fail("Could not start blocker: ${error.message}")
        } catch (error: RuntimeException) {
            fail("Could not start blocker: ${error.message}")
        }
        return START_NOT_STICKY
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    override fun onDestroy() {
        try {
            tunnel?.close()
        } catch (_: IOException) {
            // The service is already stopping.
        } finally {
            super.onDestroy()
        }
    }
}
