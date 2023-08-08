package com.example.android.camera2.basic.record

import android.content.Context
import android.opengl.*
import android.view.Surface
import com.example.android.camera2.basic.filter.ScreenFilter

class EglConfigBase(
    context: Context?,
    width: Int,
    height: Int,
    surface: Surface?,
    eglContext: EGLContext
) {
    private var eglDisplay: EGLDisplay? = null
    private var mEglConfig: EGLConfig? = null
    private var mCurrentEglContext: EGLContext? = null
    private val eglSurface: EGLSurface
    private val screenFilter: ScreenFilter
    private fun createEGLContext(eglContext: EGLContext) {
        //创建虚拟屏幕
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }
        val versions = IntArray(2)
        //初始化elgdisplay
        if (!EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            throw RuntimeException("eglInitialize failed")
        }
        val attr_list = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num_configs = IntArray(1)

        //配置eglDisplay 属性
        require(
            EGL14.eglChooseConfig(
                eglDisplay, attr_list, 0,
                configs, 0, configs.size,
                num_configs, 0
            )
        ) { "eglChooseConfig#2 failed" }
        mEglConfig = configs[0]
        val ctx_attrib_list = intArrayOf( //TODO
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        //创建EGL 上下文
        mCurrentEglContext =
            EGL14.eglCreateContext(eglDisplay, mEglConfig, eglContext, ctx_attrib_list, 0)
        if (mCurrentEglContext === EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("EGL Context Error.")
        }
    }

    fun draw(textureId: Int, timestamp: Long) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, mCurrentEglContext)) {
            throw RuntimeException("eglMakeCurrent 失败！")
        }
        screenFilter.onDrawFrame(textureId)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, timestamp)
        //交换数据，输出到mediacodec InputSurface中
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, mCurrentEglContext)
        EGL14.eglDestroyContext(eglDisplay, mCurrentEglContext)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(eglDisplay)
    }

    init {
        createEGLContext(eglContext)
        val attrib_list = intArrayOf(
            EGL14.EGL_NONE
        )

        //创建EGLSurface
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, mEglConfig, surface, attrib_list, 0)
        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface 失败！")
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, mCurrentEglContext)) {
            throw RuntimeException("eglMakeCurrent 失败！")
        }
        screenFilter = ScreenFilter(context)
        screenFilter.prepare(width, height, 0, 0)
    }
}