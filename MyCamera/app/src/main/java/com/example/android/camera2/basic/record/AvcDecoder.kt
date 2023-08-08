package com.example.android.camera2.basic.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.android.camera2.basic.util.OnDecoderPlayer
import java.io.IOException

/**
 * 视频解码
 */
class AvcDecoder {
    private var onDecoderPlayer: OnDecoderPlayer? = null
    private var mediaExtractor: MediaExtractor? = null
    private var mPath: String? = null
    private var mWidth = 0
    private var mHeight = 0
    private var fps = 0
    private var isDecoding = false
    private lateinit var outData: ByteArray
    private var codecTask: CodecTask? = null
    private var mediaCodec: MediaCodec? = null
    fun setCallBackListener(onDecoderPlayer: OnDecoderPlayer?) {
        this.onDecoderPlayer = onDecoderPlayer
    }

    /**
     * 设置要解码的视频地址
     *
     * @param path
     */
    fun setDataSource(path: String?) {
        mPath = path
    }

    fun prepare() {
        try {
            mediaExtractor = MediaExtractor()
            mediaExtractor!!.setDataSource(mPath!!)
            val trackCount = mediaExtractor!!.trackCount
            var videoIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until trackCount) {
                val trackFormat = mediaExtractor!!.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime!!.startsWith("video/")) {
                    videoIndex = i
                    videoFormat = trackFormat
                    break
                }
            }
            if (videoFormat != null) {
                mWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
                mHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
                fps = 20
                if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    fps = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                }
                //KEY_DURATION
                videoFormat.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                )
                mediaCodec =
                    MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME)!!)
                mediaCodec!!.configure(videoFormat, null, null, 0)
                mediaExtractor!!.selectTrack(videoIndex)
            }
            if (onDecoderPlayer != null) {
                onDecoderPlayer!!.setVideoParamerter(mWidth, mHeight, fps)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun start() {
        isDecoding = true
        outData = ByteArray(mWidth * mHeight * 3 / 2)
        codecTask = CodecTask()
        codecTask!!.start()
    }

    fun stop() {
        isDecoding = false
        if (null != codecTask && codecTask!!.isAlive) {
            try {
                codecTask!!.join(3000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            if (codecTask!!.isAlive) {
                codecTask!!.interrupt()
            }
            codecTask = null
        }
        if (onDecoderPlayer != null) onDecoderPlayer!!.onFinish()
    }

    internal inner class CodecTask : Thread() {
        private var isEOF = false
        override fun run() {
            super.run()
            if (null == mediaCodec) {
                return
            }
            mediaCodec!!.start()
            val bufferInfo = MediaCodec.BufferInfo()
            while (!isInterrupted) {
                if (!isDecoding) {
                    break
                }
                if (!isEOF) {
                    isEOF = dequeueInputBuffer()
                }
                val status = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 0)
                if (status >= 0) {
                    val outputBuffer = mediaCodec!!.getOutputBuffer(status)
                    if (bufferInfo.size == outData.size) {
                        outputBuffer!![outData]
                        if (onDecoderPlayer != null) {
                            onDecoderPlayer!!.offer(outData)
                        }
                    }
                    mediaCodec!!.releaseOutputBuffer(status, false)
                    try {
                        sleep((1000 / fps).toLong())
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (onDecoderPlayer != null) onDecoderPlayer!!.onFinish()
                    break
                }
            }
            mediaCodec!!.stop()
            mediaCodec!!.release()
            mediaCodec = null
            mediaExtractor!!.release()
            mediaExtractor = null
        }

        private fun dequeueInputBuffer(): Boolean {
            val status = mediaCodec!!.dequeueInputBuffer(0)
            if (status > 0) {
                val inputBuffer = mediaCodec!!.getInputBuffer(status)
                inputBuffer!!.clear()
                val size = mediaExtractor!!.readSampleData(inputBuffer, 0)
                return if (size < 0) {
                    mediaCodec!!.queueInputBuffer(
                        status,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    true
                } else {
                    mediaCodec!!.queueInputBuffer(status, 0, size, mediaExtractor!!.sampleTime, 0)
                    mediaExtractor!!.advance()
                    false
                }
            }
            return false
        }
    }
}