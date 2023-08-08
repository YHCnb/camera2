package com.example.android.camera2.basic.filter

import android.content.Context
import android.opengl.GLES20
import com.example.android.camera2.basic.util.OpenGlUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

open class BaseFilter(
    mContext: Context?,
    protected var mVertexShaderId: Int,
    protected var mFragShaderId: Int
) {
    protected val mGlVertexBuffer: FloatBuffer
    protected val mGlTextureBuffer: FloatBuffer
    protected var mVertexShader: String? = null
    protected var mFragShader: String? = null
    protected var mProgramId = 0
    protected var vTexture = 0
    protected var vMatrix = 0
    protected var vPosition = 0
    protected var vCoord = 0
    protected var mOutputHeight = 0
    protected var mOutputWidth = 0
    protected var y = 0
    protected var x = 0

    private fun initilize(mContext: Context?) {
        //读取着色器信息
        mVertexShader = mContext?.let { OpenGlUtils.readRawShaderFile(it, mVertexShaderId) }
        mFragShader = mContext?.let { OpenGlUtils.readRawShaderFile(it, mFragShaderId) }
        //创建着色器程序
        mProgramId = OpenGlUtils.loadProgram(mVertexShader, mFragShader)
        //获取着色器变量，需要赋值
        vPosition = GLES20.glGetAttribLocation(mProgramId, "vPosition")
        vCoord = GLES20.glGetAttribLocation(mProgramId, "vCoord")
        vMatrix = GLES20.glGetUniformLocation(mProgramId, "vMatrix")
        vTexture = GLES20.glGetUniformLocation(mProgramId, "vTexture")
    }

    open fun prepare(width: Int, height: Int, x: Int, y: Int) {
        mOutputWidth = width
        mOutputHeight = height
        this.x = x
        this.y = y
    }

    open fun onDrawFrame(textureId: Int): Int {
        GLES20.glViewport(x, y, mOutputWidth, mOutputHeight)
        GLES20.glUseProgram(mProgramId)
        mGlVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, mGlVertexBuffer)
        GLES20.glEnableVertexAttribArray(vPosition)
        mGlTextureBuffer.position(0)
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, mGlTextureBuffer)
        GLES20.glEnableVertexAttribArray(vCoord)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        //传如的是GL_TEXTURE_2D类型
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(vTexture, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        return textureId
    }

    open fun release() {
        GLES20.glDeleteProgram(mProgramId)
    }

    protected open fun resetCoordinate() {}

    init {
        mGlVertexBuffer =
            ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        mGlVertexBuffer.clear()

        val VERTEXT = floatArrayOf(
            -1.0f, 1.0f,
            1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f
        )
        mGlVertexBuffer.put(VERTEXT)
        mGlTextureBuffer =
            ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        mGlTextureBuffer.clear()

        val TEXTURE = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        mGlTextureBuffer.put(TEXTURE)
        initilize(mContext)
        resetCoordinate()
    }
}