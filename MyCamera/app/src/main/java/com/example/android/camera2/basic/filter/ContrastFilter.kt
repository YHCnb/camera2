package com.example.android.camera2.basic.filter

import android.content.Context
import android.opengl.GLES20
import com.example.android.camera2.basic.R

/**
 * 对比度Filter
 * 传入对比度值 0~100，内部转化范围 0f~2f
 */
class ContrastFilter(mContext: Context?) :
    AbstractFBOFilter(mContext, R.raw.screen_vert, R.raw.contrast_frag) {
    private val contrastLoc: Int
    private var contrast = 1.0f
    fun setContrast(contrast: Int) {
        this.contrast = contrast * 0.02f
    }

    override fun onDrawFrame(textureId: Int): Int {
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![0])
        GLES20.glUseProgram(mProgramId)
        mGlVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, mGlVertexBuffer)
        GLES20.glEnableVertexAttribArray(vPosition)
        mGlTextureBuffer.position(0)
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, mGlTextureBuffer)
        GLES20.glEnableVertexAttribArray(vCoord)


        //传递对比度
        GLES20.glUniform1f(contrastLoc, contrast)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(vTexture, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return mFBOTextures!![0]
    }

    init {
        contrastLoc = GLES20.glGetUniformLocation(mProgramId, "contrast")
    }
}