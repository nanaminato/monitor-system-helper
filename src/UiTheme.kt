package local.helperblock

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 界面视觉基础：配色、圆角、文字与常用控件。
 * 项目不使用 XML 资源（build.ps1 直接以 aapt2 编译 manifest），因此全部由代码构建。
 */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

internal class Palette(
    val dark: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceMuted: Int,
    val border: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val accent: Int,
    val accentMuted: Int,
    val onAccent: Int,
    val success: Int,
    val successMuted: Int,
    val danger: Int,
    val dangerMuted: Int,
    val warning: Int,
    val headerStart: Int,
    val headerEnd: Int,
    val onHeader: Int,
    val onHeaderMuted: Int,
    val trackOff: Int
)

private val lightPalette = Palette(
    dark = false,
    background = 0xFFF3F4F7.toInt(),
    surface = 0xFFFFFFFF.toInt(),
    surfaceMuted = 0xFFF6F7FA.toInt(),
    border = 0xFFE3E6EC.toInt(),
    textPrimary = 0xFF14171C.toInt(),
    textSecondary = 0xFF5A6270.toInt(),
    textTertiary = 0xFF8B93A1.toInt(),
    accent = 0xFF2F63E8.toInt(),
    accentMuted = 0xFFE8EEFC.toInt(),
    onAccent = 0xFFFFFFFF.toInt(),
    success = 0xFF10915F.toInt(),
    successMuted = 0xFFE2F5EC.toInt(),
    danger = 0xFFD93A3F.toInt(),
    dangerMuted = 0xFFFCE8E8.toInt(),
    warning = 0xFFB06A00.toInt(),
    headerStart = 0xFF17212F.toInt(),
    headerEnd = 0xFF2B3D55.toInt(),
    onHeader = 0xFFFFFFFF.toInt(),
    onHeaderMuted = 0xFF9DAEC4.toInt(),
    trackOff = 0xFFCFD6E0.toInt()
)

private val darkPalette = Palette(
    dark = true,
    background = 0xFF0C1016.toInt(),
    surface = 0xFF1A202A.toInt(),
    surfaceMuted = 0xFF232B37.toInt(),
    border = 0xFF2E3846.toInt(),
    textPrimary = 0xFFEDF1F7.toInt(),
    textSecondary = 0xFFA6B0BF.toInt(),
    textTertiary = 0xFF7A8595.toInt(),
    accent = 0xFF6E9BFF.toInt(),
    accentMuted = 0xFF1E2C4A.toInt(),
    onAccent = 0xFF06101F.toInt(),
    success = 0xFF4FD1A0.toInt(),
    successMuted = 0xFF14302A.toInt(),
    danger = 0xFFFF7B7E.toInt(),
    dangerMuted = 0xFF3A1D1F.toInt(),
    warning = 0xFFE2A94A.toInt(),
    headerStart = 0xFF0D1219.toInt(),
    headerEnd = 0xFF1B2634.toInt(),
    onHeader = 0xFFF2F6FB.toInt(),
    onHeaderMuted = 0xFF8FA0B6.toInt(),
    trackOff = 0xFF3A4351.toInt()
)

private var cachedPalette: Palette? = null
private var cachedNight: Boolean? = null

internal val Context.palette: Palette
    get() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val cached = cachedPalette
        if (cached != null && cachedNight == night) return cached
        val fresh = if (night) darkPalette else lightPalette
        cachedNight = night
        cachedPalette = fresh
        return fresh
    }

internal object Fonts {
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val mono: Typeface = Typeface.MONOSPACE
}

internal fun Context.shape(fill: Int, radius: Int, strokeWidth: Int = 0, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpf(radius.toFloat())
        setColor(fill)
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

internal fun rippleOver(content: Drawable, ripple: Int): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(ripple), content, null)

internal fun Context.text(
    value: CharSequence,
    sizeSp: Float,
    color: Int,
    medium: Boolean = false,
    mono: Boolean = false
): TextView = TextView(this).apply {
    text = value
    textSize = sizeSp
    setTextColor(color)
    typeface = when {
        mono -> Fonts.mono
        medium -> Fonts.medium
        else -> Typeface.DEFAULT
    }
    includeFontPadding = false
    setLineSpacing(0f, 1.15f)
}

/** 卡片：白色/深色底 + 细描边 + 轻微阴影。 */
internal fun Context.card(padding: Int = 18, radius: Int = 20): LinearLayout {
    val p = palette
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(p.surface, radius, 1, p.border)
        elevation = dpf(1.5f)
        setPadding(dp(padding), dp(padding - 2), dp(padding), dp(padding))
    }
}

