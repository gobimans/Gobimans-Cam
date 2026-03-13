package com.gobimans.cam.ui

import android.app.Application
import android.media.ImageReader
import android.view.SurfaceHolder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gobimans.cam.camera.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CameraUiState(
    val hasPermissions: Boolean = false,
    val rawSupported: Boolean = true,
    val isBlocked: Boolean = false,
    val isCapturing: Boolean = false,
    val lastSavedUri: String? = null,
    val iso: Int = 400,
    val exposureMs: Float = 1 / 60f,
    val manualMode: Boolean = false,
    val orientationDegrees: Int = 0,
    val currentLensIndex: Int = 0,
    val lenses: List<LensInfo> = emptyList()
)

data class LensInfo(
    val cameraId: String,
    val label: String
)

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CameraRepository(app)
    private val engine = CameraEngine(app, repository)
    private val dngSaver = DngSaver(app)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    private var currentCameraId: String? = null
    private val orientationManager = OrientationManager(app) { degrees ->
        _uiState.value = _uiState.value.copy(orientationDegrees = degrees)
    }

    init {
        engine.onRawAvailable = { reader, image, chars, result ->
            viewModelScope.launch {
                val uri = dngSaver.saveDng(image, chars, result)
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    lastSavedUri = uri
                )
            }
        }
        val rawSupported = repository.isRawSupported()
        _uiState.value = _uiState.value.copy(
            rawSupported = rawSupported,
            isBlocked = !rawSupported
        )
        val lenses = buildLenses()
        _uiState.value = _uiState.value.copy(lenses = lenses)
        if (lenses.isNotEmpty()) {
            currentCameraId = lenses.first().cameraId
        }
    }

    private fun buildLenses(): List<LensInfo> {
        val cams = repository.getCameras().filter { it.hasRaw && it.isBack }.sortedBy { it.focalLength ?: 0f }
        return cams.mapIndexed { index, cam ->
            val label = when (index) {
                0 -> "0.5x"
                1 -> "1x"
                else -> "5x"
            }
            LensInfo(cameraId = cam.cameraId, label = label)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
    }

    fun startOrientation() { orientationManager.enable() }
    fun stopOrientation() { orientationManager.disable() }
    fun setManualEnabled(enabled: Boolean) { _uiState.value = _uiState.value.copy(manualMode = enabled) }
    fun setIso(iso: Int) { _uiState.value = _uiState.value.copy(iso = iso) }
    fun setExposureMs(ms: Float) { _uiState.value = _uiState.value.copy(exposureMs = ms) }
    fun nextLens() {
        val lenses = _uiState.value.lenses
        if (lenses.isEmpty()) return
        val nextIndex = (_uiState.value.currentLensIndex + 1) % lenses.size
        _uiState.value = _uiState.value.copy(currentLensIndex = nextIndex)
        currentCameraId = lenses[nextIndex].cameraId
    }
    fun prevLens() {
        val lenses = _uiState.value.lenses
        if (lenses.isEmpty()) return
        val nextIndex = if (_uiState.value.currentLensIndex - 1 < 0) lenses.size - 1 else _uiState.value.currentLensIndex - 1
        _uiState.value = _uiState.value.copy(currentLensIndex = nextIndex)
        currentCameraId = lenses[nextIndex].cameraId
    }

    fun buildCaptureParams(): CaptureParams {
        val s = _uiState.value
        return if (!s.manualMode) {
            CaptureParams(iso = null, exposureTimeNs = null, auto = true)
        } else {
            val exposureNs = (s.exposureMs * 1_000_000f).toLong()
            CaptureParams(iso = s.iso, exposureTimeNs = exposureNs, auto = false)
        }
    }

    fun currentCameraId(): String? = currentCameraId
    fun onPreviewReady(holder: SurfaceHolder) {
        val id = currentCameraId ?: return
        val params = buildCaptureParams()
        viewModelScope.launch { engine.openCamera(id, holder, params) }
    }
    fun capture() {
        val params = buildCaptureParams()
        _uiState.value = _uiState.value.copy(isCapturing = true)
        viewModelScope.launch {
            val ok = engine.captureStill(params)
            if (!ok) { _uiState.value = _uiState.value.copy(isCapturing = false) }
        }
    }
    fun onPause() {
        engine.closeSession()
        stopOrientation()
    }
    fun onResume(surfaceHolder: SurfaceHolder?) {
        startOrientation()
        if (surfaceHolder != null && currentCameraId != null && _uiState.value.hasPermissions && !_uiState.value.isBlocked) {
            onPreviewReady(surfaceHolder)
        }
    }

    companion object {
        fun provideFactory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CameraViewModel(app) as T
                }
            }
        }
    }
}
