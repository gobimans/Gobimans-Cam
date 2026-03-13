package com.gobimans.cam.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DngSaver(private val context: Context) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun saveDng(
        image: Image,
        characteristics: CameraCharacteristics,
        result: CaptureResult
    ): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = "GOBIMANS_${dateFormat.format(Date())}.dng"
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Gobimans Cam"
            )
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: run {
            Log.e("DngSaver", "Failed to create MediaStore record")
            image.close()
            return@withContext null
        }
        var out: OutputStream? = null
        try {
            out = resolver.openOutputStream(uri) ?: throw IllegalStateException("No output stream")
            val dngCreator = DngCreator(characteristics, result)
            dngCreator.writeImage(out, image)
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            Log.e("DngSaver", "Error saving DNG", e)
            resolver.delete(uri, null, null)
            return@withContext null
        } finally {
            try { out?.close() } catch (_: Exception) { }
            image.close()
        }
        uri.toString()
    }
}
