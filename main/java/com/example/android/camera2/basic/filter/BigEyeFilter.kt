package com.example.android.camera2.basic.filter

import android.content.Context
import android.opengl.GLES20
import com.example.android.camera2.basic.R
import com.example.android.camera2.basic.face.Face
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 大眼Filter
 */
class BigEyeFilter(mContext: Context?) :
    AbstractFBOFilter(mContext, R.raw.screen_vert, R.raw.bigeye_frag) {
    private val left_eye: Int
    private val right_eye: Int
    private val left: FloatBuffer
    private val right: FloatBuffer
    private var mFace: Face? = null
    fun setFace(face: Face?) {
        mFace = face
    }

    override fun onDrawFrame(textureId: Int): Int {
        if (mFace == null) return textureId
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![0])
        GLES20.glUseProgram(mProgramId)
        mGlVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, mGlVertexBuffer)
        GLES20.glEnableVertexAttribArray(vPosition)
        mGlTextureBuffer.position(0)
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, mGlTextureBuffer)
        GLES20.glEnableVertexAttribArray(vCoord)
        /**
         * 传递眼睛的坐标 给GLSL
         */
        val landmarks = mFace!!.landmarks
        //左眼的x 、y  opengl : 0-1
        var x = landmarks[2] / mFace!!.imgWidth
        var y = landmarks[3] / mFace!!.imgHeight
        left.clear()
        left.put(x)
        left.put(y)
        left.position(0)
        GLES20.glUniform2fv(left_eye, 1, left)

        //右眼的x、y
        x = landmarks[4] / mFace!!.imgWidth
        y = landmarks[5] / mFace!!.imgHeight
        right.clear()
        right.put(x)
        right.put(y)
        right.position(0)
        GLES20.glUniform2fv(right_eye, 1, right)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(vTexture, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return mFBOTextures!![0]
    }

    init {
        left_eye = GLES20.glGetUniformLocation(mProgramId, "left_eye")
        right_eye = GLES20.glGetUniformLocation(mProgramId, "right_eye")
        left = ByteBuffer.allocateDirect(2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        right = ByteBuffer.allocateDirect(2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}