package com.example.android.camera2.basic.openGL

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import com.example.android.camera2.basic.camera.CameraMode
import com.example.android.camera2.basic.util.OnRecordListener
import com.example.android.camera2.basic.util.OnShootListener
import kotlin.math.roundToInt

class GlRenderView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs) {
    private val TAG = "GlRenderView"
    private var mSpeed = Speed.MODE_NORMAL
    private var savePath: String? = null

    enum class Speed {
        MODE_EXTRA_SLOW, MODE_SLOW, MODE_NORMAL, MODE_FAST, MODE_EXTRA_FAST
    }

    private lateinit var glRender: MyRenderer

    private var aspectRatio = 0f

    init {
        //设置EGL 版本
        setEGLContextClientVersion(2)
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()
        holder.setFixedSize(width, height)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height)
        } else {

            // Performs center-crop transformation of the camera frames
            val newWidth: Int
            val newHeight: Int
            val actualRatio = if (width > height) aspectRatio else 1f / aspectRatio
            if (width < height * actualRatio) {
                newHeight = height
                newWidth = (height * actualRatio).roundToInt()
            } else {
                newWidth = width
                newHeight = (width / actualRatio).roundToInt()
            }

            Log.d(TAG, "Measured dimensions set: $newWidth x $newHeight")
            setMeasuredDimension(newWidth, newHeight)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        super.surfaceDestroyed(holder)
        glRender.onSurfaceDestroy()
    }

    fun shoot(path:String){
        glRender.shoot(path)
    }

    fun startRecord() {
        val speed: Float
        = when (mSpeed) {
            Speed.MODE_EXTRA_SLOW -> 0.3f
            Speed.MODE_SLOW -> 0.5f
            Speed.MODE_NORMAL -> 1f
            Speed.MODE_FAST -> 1.5f
            Speed.MODE_EXTRA_FAST -> 3f
        }
        glRender.startRecord(speed, savePath)
    }

    fun stopRecord() {
        glRender.stopRecord()
    }

    fun enableStick(isChecked: Boolean) {
        queueEvent { glRender.enableStick(isChecked) }
    }

    fun enableBigEye(isChecked: Boolean) {
        queueEvent { glRender.enableBigEye(isChecked) }
    }

    fun enableBeauty(isChecked: Boolean) {
        queueEvent { glRender.enableBeauty(isChecked) }
    }

    override fun setRenderer(renderer: Renderer?) {
        super.setRenderer(renderer)
        glRender = renderer as MyRenderer
        renderMode = RENDERMODE_WHEN_DIRTY //手动渲染模式
    }

    fun setCameraMode(mode: CameraMode){
        glRender.setCameraMode(mode)
    }

    fun setSaturation(saturation:Int){
        glRender.setSaturation(saturation)
    }

    fun setContrast(contrast:Int){
        glRender.setContrast(contrast)
    }

    fun setExposure(exposure:Int){
        glRender.setExposure(exposure)
    }

    fun setBrightness(brightness:Int){
        glRender.setBrightness(brightness)
    }

    fun setSpeed(speed: Speed) {
        mSpeed = speed
    }

    fun setSavePath(savePath: String?) {
        this.savePath = savePath
    }

    fun setOnShootListener(onShootListener: OnShootListener?) {
        if(!this::glRender.isInitialized){
            Log.d(TAG, "glRender has not been Initialized")
            return
        }
        glRender.setOnShootListener(onShootListener)
    }

    fun setOnRecordListener(onRecordListener: OnRecordListener?) {
        if(!this::glRender.isInitialized){
            Log.d(TAG, "glRender has not been Initialized")
            return
        }
        glRender.setOnRecordListener(onRecordListener)
    }
}