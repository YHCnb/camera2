package com.example.android.camera2.basic.face

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.example.android.camera2.basic.camera.CameraHelper

class FaceTracker(model: String, seeta: String, private val mCameraHelper: CameraHelper) {
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private val mHandler: Handler
    private val mHandlerThread: HandlerThread
    private var self: Long

    //结果
    var mFace: Face? = null
    fun startTrack() {
        native_start(self)
    }

    fun stopTrack() {
        synchronized(this) {
            mHandlerThread.quitSafely()
            mHandler.removeCallbacksAndMessages(null)
            native_stop(self)
            self = 0
        }
    }

    fun detector(data: ByteArray?) {
        //把积压的 11号任务移除掉
        mHandler.removeMessages(11)
        //加入新的11号任务
        val message = mHandler.obtainMessage(11)
        message.obj = data
        mHandler.sendMessage(message)
    }

    //传入模型文件， 创建人脸识别追踪器和人眼定位器
    private external fun native_create(model: String, seeta: String): Long

    //开始追踪
    private external fun native_start(self: Long)

    //停止追踪
    private external fun native_stop(self: Long)

    //检测人脸
    private external fun native_detector(
        self: Long,
        data: ByteArray,
        cameraId: Int,
        width: Int,
        height: Int
    ): Face?

    init {
        self = native_create(model, seeta)
        mHandlerThread = HandlerThread("track")
        mHandlerThread.start()
        mHandler = object : Handler(mHandlerThread.looper) {
            override fun handleMessage(msg: Message) {
                //子线程 耗时再久 也不会对其他地方 (如：opengl绘制线程) 产生影响
                synchronized(this@FaceTracker) {
                    //定位 线程中检测
                    mFace = native_detector(
                        self,
                        msg.obj as ByteArray,
                        mCameraHelper.getCameraId(),
                        mCameraHelper.getSize().width,
                        mCameraHelper.getSize().height
                    )
                }
            }
        }
    }
}