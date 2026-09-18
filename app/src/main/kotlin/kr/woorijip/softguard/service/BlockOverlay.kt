package kr.woorijip.softguard.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 차단 안내 화면 (설계문서 §4.3).
 *
 * 접근성 서비스 전용 창(TYPE_ACCESSIBILITY_OVERLAY)으로 띄운다. 이 타입은 '다른 앱 위에 표시'
 * 권한이 필요 없고, 백그라운드 액티비티 실행 제한도 받지 않는다.
 * 뷰는 첫 차단 때 한 번 만들고 재사용한다. Compose 대신 일반 View 를 쓴 것은
 * 서비스 컨텍스트에서 라이프사이클 소유자 없이 안정적으로 붙이기 위해서다.
 */
class BlockOverlay(
    private val service: AccessibilityService,
    private val onHome: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private lateinit var appName: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView

    var isShowing: Boolean = false
        private set

    fun show(appLabel: String, titleText: String, bodyText: String) {
        val view = root ?: build().also { root = it }
        appName.text = appLabel
        title.text = titleText
        body.text = bodyText
        body.visibility = if (bodyText.isBlank()) View.GONE else View.VISIBLE
        if (!isShowing) {
            wm.addView(view, layoutParams())
            isShowing = true
        }
    }

    fun hide() {
        val view = root ?: return
        if (!isShowing) return
        try {
            wm.removeViewImmediate(view)
        } catch (_: Exception) {
        }
        isShowing = false
    }

    fun destroy() {
        hide()
        root = null
    }

    private fun layoutParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.MATCH_PARENT
        gravity = Gravity.CENTER
        // 포커스를 잡지 않아 뒤로 가기는 아래 앱으로 간다 (앱이 닫히면 홈 화면 이벤트가 와서 오버레이도 내려간다).
        // 터치는 이 창이 받으므로 아래 앱은 조작할 수 없다.
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()

    private fun build(): View {
        val ctx = service
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F21F2937"))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true // 아래로 터치가 새지 않게
        }

        val icon = TextView(ctx).apply {
            text = "🔒" // 🔒
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            gravity = Gravity.CENTER
        }
        appName = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(8))
        }
        title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
        }
        body = TextView(ctx).apply {
            setTextColor(Color.parseColor("#D1D5DB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        val home = Button(ctx).apply {
            text = "홈으로"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setOnClickListener { onHome() }
        }
        val homeParams = LinearLayout.LayoutParams(dp(200), dp(56)).apply { topMargin = dp(32) }

        column.addView(icon)
        column.addView(appName)
        column.addView(title)
        column.addView(body)
        column.addView(home, homeParams)
        return column
    }
}
