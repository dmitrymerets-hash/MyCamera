package com.example.mycamera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
 private var browser: WebView? = null
 private var request: PermissionRequest? = null
 private lateinit var root: LinearLayout
 private val role = "camera"
 private val prefs by lazy { getSharedPreferences("remote", MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.rgb(10,15,27)
  window.navigationBarColor = Color.rgb(10,15,27)
  root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setBackgroundColor(Color.rgb(10,15,27))
   setOnApplyWindowInsetsListener { view, insets ->
    val bars = insets.getInsets(WindowInsets.Type.systemBars())
    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
    insets
   }
  }
  setContentView(root)
  root.requestApplyInsets()
  showSetup()
 }
 private fun showSetup() {
  browser?.destroy()
  browser = null
  root.removeAllViews()
  val heading = TextView(this).apply {
   text = "MyCamera — камера"
   textSize = 24f
   setTextColor(Color.WHITE)
   setPadding(24,30,24,20)
  }
  val help = TextView(this).apply {
   text = "Введи HTTPS-адрес твоего бесплатного Cloudflare Worker. Он должен быть одинаковым на обоих телефонах."
   textSize = 15f
   setTextColor(Color.LTGRAY)
   setPadding(24,0,24,18)
  }
  val address = EditText(this).apply {
   setSingleLine()
   hint = "https://mycamera-remote.имя.workers.dev"
   setText(prefs.getString("server",""))
   setTextColor(Color.WHITE)
   setHintTextColor(Color.GRAY)
   setPadding(24,12,24,12)
  }
  val open = Button(this).apply { text = "Открыть MyCamera — камера" }
  root.addView(heading);root.addView(help);root.addView(address)
  root.addView(open)
  open.setOnClickListener {
   val url = address.text.toString().trim().trimEnd('/')
   val parsed = try { android.net.Uri.parse(url) } catch (_: Exception) { null }
   if (parsed?.scheme != "https" || parsed.host.isNullOrBlank() ||
       parsed.host?.endsWith(".workers.dev") != true || parsed.path !in listOf(null, "", "/")) {
    Toast.makeText(this,"Нужен HTTPS-адрес вида https://имя.workers.dev",Toast.LENGTH_LONG).show()
    return@setOnClickListener
   }
   prefs.edit().putString("server",url).apply()
   showBrowser(url)
  }
 }
 private fun showBrowser(url: String) {
  root.removeAllViews()
  val back = Button(this).apply {
   text = "Сменить сервер"
   setOnClickListener { closeBrowser();showSetup() }
  }
  root.addView(back)
  val view = WebView(this)
  browser = view
  root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
  view.settings.javaScriptEnabled = true
  view.settings.domStorageEnabled = true
  view.settings.mediaPlaybackRequiresUserGesture = true
  view.settings.allowFileAccess = false
  view.settings.allowContentAccess = false
  view.webViewClient = object : WebViewClient() {
   override fun shouldOverrideUrlLoading(v: WebView?, req: android.webkit.WebResourceRequest?): Boolean {
    return req?.url?.host != android.net.Uri.parse(url).host
   }
  }
  view.webChromeClient = object : WebChromeClient() {
   override fun onPermissionRequest(newRequest: PermissionRequest) {
    runOnUiThread {
     val expectedHost = android.net.Uri.parse(url).host
     if (role != "camera" || newRequest.origin.scheme != "https" || newRequest.origin.host != expectedHost ||
         !newRequest.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
      newRequest.deny()
     } else {
      request = newRequest
      if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
       allowCamera()
      } else {
       requestPermissions(arrayOf(Manifest.permission.CAMERA),100)
      }
     }
    }
   }
   override fun onPermissionRequestCanceled(cancelled: PermissionRequest) {
    if (request == cancelled) request = null
   }
  }
  view.loadUrl(url+"/?role="+role)
 }
 private fun allowCamera() {
  val pending=request
  request=null
  pending?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
 }
 private fun closeBrowser() {
  request?.deny();request=null
  browser?.apply { loadUrl("about:blank");stopLoading();destroy() }
  browser=null
 }
 override fun onRequestPermissionsResult(code:Int, permissions:Array<out String>,results:IntArray) {
  super.onRequestPermissionsResult(code,permissions,results)
  if(code==100) {
   if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) allowCamera()
   else { request?.deny();request=null }
  }
 }
 override fun onStop() {
  super.onStop()
  // Stop capture whenever the broadcasting app leaves the screen.
  if(role=="camera" && browser!=null) {
   browser?.loadUrl("about:blank")
   Toast.makeText(this,"Трансляция остановлена",Toast.LENGTH_SHORT).show()
  }
 }
 override fun onResume() {
  super.onResume()
  if(role=="camera" && browser?.url == "about:blank") {
   prefs.getString("server",null)?.let {browser?.loadUrl(it+"/?role=camera")}
  }
 }
 override fun onDestroy() {closeBrowser();super.onDestroy()}
}
