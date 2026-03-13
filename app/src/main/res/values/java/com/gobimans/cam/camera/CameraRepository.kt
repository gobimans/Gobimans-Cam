package com.gobimans.cam.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import android.graphics.ImageFormat

data class PhysicalCamera(
    val cameraId: String,
    val isBack: Boolean,
    val focalLength: Float?,
    val capabilities: IntArray,
    val hasRaw: Boolean
)

data class RawConfig(
    val cameraId: String,
    val rawSize: Size
)

class CameraRepository(private val context: Context) {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun getCameras(): List<PhysicalCamera> {
        return cameraManager.cameraIdList.mapNotNull { id ->
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val isBack = facing == CameraCharacteristics.LENS_FACING_BACK
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focal = focalLengths?.firstOrNull()
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                val hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                PhysicalCamera(
                    cameraId = id,
                    isBack = isBack,
                    focalLength = focal,
                    capabilities = caps,
                    hasRaw = hasRaw
                )
            } catch (e: Exception) {
                Log.e("CameraRepository", "Error getting camera $id", e)
                null
            }
        }
    }

    fun isRawSupported(): Boolean {
        return getCameras().any { it.hasRaw }
    }

    fun getCameraCharacteristics(cameraId: String): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    fun getCameraManager(): CameraManager = cameraManager
}
