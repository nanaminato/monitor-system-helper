package local.helperblock;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        TextView title = new TextView(this);
        title.setText("Block network access for com.mobiletools.systemhelper");
        layout.addView(title);
        Button start = new Button(this);
        start.setText("Enable blocker");
        start.setOnClickListener(view -> {
            Intent consent = VpnService.prepare(this);
            if (consent == null) enable();
            else startActivityForResult(consent, 1);
        });
        layout.addView(start);
        Button stop = new Button(this);
        stop.setText("Disable blocker");
        stop.setOnClickListener(view -> stopService(new Intent(this, BlockService.class)));
        layout.addView(stop);
        Button monitor = new Button(this);
        monitor.setText("应用通信监控");
        monitor.setOnClickListener(view -> startActivity(new Intent(this, MonitorActivity.class)));
        layout.addView(monitor);
        setContentView(layout);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && result == RESULT_OK) enable();
    }

    private void enable() {
        if (CaptureService.busy) {
            android.widget.Toast.makeText(this, "请先停止通信抓包。", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        startForegroundService(new Intent(this, BlockService.class));
    }
}