internal fun Context.sectionLabel(value: CharSequence, color: Int = palette.textSecondary): TextView =
    text(value.toString().uppercase(), 12f, color, medium = true).apply {
        letterSpacing = 0.06f
    }

internal fun Context.chip(value: CharSequence, fg: Int, bg: Int): TextView =
    text(value, 11f, fg, medium = true).apply {
        background = shape(bg, 8)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        gravity = Gravity.CENTER
    }

internal fun Context.dot(color: Int, sizeDp: Int = 9): View = View(this).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

private fun Context.buttonBackground(fill: Int, textColor: Int, radius: Int, border: Int, ripple: Int): Drawable {
    val content = shape(fill, radius, if (border != 0) 1 else 0, border)
    return rippleOver(content, ripple)
}

internal fun Context.filledButton(label: String, action: () -> Unit): TextView {
    val p = palette
    return text(label, 15f, p.onAccent, medium = true).apply {
        gravity = Gravity.CENTER
        background = buttonBackground(p.accent, p.onAccent, 16, 0, 0x33FFFFFF)
        minHeight = dp(50)
        setPadding(dp(18), dp(15), dp(18), dp(15))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }
}

internal fun Context.outlineButton(label: String, action: () -> Unit): TextView {
    val p = palette
    return text(label, 15f, p.textPrimary, medium = true).apply {
        gravity = Gravity.CENTER
        background = buttonBackground(p.surfaceMuted, p.textPrimary, 16, p.border, if (p.dark) 0x24FFFFFF else 0x14101720)
        minHeight = dp(48)
        setPadding(dp(16), dp(13), dp(16), dp(13))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }
}

internal fun Context.linkButton(label: String, action: () -> Unit): TextView {
    val p = palette
    return text(label, 13f, p.accent, medium = true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        minHeight = dp(40)
        isClickable = true
        isFocusable = true
        background = rippleOver(shape(Color.TRANSPARENT, 10), if (p.dark) 0x33FFFFFF else 0x1A2F63E8)
        setOnClickListener { action() }
    }
}

internal fun LinearLayout.addFull(view: View, top: Int = 0, bottom: Int = 0) {
    addView(view, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = context.dp(top)
        bottomMargin = context.dp(bottom)
    })
}

internal fun Context.hairline(color: Int = palette.border): View = View(this).apply {
    background = GradientDrawable().apply { setColor(color) }
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
}

/** 顶部深色渐变标题区，底部两角圆角，供下方卡片叠压。 */
internal fun Context.screenHeader(
    eyebrow: String,
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)? = null
): LinearLayout {
    val p = palette
    val radius = dpf(28f)
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(p.headerStart, p.headerEnd)
        ).apply { cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f) }
        setPadding(dp(20), dp(18), dp(20), dp(52))

        if (onBack != null) {
            val back = text("\u2190", 18f, p.onHeader, medium = true).apply {
                gravity = Gravity.CENTER
                background = rippleOver(shape(0x26FFFFFF, 18), 0x40FFFFFF)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                contentDescription = "返回"
                isClickable = true
                isFocusable = true
                setOnClickListener { onBack() }
            }
            addView(back)
        }

        addFull(text(eyebrow, 11f, p.onHeaderMuted, medium = true).apply {
            letterSpacing = 0.14f
        }, top = if (onBack != null) 16 else 0)

        addFull(text(title, 27f, p.onHeader, medium = true), top = 8)
        if (subtitle != null) {
            addFull(text(subtitle, 13f, p.onHeaderMuted), top = 8)
        }
    }
}

/** 统一窗口外观：无系统标题栏、状态栏与标题区同色、浅色主题使用深色导航栏图标。 */
internal fun Activity.applyChrome() {
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    val p = palette
    window.setBackgroundDrawable(ColorDrawable(p.background))
    window.statusBarColor = p.headerStart
    window.navigationBarColor = p.background
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility =
        if (p.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
}

/** 抓包状态：由服务状态文本推导，仅用于界面呈现。 */
internal enum class CaptureState(val title: String) {
    IDLE("未开始抓包"),
    STARTING("正在启动抓包"),
    RUNNING("正在后台抓包"),
    STOPPING("正在停止并保存"),
    FAILED("抓包未能开始");

    fun color(palette: Palette): Int = when (this) {
        RUNNING -> palette.success
        STARTING, STOPPING -> palette.warning
        FAILED -> palette.danger
        IDLE -> palette.textTertiary
    }
}

internal fun captureState(message: String): CaptureState = when {
    message.startsWith("无法") -> CaptureState.FAILED
    message.startsWith("正在停止") -> CaptureState.STOPPING
    message.startsWith("正在初始化") || message.startsWith("正在启动") -> CaptureState.STARTING
    CaptureService.busy -> CaptureState.RUNNING
    else -> CaptureState.IDLE
}

/** 圆角自定义对话框容器：透明窗口 + 内容面板。 */
internal class PanelDialog(context: Context, private val keyboard: Boolean = false) : Dialog(context) {
    val content: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(18))
        background = context.shape(context.palette.surface, 26)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(content)
    }

    override fun show() {
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(
                if (keyboard) {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                } else {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }
            )
        }
        super.show()
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

