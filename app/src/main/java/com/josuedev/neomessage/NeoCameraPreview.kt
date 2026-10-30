package com.josuedev.neomessage

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream

@Composable
fun RealCameraPreview(
    isFrontCamera: Boolean = true,
    onFrameCaptured: ((ByteArray) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    var currentFacing by remember { mutableStateOf<Boolean?>(null) }

    fun safeSetupCamera(cam: Camera?, holder: SurfaceHolder) {
        if (cam == null) return
        try {
            cam.setDisplayOrientation(90)
            cam.setPreviewDisplay(holder)
            
            var lastFrame = 0L
            cam.setPreviewCallback { data, c ->
                if (onFrameCaptured != null && data != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastFrame > 220) { // ~4-5 FPS óptimo
                        lastFrame = now
                        try {
                            val size = c.parameters.previewSize
                            if (size != null) {
                                val yuv = YuvImage(data, ImageFormat.NV21, size.width, size.height, null)
                                val out = ByteArrayOutputStream()
                                yuv.compressToJpeg(Rect(0, 0, size.width, size.height), 28, out)
                                onFrameCaptured(out.toByteArray())
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            cam.startPreview()
        } catch (e: Exception) {
            Log.e("NeoCamera", "Error en preview: ${e.message}")
        }
    }

    AndroidView(
        factory = { ctx ->
            object : SurfaceView(ctx), SurfaceHolder.Callback {
                init {
                    holder.addCallback(this)
                }

                override fun surfaceCreated(h: SurfaceHolder) {
                    try {
                        val cameraId = getCameraId(isFrontCamera)
                        camera = Camera.open(cameraId)
                        currentFacing = isFrontCamera
                        safeSetupCamera(camera, h)
                    } catch (e: Exception) {
                        Log.e("NeoCamera", "Error abriendo cámara: ${e.message}")
                    }
                }

                override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
                    if (h.surface == null) return
                    try {
                        if (camera == null) {
                            val cameraId = getCameraId(isFrontCamera)
                            camera = Camera.open(cameraId)
                            currentFacing = isFrontCamera
                            safeSetupCamera(camera, h)
                        }
                    } catch (e: Exception) {
                        Log.e("NeoCamera", "Error en surfaceChanged: ${e.message}")
                    }
                }

                override fun surfaceDestroyed(h: SurfaceHolder) {
                    try {
                        camera?.setPreviewCallback(null)
                        camera?.stopPreview()
                        camera?.release()
                    } catch (_: Exception) {}
                    camera = null
                    currentFacing = null
                }
            }
        },
        update = { sv ->
            // SOLO cambiar de cámara si el usuario presionó el botón de girar cámara
            if (currentFacing != null && currentFacing != isFrontCamera) {
                try {
                    camera?.setPreviewCallback(null)
                    camera?.stopPreview()
                    camera?.release()
                    val cameraId = getCameraId(isFrontCamera)
                    camera = Camera.open(cameraId)
                    currentFacing = isFrontCamera
                    safeSetupCamera(camera, sv.holder)
                } catch (e: Exception) {
                    Log.e("NeoCamera", "Error cambiando orientación de cámara: ${e.message}")
                }
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                camera?.setPreviewCallback(null)
                camera?.stopPreview()
                camera?.release()
            } catch (_: Exception) {}
            camera = null
            currentFacing = null
        }
    }
}

private fun getCameraId(isFront: Boolean): Int {
    try {
        val count = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (i in 0 until count) {
            Camera.getCameraInfo(i, info)
            if (isFront && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return i
            } else if (!isFront && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i
            }
        }
    } catch (_: Exception) {}
    return 0
}
