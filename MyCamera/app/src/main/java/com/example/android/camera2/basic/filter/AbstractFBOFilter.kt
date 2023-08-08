package com.example.android.camera2.basic.filter

import android.content.Context
import android.opengl.GLES20
import com.example.android.camera2.basic.util.OpenGlUtils

open class AbstractFBOFilter(mContext: Context?, mVertexShaderId: Int, mFragShaderId: Int) :
    BaseFilter(mContext, mVertexShaderId, mFragShaderId) {
    protected var mFrameBuffers: IntArray? = null
    protected var mFBOTextures: IntArray? = null
    override fun resetCoordinate() {
        mGlTextureBuffer.clear()
        val TEXTURE = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )
        mGlTextureBuffer.put(TEXTURE)
    }

    override fun prepare(width: Int, height: Int, x: Int, y: Int) {
        super.prepare(width, height, x, y)
        loadFOB()
    }

    private fun loadFOB() {
        if (mFrameBuffers != null) {
            destroyFrameBuffers()
        }
        //创建FrameBuffer
        mFrameBuffers = IntArray(1)
        GLES20.glGenFramebuffers(mFrameBuffers!!.size, mFrameBuffers, 0)
        //穿件FBO中的纹理
        mFBOTextures = IntArray(1)
        OpenGlUtils.glGenTextures(mFBOTextures!!)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTextures!![0])
        //指定FBO纹理的输出图像的格式 RGBA
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mOutputWidth, mOutputHeight,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![0])

        //将fbo绑定到2d的纹理上
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, mFBOTextures!![0], 0
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun destroyFrameBuffers() {
        //删除fbo的纹理
        if (mFBOTextures != null) {
            GLES20.glDeleteTextures(1, mFBOTextures, 0)
            mFBOTextures = null
        }
        //删除fbo
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0)
            mFrameBuffers = null
        }
    }
}