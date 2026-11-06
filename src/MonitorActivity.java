package local.helperblock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import java.util.ArrayList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class MonitorActivity extends Activity {
    private static final int VPN_CONSENT = 10;
    private SharedPreferences preferences;
    private final Set<String> packages = new TreeSet<>();
    private TextView selection;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("应用通信监控");
        preferences = getSharedPreferences("monitor", MODE_PRIVATE);
        packages.addAll(preferences.getStringSet("packages", new HashSet<>()));
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        scroll.addView(layout);
        selection = new TextView(this);
        layout.addView(selection);
        addButton(layout, "选择应用", this::chooseApps);
        addButton(layout, "按包名添加", this::addPackage);
        status = new TextView(this);
        layout.addView(status);
        addButton(layout, "开始抓包", this::checkStart);
        addButton(layout, "停止抓包", () -> {
            if (CaptureService.busy) {
                startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP));
            }
        });
        addButton(layout, "刷新抓包状态", this::refreshStatus);
        setContentView(scroll);
        showSelection();
    }

    private void addButton(LinearLayout layout, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(view -> action.run());
        layout.addView(button);
    }

    private void chooseApps() {
        Map<String, String> apps = new TreeMap<>();
        for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            apps.put(app.packageName, app.loadLabel(getPackageManager()).toString());
        }
        for (String name : packages) apps.putIfAbsent(name, name);
        apps.remove(getPackageName());
        String[] names = apps.keySet().toArray(new String[0]);
        String[] labels = new String[names.length];
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            labels[i] = apps.get(names[i]) + "\n" + names[i];
            checked[i] = packages.contains(names[i]);
        }
        new AlertDialog.Builder(this).setTitle("选择要监控的应用")
            .setMultiChoiceItems(labels, checked, (dialog, index, selected) -> checked[index] = selected)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", (dialog, button) -> {
                packages.clear();
                for (int i = 0; i < names.length; i++) {
                    if (checked[i]) packages.add(names[i]);
                }
                saveSelection();
            }).show();
    }

    private void addPackage() {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setHint("例如 com.mobiletools.systemhelper");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("添加应用包名")
            .setView(input).setNegativeButton("取消", null).setPositiveButton("添加", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (!name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
                    || name.equals(getPackageName())) {
                input.setError("请输入要监控的其他应用的包名");
                return;
            }
            packages.add(name);
            saveSelection();
            dialog.dismiss();
        });
    }

    private void saveSelection() {
        preferences.edit().putStringSet("packages", new HashSet<>(packages)).apply();
        showSelection();
    }

    private void showSelection() {
        selection.setText(packages.isEmpty() ? "尚未选择应用" : "已选择\n" + String.join("\n", packages));
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        status.setText(CaptureService.status);
    }

    private void checkStart() {
        if (CaptureService.busy) {
            refreshStatus();
            return;
        }
        if (packages.isEmpty()) {
            status.setText("请至少选择一个应用。");
            return;
        }
        new AlertDialog.Builder(this).setTitle("开始记录通信")
            .setMessage("将保存所选应用的原始通信包。文件可能含有明文敏感数据。抓包 VPN 可能替换现有 VPN，包括本应用的拦截模式。")
            .setNegativeButton("取消", null)
            .setPositiveButton("继续", (dialog, button) -> requestVpn())
            .show();
    }

    private void requestVpn() {
        Intent consent = VpnService.prepare(this);
        if (consent == null) startCapture();
        else startActivityForResult(consent, VPN_CONSENT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != VPN_CONSENT) return;
        if (result == RESULT_OK) startCapture();
        else status.setText("未授予 VPN 权限。");
    }

    private void startCapture() {
        startForegroundService(new Intent(this, CaptureService.class)
            .putStringArrayListExtra(CaptureService.PACKAGES, new ArrayList<>(packages)));
        status.setText("正在启动抓包。");
    }
}
