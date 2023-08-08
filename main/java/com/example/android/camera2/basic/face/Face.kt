package com.example.android.camera2.basic.face

import java.util.*

class Face internal constructor(
    var width: Int, var height: Int,
    var imgWidth: Int, var imgHeight: Int,
    var landmarks: FloatArray
) {
    override fun toString(): String {
        return "Face{" +
                "landmarks=" + Arrays.toString(landmarks) +
                ", width=" + width +
                ", height=" + height +
                ", imgWidth=" + imgWidth +
                ", imgHeight=" + imgHeight +
                '}'
    }
}