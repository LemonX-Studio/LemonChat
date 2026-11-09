package com.lemonchat.passid

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

// PassID-LC：本机账号密码保管箱，人人可用。
// 同时保留通行证能力：Lemon Chat 发起登录时跳过来，选一个账号点「确认」放行。
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var listArea: LinearLayout
    private var isConfirm = false
    private var selected = -1 // 确认模式下选中的账号下标

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("lemon_passid", MODE_PRIVATE)
        isConfirm = intent?.action == "com.lemonchat.passid.CONFIRM"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#F4FBF6"))
        }
        setContentView(root)

        root.addView(TextView(this).apply {
            text = if (isConfirm) "🔑 Lemon Chat 请求登录" else "🔑 PassID-LC"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B3D2F"))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (isConfirm) "选择一个已存账号放行（仅本机保存）"
                   else "账号密码保管箱 · 只存在这台手机里"
            textSize = 13f
            setTextColor(Color.parseColor("#8AA096"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(18))
        })

        // 新增账号输入
        val userEdit = EditText(this).apply { hint = "用户名"; setSingleLine(true) }
        val passEdit = EditText(this).apply {
            hint = "密码"
            setSingleLine(true)
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        }
        root.addView(userEdit)
        root.addView(passEdit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(Button(this).apply {
            text = "➕ 保存到保管箱"
            setBackgroundColor(Color.parseColor("#22C55E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val u = userEdit.text.toString().trim()
                val p = passEdit.text.toString()
                if (u.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this@MainActivity, "都填上再保存", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val arr = loadEntries()
                arr.put(JSONObject().put("username", u).put("password", p))
                prefs.edit().putString("entries", arr.toString()).apply()
                userEdit.setText(""); passEdit.setText("")
                Toast.makeText(this@MainActivity, "已保存 ✅", Toast.LENGTH_SHORT).show()
                renderList()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })

        root.addView(TextView(this).apply {
            text = if (isConfirm) "↓ 点选账号后按确认" else "已存账号（长按删除）"
            textSize = 13f
            setTextColor(Color.parseColor("#8AA096"))
            setPadding(0, dp(16), 0, dp(6))
        })

        listArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listArea) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })

        renderList()
    }

    private fun loadEntries(): JSONArray =
        JSONArray(prefs.getString("entries", "[]") ?: "[]")

    private fun renderList() {
        listArea.removeAllViews()
        val arr = loadEntries()
        if (arr.length() == 0) {
            listArea.addView(TextView(this).apply {
                text = "还没有存任何账号，上面填一个吧"
                textSize = 13f
                setTextColor(Color.parseColor("#8AA096"))
                gravity = Gravity.CENTER
                setPadding(0, dp(30), 0, 0)
            })
        }
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16).toFloat()
                }
                setPadding(dp(16), dp(12), dp(16), dp(12))
                addView(TextView(this@MainActivity).apply {
                    text = e.getString("username")
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1B3D2F"))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "密码：••••••（点卡片看，长按删）"
                    textSize = 12f
                    setTextColor(Color.parseColor("#8AA096"))
                })
                if (isConfirm && selected == i) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor("#E6F5EC"))
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(2), Color.parseColor("#22C55E"))
                    }
                }
                setOnClickListener {
                    if (isConfirm) {
                        selected = i; renderList()
                    } else {
                        // 平时点击：显示明文密码，点账号用途
                        Toast.makeText(this@MainActivity,
                            "密码：${e.getString("password")}（已复制可粘贴）",
                            Toast.LENGTH_LONG).show()
                        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("pass", e.getString("password")))
                    }
                }
                setOnLongClickListener {
                    val a2 = loadEntries()
                    val a3 = JSONArray()
                    for (j in 0 until a2.length()) if (j != i) a3.put(a2.getJSONObject(j))
                    prefs.edit().putString("entries", a3.toString()).apply()
                    Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    renderList()
                    true
                }
            }
            listArea.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        if (isConfirm) {
            listArea.addView(Button(this).apply {
                text = "✅ 确认，允许登录"
                isEnabled = selected >= 0
                alpha = if (selected >= 0) 1f else 0.5f
                setBackgroundColor(if (selected >= 0) Color.parseColor("#22C55E") else Color.parseColor("#C8E6CF"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    if (selected < 0) return@setOnClickListener
                    val e = loadEntries().getJSONObject(selected)
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("username", e.getString("username"))
                        putExtra("password", e.getString("password"))
                    })
                    finish()
                }
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
            listArea.addView(Button(this).apply {
                text = "拒绝"
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.parseColor("#D64545"))
                setOnClickListener { setResult(RESULT_CANCELED); finish() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
