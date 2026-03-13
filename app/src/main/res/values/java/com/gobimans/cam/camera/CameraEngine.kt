package com.gobimans.cam.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CaptureParams(
    val iso: Int?,
    val exposureTimeNs: Long?,
    val auto: Boolean
)

class CameraEngine(context: Context, private val repository: CameraRepository) {
    private val cameraManager: CameraManager = repository.getCameraManager()
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var activeCameraId: String? = null
    @Volatile private var lastCaptureResult: CaptureResult? = null

    var onRawAvailable: ((ImageReader, android.media.Image, CameraCharacteristics, CaptureResult) -> Unit)? = null

    suspend fun openCamera(cameraId: String, surfaceHolder: SurfaceHolder, captureParams: CaptureParams) = withContext(Dispatchers.Main) {
        closeSession()
        startBackgroundThread()
        val chars = repository.getCameraCharacteristics(cameraId)
        val streamConfig = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: throw IllegalStateException("No stream config")
        val rawSizes = streamConfig.getOutputSizes(ImageFormat.RAW_SENSOR) ?: throw IllegalStateException("RAW_SENSOR not supported")
        val maxRaw = rawSizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: throw IllegalStateException("No RAW sizes")
        val imageReader = ImageReader.newInstance(maxRaw.width, maxRaw.height, ImageFormat.RAW_SENSOR, 2)
        this@CameraEngine.imageReader = imageReader
        val openDeferred = CompletableDeferred<Unit>()
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                activeCameraId = cameraId
                openDeferred.complete(Unit)
            }
            override fun onDisconnected(device: CameraDevice) {
                device.close()
                if (!openDeferred.isCompleted) openDeferred.completeExceptionally(RuntimeException("Camera disconnected"))
            }
            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                if (!openDeferred.isCompleted) openDeferred.completeExceptionally(RuntimeException("Camera error $error"))
            }
        }, backgroundHandler)
        openDeferred.await()
        createSession(surfaceHolder, captureParams, chars)
    }

    private suspend fun createSession(surfaceHolder: SurfaceHolder, captureParams: CaptureParams, chars: CameraCharacteristics) = withContext(Dispatchers.Main) {
        val device = cameraDevice ?: return@withContext
        val previewSurface = surfaceHolder.surface
        this@CameraEngine.previewSurface = previewSurface
        val ir = imageReader ?: return@withContext
        ir.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                val lastCaptureResult = lastCaptureResult ?: return@setOnImageAvailableListener
                val cameraChars = repository.getCameraCharacteristics(activeCameraId!!)
                onRawAvailable?.invoke(reader, image, cameraChars, lastCaptureResult)
            } catch (e: Exception) {
                Log.e("CameraEngine", "OnImageAvailable error", e)
            }
        }, backgroundHandler)
        val targets = listOf(previewSurface, ir.surface)
        val sessionDeferred = CompletableDeferred<Unit>()
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                startPreview(session, chars, captureParams)
                sessionDeferred.complete(Unit)
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                sessionDeferred.completeExceptionally(RuntimeException("Configure failed"))
            }
        }, backgroundHandler)
        sessionDeferred.await()
    }

    private fun startPreview(session: CameraCaptureSession, chars: CameraCharacteristics, captureParams: CaptureParams) {
        val device = cameraDevice ?: return
        val previewSurface = previewSurface ?: return
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
            set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_OFF)
            set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
            if (captureParams.auto) {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                captureParams.iso?.let { set(CaptureRequest.SENSOR_SENSITIVITY, it) }
                captureParams.exposureTimeNs?.let { set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
            }
        }
        session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                lastCaptureResult = result
            }
        }, backgroundHandler)
    }

    suspend fun captureStill(captureParams: CaptureParams): Boolean = withContext(Dispatchers.Main) {
        val device = cameraDevice ?: return@withContext false
        val session = captureSession ?: return@withContext false
        val ir = imageReader ?: return@withContext false
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(ir.surface)
            set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
            set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_OFF)
            set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
            if (captureParams.auto) {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                captureParams.iso?.let { set(CaptureRequest.SENSOR_SENSITIVITY, it) }
                captureParams.exposureTimeNs?.let { set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
            }
        }
        val deferred = CompletableDeferred<Boolean>()
        session.stopRepeating()
        session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                lastCaptureResult = result
                deferred.complete(true)
                startPreview(session, repository.getCameraCharacteristics(activeCameraId!!), captureParams)
            }
            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                deferred.complete(false)
                startPreview(session, repository.getCameraCharacteristics(activeCameraId!!), captureParams)
            }
        }, backgroundHandler)
        deferred.await()
    }

    fun closeSession() {
        try { captureSession?.close() } catch (_: Exception) { }
        captureSession = null
        try { cameraDevice?.close() } catch (_: Exception) { }
        cameraDevice = null
        try { imageReader?.close() } catch (_: Exception) { }
        imageReader = null
        previewSurface = null
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        val thread = HandlerThread("CameraBackground").also { it.start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }
}
