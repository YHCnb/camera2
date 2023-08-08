package com.example.android.camera2.basic.util

import android.content.Context
import android.opengl.GLES20
import java.io.*

object OpenGlUtils {
    fun readRawShaderFile(context: Context, shareId: Int): String {
        val `is` = context.resources.openRawResource(shareId)
        val br = BufferedReader(InputStreamReader(`is`))
        var line: String?
        val sb = StringBuffer()
        try {
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
                sb.append("\n")
            }
            br.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return sb.toString()
    }

    fun loadProgram(mVertexShader: String?, mFragShader: String?): Int {
        val vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vshader, mVertexShader)
        GLES20.glCompileShader(vshader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "load vertex raw error :" + GLES20.glGetShaderInfoLog(
                vshader
            )
        }
        val fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fshader, mFragShader)
        GLES20.glCompileShader(fshader)
        GLES20.glGetShaderiv(fshader, GLES20.GL_SHADER_COMPILER, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "load fragment raw error :" + GLES20.glGetShaderInfoLog(
                fshader
            )
        }
        val programeId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programeId, vshader)
        GLES20.glAttachShader(programeId, fshader)
        GLES20.glLinkProgram(programeId)
        GLES20.glGetProgramiv(programeId, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "link program:" + GLES20.glGetProgramInfoLog(programeId) }
        GLES20.glDeleteShader(vshader)
        GLES20.glDeleteShader(fshader)
        return programeId
    }

    fun glGenTextures(textures: IntArray) {
        GLES20.glGenTextures(textures.size, textures, 0)
        for (texture in textures) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_NEAREST
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }

    fun copyAssets2SdCard(context: Context, src: String?, dst: String?) {
        try {
            val file = File(dst)
            if (!file.exists()) {
                val `is` = context.assets.open(src!!)
                val fos = FileOutputStream(file)
                var len: Int
                val buffer = ByteArray(2048)
                while (`is`.read(buffer).also { len = it } != -1) {
                    fos.write(buffer, 0, len)
                }
                `is`.close()
                fos.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}