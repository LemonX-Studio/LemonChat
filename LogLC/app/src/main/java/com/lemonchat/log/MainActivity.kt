package com.lemonchat.log

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Log-LC：查看 Lemon Chat 写在 /sdcard 的运行日志与崩溃日志
class MainActivity : Activity() {

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
            setBackgroundColor(Color.parseColor("#F4FBF6"))
        }
        setContentView(root)

        root.addView(TextView(this).apply {
            text = "📋 Log-LC · Lemon Chat 运行日志"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B3D2F"))
        })
        root.addView(TextView(this).apply {
            text = "读取 /sdcard/lemonchat_log.txt 与 lemonchat_crash.txt"
            textSize = 12f
            setTextColor(Color.parseColor("#8AA096"))
            setPadding(0, dp(4), 0, dp(10))
        })

        root.addView(Button(this).apply {
            text = "刷新"
            setBackgroundColor(Color.parseColor("#22C55E"))
            setTextColor(Color.WHITE)
            setOnClickListener { render() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.parseColor("#22332B"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply {
            addView(logView)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
        render()
    }

    private fun render() {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        for (name in listOf("lemonchat_crash.txt", "lemonchat_log.txt")) {
            val f = File(Environment.getExternalStorageDirectory(), name)
            sb.append("════ $name （${if (f.exists()) sdf.format(Date(f.lastModified())) + " · " + f.length() + "B" else "不存在"}）════\n")
            if (f.exists()) {
                val lines = f.readLines()
                lines.takeLast(300).forEach { sb.append(it).append('\n') }
                if (lines.size > 300) sb.append("…（仅显示最后300行，共${lines.size}行）\n")
            }
            sb.append('\n')
        }
        logView.text = sb.toString()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "没有存储权限读不了日志哦", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