internal fun Context.confirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    val p = palette
    val panel = PanelDialog(this)
    panel.content.apply {
        addFull(text(title, 19f, p.textPrimary, medium = true))
        addFull(text(message, 13.5f, p.textSecondary), top = 10)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(context.outlineButton("取消") { panel.dismiss() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, context.dp(50), 1f).apply {
                    marginEnd = context.dp(10)
                }
            })
            addView(context.filledButton(confirmLabel) {
                panel.dismiss()
                onConfirm()
            }, LinearLayout.LayoutParams(0, context.dp(50), 1f))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })
    }
    panel.show()
}

/** 折叠箭头，避免使用依赖字体渲染的字符。 */
internal class ChevronView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = context.palette.textTertiary
    }
    private val path = Path()

    var expanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = width * 0.22f
        path.reset()
        if (expanded) {
            path.moveTo(cx - s, cy - s * 0.45f)
            path.lineTo(cx, cy + s * 0.6f)
            path.lineTo(cx + s, cy - s * 0.45f)
        } else {
            path.moveTo(cx - s * 0.45f, cy - s)
            path.lineTo(cx + s * 0.6f, cy)
            path.lineTo(cx - s * 0.45f, cy + s)
        }
        canvas.drawPath(path, paint)
    }
}

/** 应用首字母头像，颜色由包名稳定派生。 */
internal class AppAvatar(context: Context) : TextView(context) {
    private val palette = context.palette
    private val tints = intArrayOf(
        0xFF2F63E8.toInt(), 0xFF7A4DE8.toInt(), 0xFF0E8C7F.toInt(),
        0xFFC2410C.toInt(), 0xFF1D6FB8.toInt(), 0xFF9A3412.toInt()
    )

    init {
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = Fonts.medium
        layoutParams = LinearLayout.LayoutParams(context.dp(42), context.dp(42))
        includeFontPadding = false
    }

    fun bind(key: String, label: String) {
        val tint = if (key.isEmpty()) palette.textTertiary else tints[(key.hashCode() and 0x7FFFFFFF) % tints.size]
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (palette.dark) 0x2E000000 or (tint and 0x00FFFFFF) else 0x1A000000 or (tint and 0x00FFFFFF))
        }
        setTextColor(tint)
        text = label.take(1).uppercase()
    }
}

/** 空状态用的简易流量图形，避免依赖字符或系统图标。 */
internal class BarsGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.palette.textTertiary
        alpha = 150
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val widthUnit = width * 0.42f
        val barHeight = height * 0.075f
        val gap = height * 0.155f
        val start = height / 2f - (barHeight * 3 + gap * 2) / 2f
        for (index in 0..2) {
            val half = widthUnit * (1f - index * 0.16f) / 2f
            val top = start + index * (barHeight + gap)
            canvas.drawRoundRect(cx - half, top, cx + half, top + barHeight, barHeight / 2, barHeight / 2, paint)
        }
    }
}

/** 三列指标行。 */
internal fun Context.metricRow(left: String, middle: String, right: String): LinearLayout {
    val p = palette
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for ((index, pair) in listOf("包" to left, "应用" to middle, "拦截" to right).withIndex()) {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(context.text(pair.second, 20f, p.textPrimary, medium = true))
                addView(context.text(pair.first, 11f, p.textTertiary).apply {
                    letterSpacing = 0.08f
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = context.dp(3) })
            })
            if (index < 2) addView(View(context).apply {
                background = GradientDrawable().apply { setColor(p.border) }
                layoutParams = LinearLayout.LayoutParams(context.dp(1), context.dp(28)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = context.dp(12)
                    marginEnd = context.dp(12)
                }
            })
        }
    }
}
