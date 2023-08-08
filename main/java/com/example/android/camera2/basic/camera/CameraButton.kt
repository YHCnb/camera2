package com.example.android.camera2.basic.camera

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * 集合拍照与录像两种行为模式的button
 * 通过两种Listener执行对应操作
 */
class CameraButton @kotlin.jvm.JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) :androidx.appcompat.widget.AppCompatButton(context, attrs) {

    /** 拍摄模式 */
    private var cameraMode = CameraMode.PHOTO

    private var isRecording = false

    private var recordListener: OnRecordListener? = null
    private var captureListener: OnCapturedListener? = null


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (recordListener == null || captureListener == null) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                if (cameraMode== CameraMode.PHOTO){
                    captureListener!!.onCapture()
                }else{
                    if (isRecording){
                        recordListener!!.onRecordStop()
                    }else{
                        recordListener!!.onRecordStart()
                    }
                    isRecording = !isRecording
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (cameraMode== CameraMode.PHOTO || !isRecording){
                    isPressed = false
                }
            }
        }
        return true
    }

    fun setCaptureMode(mode: CameraMode){
        cameraMode = mode
        if (mode== CameraMode.VIDEO){
            isRecording = false
        }
    }

    fun setOnCapturedListener(listener: OnCapturedListener?) {
        captureListener = listener
    }

    fun setOnRecordListener(listener: OnRecordListener?) {
        recordListener = listener
    }

    interface OnCapturedListener {
        fun onCapture()
    }

    interface OnRecordListener {
        fun onRecordStart()
        fun onRecordStop()
    }
}