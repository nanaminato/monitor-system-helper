package local.helperblock

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun verticalPanel(context: Context) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(context.dp(20), context.dp(16), context.dp(20), context.dp(24))
}

internal fun LinearLayout.addHeading(label: String, size: Float = 20f) {
    addSpaced(TextView(context).apply {
        text = label
        textSize = size
        setTypeface(typeface, Typeface.BOLD)
        isAccessibilityHeading = true
    }, if (childCount == 0) 0 else 24)
}

internal fun LinearLayout.addDescription(label: String) {
    addSpaced(TextView(context).apply {
        text = label
        textSize = 14f
        setTextAppearance(android.R.style.TextAppearance_Material_Body1)
    }, 6)
}

internal fun LinearLayout.addButton(label: String, action: () -> Unit, secondary: Boolean = false) {
    val style = if (secondary) android.R.attr.borderlessButtonStyle else android.R.attr.buttonStyle
    addSpaced(Button(context, null, style).apply {
        text = label
        isAllCaps = false
        minHeight = context.dp(48)
        setOnClickListener { action() }
    }, 4)
}

internal fun LinearLayout.addSpaced(view: View, top: Int) {
    addView(view, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = context.dp(top) })
}
