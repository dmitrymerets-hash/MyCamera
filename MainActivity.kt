package com.example.mycamera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var facing = CameraCharacteristics.LENS_FACING_BACK
    private var enabled = true
    private val thread = HandlerThread("CameraThread")
    private lateinit var handler: Handler
    private val manager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thread.start(); handler = Handler(thread.looper)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff101820.toInt()) }
        status = TextView(this).apply { text = "Камера выключена"; setTextColor(-1); gravity = Gravity.CENTER; textSize = 18f; setPadding(12, 22, 12, 22) }
        preview = TextureView(this)
        val switch = Button(this).apply { text = "Сменить камеру"; setOnClickListener { facing = if (facing == CameraCharacteristics.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK; closeCamera(); if (enabled) openCamera() } }
        val toggle = Button(this).apply { text = "Выключить камеру"; setOnClickListener { enabled = !enabled; text = if (enabled) "Выключить камеру" else "Включить камеру"; if (enabled) openCamera() else { closeCamera(); status.text = "Камера выключена" } } }
        root.addView(status)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(switch); root.addView(toggle)
        setContentView(root)
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { if (enabled) openCamera() }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun openCamera() {
        if (!enabled || !preview.isAvailable || camera != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10); return
        }
        try {
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing }
            if (id == null) { status.text = "Эта камера недоступна"; return }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; startPreview(device) }
                override fun onDisconnected(device: CameraDevice) { device.close(); if (camera === device) camera = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); if (camera === device) camera = null; runOnUiThread { status.text = "Ошибка камеры: $error" } }
            }, handler)
        } catch (e: Exception) { status.text = "Не удалось открыть камеру: ${e.message}" }
    }

    private fun startPreview(device: CameraDevice) {
        try {
            if (!enabled || !preview.isAvailable) { device.close(); return }
            val texture = preview.surfaceTexture ?: return
            val size = manager.getCameraCharacteristics(device.id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)?.firstOrNull { it.width <= 1920 && it.height <= 1080 }
                ?: manager.getCameraCharacteristics(device.id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)?.firstOrNull()
                ?: return
            texture.setDefaultBufferSize(size.width, size.height)
            val surface = Surface(texture)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface); set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) }
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (camera !== device || !enabled) { s.close(); return }
                    session = s
                    try { s.setRepeatingRequest(request.build(), null, handler); runOnUiThread { status.text = "Камера включена" } }
                    catch (e: Exception) { runOnUiThread { status.text = "Ошибка просмотра" } }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { runOnUiThread { status.text = "Не удалось запустить видео" } }
            }, handler)
        } catch (e: Exception) { runOnUiThread { status.text = "Ошибка: ${e.message}" } }
    }

    private fun closeCamera() { session?.close(); session = null; camera?.close(); camera = null }
    override fun onResume() { super.onResume(); if (::preview.isInitialized && enabled) openCamera() }
    override fun onPause() { closeCamera(); super.onPause() }
    override fun onDestroy() { closeCamera(); thread.quitSafely(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) { if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera() else status.text = "Разрешение на камеру не предоставлено" }
    }
}
