package com.example.android.camera2.basic.util

import android.hardware.camera2.CameraCharacteristics

/**
 * 一些Label对
 */
object LabelLib {
    fun getAWBLabel(extension: Int): String {
        return when (extension) {
            CameraCharacteristics.CONTROL_AWB_MODE_OFF -> "OFF"
            CameraCharacteristics.CONTROL_AWB_MODE_AUTO -> "AUTO"
            CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"
            CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT-> "FLUORESCENT"
            CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT-> "WARM_FLUORESCENT"
            CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT-> "DAYLIGHT"
            CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT-> "CLOUDY_DAYLIGHT"
            CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT-> "TWILIGHT"
            CameraCharacteristics.CONTROL_AWB_MODE_SHADE-> "SHADE"
            else -> "not support"
        }
    }
}