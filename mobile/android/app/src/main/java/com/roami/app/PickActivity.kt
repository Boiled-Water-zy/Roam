package com.roami.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** 选环境：存过的服务器一行一个，底下输网址。选过一次以后启动 3 秒倒计时自动进，点任意处取消。 */
class PickActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick)
        prefs = Prefs(this)
        val url = findViewById<EditText>(R.id.url)
        findViewById<View>(R.id.go).setOnClickListener { go(url.text.toString()) }
        url.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_GO) { go(url.text.toString()); true } else false }
        findViewById<View>(R.id.root).setOnClickListener { cancelAuto() }
        findViewById<TextView>(R.id.ver).text = "Roami " + (runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "")
        url.setOnFocusChangeListener { _, has -> if (has) cancelAuto() }
    }

    override fun onResume() {
        super.onResume()
        render()
        // 从 WebActivity 摇回来的（带 noauto）不再倒计时
        val cur = prefs.current
        if (cur != null && !intent.getBooleanExtra("noauto", false)) startAuto(cur) else cancelAuto(silent = true)
        intent.removeExtra("noauto")
    }

    override fun onPause() { super.onPause(); timer?.cancel(); timer = null }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        for (s in prefs.servers) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) }
                addView(TextView(context).apply { text = s.name; setTextColor(0xFFE6EDF3.toInt()); textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
                addView(TextView(context).apply { text = s.url; setTextColor(0xFF8B949E.toInt()); textSize = 15f })
                setOnClickListener { go(s.url) }
                setOnLongClickListener {
                    AlertDialog.Builder(context).setMessage(R.string.pick_delete)
                        .setPositiveButton(android.R.string.ok) { _, _ -> prefs.forget(s.url); render() }
                        .setNegativeButton(android.R.string.cancel, null).show(); true
                }
            }
            list.addView(row)
        }
    }

    private fun startAuto(url: String) {
        val auto = findViewById<TextView>(R.id.auto)
        timer?.cancel()
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(ms: Long) { auto.text = getString(R.string.pick_auto, (ms / 1000 + 1).toInt(), url) }
            override fun onFinish() { go(url) }
        }.start()
    }

    private fun cancelAuto(silent: Boolean = false) {
        if (timer == null && silent) { findViewById<TextView>(R.id.auto).text = ""; return }
        timer?.cancel(); timer = null
        findViewById<TextView>(R.id.auto).text = if (silent) "" else getString(R.string.pick_auto_cancel)
    }

    private fun go(raw: String) {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        if (runCatching { java.net.URI(u).host }.getOrNull().isNullOrEmpty()) {
            Toast.makeText(this, R.string.pick_bad_url, Toast.LENGTH_SHORT).show(); return
        }
        timer?.cancel(); timer = null
        prefs.remember(u)
        startActivity(Intent(this, WebActivity::class.java).putExtra("url", u))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
