package com.example.mycamera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), SurfaceHolder.Callback {
 private lateinit var preview: SurfaceView
 private lateinit var status: TextView
 private lateinit var manager: CameraManager
 private var camera: CameraDevice? = null
 private var session: CameraCaptureSession? = null
 private var front = false
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  manager = getSystemService(CAMERA_SERVICE) as CameraManager
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
  status = TextView(this).apply { text = "My Camera"; setTextColor(Color.WHITE); textSize = 20f; setPadding(20,20,20,20) }
  preview = SurfaceView(this)
  layout.addView(status)
  layout.addView(preview, LinearLayout.LayoutParams(-1,0,1f))
  val switch = Button(this).apply { text = "Switch camera"; setOnClickListener { front = !front; startCamera() } }
  val stop = Button(this).apply { text = "Stop camera"; setOnClickListener { closeCamera(); status.text = "Camera stopped" } }
  val start = Button(this).apply { text = "Start camera"; setOnClickListener { startCamera() } }
  layout.addView(switch); layout.addView(stop); layout.addView(start)
  setContentView(layout)
  layout.setOnApplyWindowInsetsListener { view, insets ->
   val bars = insets.getInsets(WindowInsets.Type.systemBars())
   view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
   insets
  }
  layout.requestApplyInsets()
  preview.holder.addCallback(this)
  if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA),1)
 }
 private fun closeCamera() { session?.close(); session=null; camera?.close(); camera=null }
 private fun startCamera() {
  closeCamera()
  if (!preview.holder.surface.isValid || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
  try {
   val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == (if(front) android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT else android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) } ?: return
   manager.openCamera(id, object : CameraDevice.StateCallback() {
    override fun onOpened(device: CameraDevice) {
     camera=device
     val surface=preview.holder.surface
     device.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
      override fun onConfigured(s:CameraCaptureSession){
       session=s
       try { val request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }.build(); s.setRepeatingRequest(request,null,null); runOnUiThread { status.text="Camera active" } } catch(e:Exception){runOnUiThread{status.text=e.message}}
      }
      override fun onConfigureFailed(s:CameraCaptureSession){runOnUiThread{status.text="Camera setup failed"}}
     },null)
    }
    override fun onDisconnected(device:CameraDevice){device.close()}
    override fun onError(device:CameraDevice,error:Int){device.close();runOnUiThread{status.text="Camera error: $error"}}
   },null)
  } catch(e:Exception){status.text=e.message}
 }
 override fun surfaceCreated(holder:SurfaceHolder){startCamera()}
 override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){}
 override fun surfaceDestroyed(holder:SurfaceHolder){closeCamera()}
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==1 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startCamera()}
 override fun onPause(){super.onPause();closeCamera()}
}
