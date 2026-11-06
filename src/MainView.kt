package local.helperblock

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal fun mainView(
    context: Context,
    enable: () -> Unit,
    disable: () -> Unit,
    monitor: () -> Unit
): LinearLayout = verticalPanel(context).apply {
    addView(TextView(context).apply {
        text = "Block network access for com.mobiletools.systemhelper"
    })
    addButton("Enable blocker", enable)
    addButton("Disable blocker", disable)
    addButton("应用通信监控", monitor)
}

internal fun verticalPanel(context: Context) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(32, 32, 32, 32)
}

internal fun LinearLayout.addButton(label: String, action: () -> Unit) {
    addView(Button(context).apply {
        text = label
        setOnClickListener { action() }
    })
}
