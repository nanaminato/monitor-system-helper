package local.helperblock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

public class CaptureService extends VpnService {
    static final String STOP = "local.helperblock.STOP_CAPTURE";
    static final String PACKAGES = "packages";
    static boolean busy;
    static String status = "当前没有运行抓包。";
    private volatile boolean running;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopCapture();
            return START_NOT_STICKY;
        }
        if (busy) return START_NOT_STICKY;

        startForeground(2, notification());
        try {
            ArrayList<String> packages = intent.getStringArrayListExtra(PACKAGES);
            if (packages == null || packages.isEmpty() || packages.contains(getPackageName())) {
                throw new IllegalArgumentException("请选择至少一个其他应用。");
            }
            System.loadLibrary("capture");
            Builder builder = new Builder()
                .setSession("SystemHelper 通信监控")
                .setMtu(1500)
                .addAddress("192.0.2.1", 32)
                .addAddress("2001:db8::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0);
            for (String name : packages) {
                try {
                    builder.addAllowedApplication(name);
                } catch (android.content.pm.PackageManager.NameNotFoundException error) {
                    throw new IllegalArgumentException("应用未安装或不可用：" + name);
                }
            }

            ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
            Network network = connectivity.getActiveNetwork();
            LinkProperties link = connectivity.getLinkProperties(network);
            if (link != null) {
                for (InetAddress server : link.getDnsServers()) {
                    if (!server.isLinkLocalAddress()) builder.addDnsServer(server);
                }
            }
            ParcelFileDescriptor tunnel = builder.establish();
            if (tunnel == null) throw new IOException("VPN 授权不可用。");
            stopService(new Intent(this, BlockService.class));

            busy = true;
            running = true;
            status = "正在初始化抓包。";
            new Thread(() -> record(tunnel), "packet-capture").start();
        } catch (IOException | RuntimeException | UnsatisfiedLinkError error) {
            status = "无法开始抓包：" + error.getMessage();
            Toast.makeText(this, status, Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
            "capture", "应用通信监控", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, MonitorActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 0,
            new Intent(this, CaptureService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "capture")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在记录所选应用的通信")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "停止抓包", stop).build())
            .build();
    }

    private void record(ParcelFileDescriptor tunnel) {
        Uri file = null;
        boolean saved = false;
        String name = "SystemHelperBlocker-" + System.currentTimeMillis() + ".pcap";
        String outcome;
        try (ParcelFileDescriptor input = tunnel) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.tcpdump.pcap");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SystemHelperBlocker");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            file = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (file == null) throw new IOException("无法创建 PCAP 文件。");
            try (ParcelFileDescriptor output = getContentResolver().openFileDescriptor(file, "w")) {
                if (output == null) throw new IOException("无法打开 PCAP 文件。");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (running) status = "正在后台抓包。停止后可在下载目录查看 PCAP。";
                });
                if (capture(input.getFd(), output.getFd()) != 0) {
                    throw new IOException("隧道或 PCAP 写入出错。");
                }
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            saved = getContentResolver().update(file, values, null, null) == 1;
            if (!saved) throw new IOException("无法发布 PCAP 文件。");
            outcome = "已保存\nDownload/SystemHelperBlocker/" + name;
        } catch (IOException | RuntimeException | UnsatisfiedLinkError error) {
            outcome = "抓包结束：" + error.getMessage();
        } finally {
            if (file != null && !saved) {
                try {
                    getContentResolver().delete(file, null, null);
                } catch (RuntimeException error) {
                    android.util.Log.w("CaptureService", "Could not remove pending capture", error);
                }
            }
        }
        String result = outcome;
        new Handler(Looper.getMainLooper()).post(() -> {
            running = false;
            busy = false;
            status = result;
            stopSelf();
        });
    }

    private boolean shouldCapture() {
        return running;
    }

    private native int capture(int tunnel, int output);

    private void stopCapture() {
        if (busy) status = "正在停止抓包并保存文件。";
        else stopSelf();
        running = false;
    }

    @Override public void onRevoke() {
        running = false;
        new Handler(Looper.getMainLooper()).post(this::stopCapture);
    }

    @Override public void onDestroy() {
        running = false;
        super.onDestroy();
    }
}
