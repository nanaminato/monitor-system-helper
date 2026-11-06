package local.helperblock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;
import java.io.IOException;

public class BlockService extends VpnService {
    private ParcelFileDescriptor tunnel;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (tunnel != null) return START_NOT_STICKY;

        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
            "block", "Network blocker", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "block")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("SystemHelper network blocker")
            .setContentIntent(open)
            .setOngoing(true)
            .build());

        try {
            // The TUN interface has no forwarding path. Its queue drops excess packets.
            tunnel = new Builder()
                .setSession("SystemHelper Blocker")
                .addAllowedApplication("com.mobiletools.systemhelper")
                .addAddress("192.0.2.1", 32)
                .addAddress("2001:db8::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("192.0.2.53")
                .addDnsServer("2001:db8::53")
                .establish();
            if (tunnel == null) fail("VPN authorization unavailable");
            else Toast.makeText(this, "SystemHelper blocker active", Toast.LENGTH_SHORT).show();
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            fail("Could not start blocker: " + error.getMessage());
        }
        return START_NOT_STICKY;
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        stopSelf();
    }

    @Override public void onDestroy() {
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (IOException ignored) {
                // The service is already stopping.
            }
        }
        super.onDestroy();
    }
}
