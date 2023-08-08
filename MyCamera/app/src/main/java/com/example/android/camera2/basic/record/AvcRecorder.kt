package com.example.android.camera2.basic.record

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGLContext
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.example.android.camera2.basic.util.OnRecordListener
import java.io.IOException

/**
 * 实现录像，并保存在指定路径
 */
class AvcRecorder(
    private val mContext: Context,
    private val mWidth: Int,
    private val mHeight: Int,
    private val eglContext: EGLContext
) {
    private var mSavePath: String? = null
    private var mSpeed = 0f
    private var inputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    private var mHandler: Handler? = null
    private var isPlaying = false
    private var eglConfigBase: EglConfigBase? = null
    private var mediaCodec: MediaCodec? = null
    private var avcIndex = 0
    private var onRecordListener: OnRecordListener? = null
    private val fps = 20
    fun start(speed: Float, savePath: String?) {
        mSavePath = savePath
        mSpeed = speed
        try {
            val mediaFormat =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight)
            mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mWidth * mHeight * fps / 5)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, fps)
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mediaCodec!!.createInputSurface()
            mediaMuxer = MediaMuxer(mSavePath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)


            //-------------配置EGL环境----------------
            val handlerThread = HandlerThread("elgCodec")
            handlerThread.start()
            mHandler = Handler(handlerThread.looper)
            mHandler!!.post {
                eglConfigBase = EglConfigBase(mContext, mWidth, mHeight, inputSurface, eglContext)
                mediaCodec!!.start()
                isPlaying = true
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun encodeFrame(textureId: Int, timeStamp: Long) {
        if (!isPlaying) return
        mHandler!!.post {
            eglConfigBase!!.draw(textureId, timeStamp)
            getCodec(false)
        }
    }

    private fun getCodec(endOfStream: Boolean) {
        if (endOfStream) {
            mediaCodec!!.signalEndOfInputStream()
        }
        val bufferInfo = MediaCodec.BufferInfo()
        val status = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10000)
        //表示请求超时，10_000毫秒内没有数据到来
        if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
        } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            //编码格式改变 ，第一次start 都会调用一次
            val outputFormat = mediaCodec!!.outputFormat
            //设置mediaMuxer 的视频轨
            avcIndex = mediaMuxer!!.addTrack(outputFormat)
            mediaMuxer!!.start()
        } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            //Outputbuffer  改变了
        } else {
            val outputBuffer = mediaCodec!!.getOutputBuffer(status)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                bufferInfo.size = 0
            }
            if (bufferInfo.size > 0) {
                bufferInfo.presentationTimeUs = (bufferInfo.presentationTimeUs / mSpeed).toLong()
                outputBuffer!!.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.size - bufferInfo.offset)
                //交给mediaMuxer 保存
                mediaMuxer!!.writeSampleData(avcIndex, outputBuffer, bufferInfo)
            }
            mediaCodec!!.releaseOutputBuffer(status, false)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            }
        }
    }

    fun stop() {
        isPlaying = false
        mHandler!!.post {
            getCodec(true)
            mediaCodec!!.stop()
            mediaCodec!!.release()
            mediaCodec = null
            mediaMuxer!!.stop()
            mediaMuxer!!.release()
            mediaMuxer = null
            eglConfigBase!!.release()
            eglConfigBase = null
            inputSurface!!.release()
            inputSurface = null
            mHandler!!.looper.quitSafely()
            mHandler = null
            if (onRecordListener != null) {
                onRecordListener!!.recordFinish(mSavePath)
            }
        }
    }

    fun setOnRecordListener(onRecordListener: OnRecordListener?) {
        this.onRecordListener = onRecordListener
    }
}