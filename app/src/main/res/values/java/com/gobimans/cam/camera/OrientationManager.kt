package com.gobimans.cam.camera

import android.content.Context
import android.view.OrientationEventListener

class OrientationManager(
    context: Context,
    private val onOrientationChangedDegrees: (Int) -> Unit
) : OrientationEventListener(context) {

    private var lastBucket = 0

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        val bucket = when {
            inRange(orientation, 315, 360) || inRange(orientation, 0, 45) -> 0
            inRange(orientation, 45, 135) -> 90
            inRange(orientation, 135, 225) -> 180
            else -> 270
        }
        if (bucket != lastBucket) {
            lastBucket = bucket
            onOrientationChangedDegrees(bucket)
        }
    }

    private fun inRange(value: Int, start: Int, end: Int): Boolean {
        return value in start until end
    }
}
