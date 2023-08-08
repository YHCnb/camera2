package com.example.android.camera2.basic.openGL

import java.nio.ByteBuffer
import java.nio.ByteOrder

class GLImage {
    private var y_len = 0
    private var uv_len = 0
    private lateinit var yBytes: ByteArray
    private lateinit var uvBytes: ByteArray
    var y: ByteBuffer? = null
        private set
    var u: ByteBuffer? = null
        private set
    var v: ByteBuffer? = null
        private set
    private var hasImage = false

    /**
     * 初始化
     * @param width
     * @param height
     */
    fun initSize(width: Int, height: Int) {
        //初始化 y、u、v数据缓存 y的数据长度
        y_len = width * height
        //u和v的字节长度
        uv_len = width / 2 * height / 2
        //存储y的字节
        yBytes = ByteArray(y_len)
        uvBytes = ByteArray(uv_len)
        //保存y、u、v数据
        y = ByteBuffer.allocateDirect(y_len).order(ByteOrder.nativeOrder())
        u = ByteBuffer.allocateDirect(uv_len).order(ByteOrder.nativeOrder())
        v = ByteBuffer.allocateDirect(uv_len).order(ByteOrder.nativeOrder())
    }

    /**
     * 分离yuv
     * @param data
     * @return
     */
    fun initData(data: ByteArray): Boolean {
        hasImage = readBytes(data, y, 0, y_len) && readBytes(data, u, y_len, uv_len) && readBytes(
            data,
            v,
            y_len + uv_len,
            uv_len
        )
        return hasImage
    }

    private fun readBytes(data: ByteArray, buffer: ByteBuffer?, offset: Int, len: Int): Boolean {
        //有没有这么长的数据刻度
        if (data.size < offset + len) {
            return false
        }
        val bytes: ByteArray
        bytes = if (len == yBytes.size) {
            yBytes
        } else {
            uvBytes
        }
        System.arraycopy(data, offset, bytes, 0, len)
        buffer!!.position(0)
        buffer.put(bytes)
        buffer.position(0)
        return true
    }

    fun hasImage(): Boolean {
        return hasImage
    }
}