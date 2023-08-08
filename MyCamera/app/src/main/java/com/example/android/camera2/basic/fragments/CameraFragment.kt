/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.basic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.forEachIndexed
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camera2.basic.*
import com.example.android.camera2.basic.camera.CameraButton
import com.example.android.camera2.basic.camera.CameraHelper
import com.example.android.camera2.basic.camera.CameraMode
import com.example.android.camera2.basic.databinding.FragmentCameraBinding
import com.example.android.camera2.basic.openGL.GlRenderView
import com.example.android.camera2.basic.openGL.MyRenderer
import com.example.android.camera2.basic.util.LabelLib
import com.example.android.camera2.basic.util.OnRecordListener
import com.example.android.camera2.basic.util.OnShootListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraFragment :Fragment(){
    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** GlRenderView  */
    private lateinit var glRenderView: GlRenderView
    /** CameraHelper */
    private lateinit var cameraHelper: CameraHelper

    /** Performs recording animation of flashing screen 拍摄时动画效果 */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            fragmentCameraBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            fragmentCameraBinding.overlay.postDelayed({
                // Remove white flash animation
                fragmentCameraBinding.overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //初始化
        cameraHelper = CameraHelper(requireContext())
        glRenderView = fragmentCameraBinding.renderView!!

        val myRenderer = MyRenderer(glRenderView,cameraHelper)
        glRenderView.setRenderer(myRenderer)
        glRenderView.setOnRecordListener(
            object : OnRecordListener {
                // TODO 导航到视频预览
                override fun recordFinish(path: String?) {
                    Log.e(TAG, "Successfully save a video in path:$path")
                }
            }
        )
        glRenderView.setOnShootListener(
            object : OnShootListener{
                /** 拍摄完成导航到图片预览 */
                override fun shootFinish(path: String) {
                    Log.e(TAG, "Successfully save a photo in path:$path")
                    lifecycleScope.launch(Dispatchers.Main) {
                        navController.navigate(CameraFragmentDirections
                            .actionCameraToJpegViewer(path)
                            .setOrientation(0)
                            .setDepth(false)
                        )
                    }
                }
            }
        )

        //一些功能按钮
        fragmentCameraBinding.apply {
            beauty!!.setOnClickListener{
                glRenderView.enableBeauty(beauty.isChecked)
            }
            bigEye!!.setOnClickListener {
                glRenderView.enableBigEye(bigEye.isChecked)
            }
            stick!!.setOnClickListener {
                glRenderView.enableStick(stick.isChecked)
            }
            captureModeSwitch!!.setOnClickListener {
                if (captureModeSwitch.isChecked){
                    glRenderView.setCameraMode(CameraMode.VIDEO)
                    cameraButton!!.setCaptureMode(CameraMode.VIDEO)
                    captureModeSwitch.text = getString(R.string.video_mode)
                }else{
                    glRenderView.setCameraMode(CameraMode.PHOTO)
                    cameraButton!!.setCaptureMode(CameraMode.PHOTO)
                    captureModeSwitch.text = getString(R.string.photo_mode)
                }
            }
            exposureSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setExposure(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            saturationSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setSaturation(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            contrastSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setContrast(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            brightSeekBar!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        myRenderer.setBrightness(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
            rgSpeed!!.setOnCheckedChangeListener { _, checkedId ->
                rgSpeed.forEachIndexed { id, view ->
                    if (view.id == checkedId) {
                        glRenderView.setSpeed(GlRenderView.Speed.values()[id])
                    }
                }
            }
        }
        //切换相机白平衡模式按钮
        requireActivity().runOnUiThread{
            fragmentCameraBinding.switchButton!!.text = LabelLib.getAWBLabel(cameraHelper.getCurrentAWB())
        }
        fragmentCameraBinding.switchButton!!.setOnClickListener { v ->
            if (v.id == R.id.switch_button) {
                requireActivity().runOnUiThread{
                    cameraHelper.switchAWB()
                    fragmentCameraBinding.switchButton!!.text = LabelLib.getAWBLabel(cameraHelper.getCurrentAWB())
                }
            }
        }
        //拍摄按钮
        fragmentCameraBinding.cameraButton!!.setOnApplyWindowInsetsListener { v, insets ->
            v.translationX = (-insets.systemWindowInsetRight).toFloat()
            v.translationY = (-insets.systemWindowInsetBottom).toFloat()
            insets.consumeSystemWindowInsets()
        }
        //拍照Listener
        fragmentCameraBinding.cameraButton!!.setOnCapturedListener(
            object : CameraButton.OnCapturedListener {
                override fun onCapture() {
                    // 先禁止，防止短时间重复请求
                    fragmentCameraBinding.cameraButton!!.isEnabled = false

                    glRenderView.post(animationTask)
                    try {
                        val output = createFile(requireContext(),"jpg")
                        glRenderView.shoot(output.absolutePath)
                    }catch (e: NullPointerException){
                        e.printStackTrace()
                    }

                    fragmentCameraBinding.cameraButton!!.post { fragmentCameraBinding.cameraButton!!.isEnabled = true }
                }
            }
        )
        //录制Listener
        fragmentCameraBinding.cameraButton!!.setOnRecordListener(
            object : CameraButton.OnRecordListener{
                override fun onRecordStart() {
                    try {
                        val output = createFile(requireContext(),"mp4")
                        glRenderView.startRecord(output.absolutePath)
                    } catch (e: NullPointerException) {
                        e.printStackTrace()
                    }
                }

                override fun onRecordStop() {
                    glRenderView.stopRecord()
                }
            }
        )
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = "CameraFragment"

        /** 创建特定格式的file */
        private fun createFile(context: Context, extension: String): File {
            //保存在内部
            //val saveDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)!!.path

            //保存在手机DCIM中
            val root = Environment.getExternalStorageDirectory()
            val saveDir = File(root.absolutePath + "/DCIM")

            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA)
            return File(saveDir, "${sdf.format(Date())}.$extension")
        }
    }
}