package com.example.android.camera2.basic.util

interface OnDecoderPlayer {
    fun offer(data: ByteArray?)
    fun pool(): ByteArray?
    fun setVideoParamerter(width: Int, height: Int, fps: Int)
    fun onFinish()
}