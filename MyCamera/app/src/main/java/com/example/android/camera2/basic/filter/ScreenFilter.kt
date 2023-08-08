package com.example.android.camera2.basic.filter

import android.content.Context
import com.example.android.camera2.basic.R

/**
 * 屏幕Filter
 * 用于将处理后的纹理显示在屏幕上
 */
class ScreenFilter(mContext: Context?) : BaseFilter(mContext, R.raw.screen_vert, R.raw.screen_frag)