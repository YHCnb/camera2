package com.example.android.camera2.basic.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.storage.StorageManager
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.*
import java.lang.reflect.Array

/**
 * 文件辅助工具，提供注视点标定数据的保存、读取和BMP文件流的封装。
 */
object FileUtil {
    private val TAG = FileUtil::class.java.simpleName

    /**
     * 创建内部私有目录
     *
     * @param context
     * @param dirPathName
     * @return
     */
    fun createPrivateDir(context: Context, dirPathName: String?): File {
        val dir = context.filesDir
        val privateDir = File(dir, dirPathName)
        if (privateDir.exists()) {
            if (!privateDir.isDirectory) {
                privateDir.delete()
            }
        }
        privateDir.mkdirs()
        return privateDir
    }

    /**
     * 在外部存储空间或内部私有空间创建文件夹
     *
     * @param context
     * @param dirName 目录名
     * @return 创建的文件夹
     * @throws NoExternalStoragePermissionException 未能获取外部存储卡读写权限异常
     * @throws NoExternalStorageMountedException    外部存储卡未挂载异常
     */
    @Throws(NoExternalStoragePermissionException::class, NoExternalStorageMountedException::class)
    fun createExternalDir(context: Context, dirName: String?): File {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            var absolutePath: String? = null
            absolutePath = getExternalPath(context)
            val dir = File(absolutePath, dirName)
            if (dir.exists()) {
                if (!dir.isDirectory) {
                    dir.delete()
                }
            }
            val mkdirs = dir.mkdirs()
            dir
        } else {
            throw NoExternalStoragePermissionException("无法获取对外部存储空间的读写权限，请在Activity中加入获取权限的代码并在AndroidManifest中声明权限")
        }
    }

    fun getExternalPath(context: Context): String? {
        var absolutePath: String? = null
        val externalFilesDirs = context.getExternalFilesDirs("")
        if (externalFilesDirs.size > 1) {
            absolutePath = externalFilesDirs[1].absolutePath
        }

//        absolutePath = getStoragePath(context, true);
        if (TextUtils.isEmpty(absolutePath)) {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                absolutePath = Environment.getExternalStorageDirectory().absolutePath
            } else {
                try {
                    throw NoExternalStorageMountedException("外部存储卡未挂载")
                } catch (e: NoExternalStorageMountedException) {
                    e.printStackTrace()
                }
            }
        }
        return absolutePath
    }

    /**
     * 在外部存储空间或内部私有空间新建指定文件夹中的指定文件
     *
     * @param context
     * @param dirPath       目录路径名称
     * @param fileName      文件名
     * @param needFreeSpace 需要的空间空间，以字节计
     * @return 创建的文件
     * @throws NoExternalStoragePermissionException 未能获取外部存储卡读写权限异常
     * @throws NoExternalStorageMountedException    外部存储卡未挂载异常
     * @throws DirHasNoFreeSpaceException           目录缺少足够的存储空间异常
     * @throws IOException                          文件读写IO异常
     */
    @Throws(
        NoExternalStoragePermissionException::class,
        NoExternalStorageMountedException::class,
        DirHasNoFreeSpaceException::class,
        IOException::class
    )
    fun createFile(
        context: Context,
        isPrivate: Boolean,
        dirPath: String?,
        fileName: String?,
        needFreeSpace: Long
    ): File {
        var dir: File? = null
        dir = if (isPrivate) {
            createPrivateDir(context, dirPath)
        } else {
            createExternalDir(context, dirPath)
        }
        val freeSpace = dir.freeSpace
        Log.e(TAG, "freeSpace:$freeSpace")
        //需要保留50MB空间
//        if (freeSpace > needFreeSpace) {
        val targetFile = File(dir, fileName)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        Log.e(TAG, "targetFile:" + targetFile.absolutePath)
        targetFile.createNewFile()
        Log.e(TAG, "createFile:" + targetFile.absolutePath)
        return targetFile
        //        } else {
//            throw new DirHasNoFreeSpaceException("目录" + dir.getAbsolutePath() + "缺少足够的空间以保存文件");
//        }
    }

    /**
     * 通过反射调用获取内置存储和外置sd卡根路径(通用)
     *
     * @param mContext    上下文
     * @param is_removale 是否可移除，false返回内部存储路径，true返回外置SD卡路径
     * @return
     */
    fun getStoragePath(mContext: Context, is_removale: Boolean): String? {
        var path = ""
        //使用getSystemService(String)检索一个StorageManager用于访问系统存储功能。
        val mStorageManager = mContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        var storageVolumeClazz: Class<*>? = null
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
            val getPath = storageVolumeClazz.getMethod("getPath")
            val isRemovable = storageVolumeClazz.getMethod("isRemovable")
            val result = getVolumeList.invoke(mStorageManager)
            for (i in 0 until Array.getLength(result)) {
                val storageVolumeElement = Array.get(result, i)
                path = getPath.invoke(storageVolumeElement) as String
                val removable = isRemovable.invoke(storageVolumeElement) as Boolean
                if (is_removale == removable) {
                    return path
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return path
    }

    /**
     * 将输入流写入到指定文件
     *
     * @param context
     * @param isPrivate      是否私有
     * @param dirPath        目录路径
     * @param targetFileName 目标文件名
     * @param inputStream    输入流
     * @return 写入完成的文件
     * @throws IOException                          文件读写IO异常
     * @throws NoExternalStoragePermissionException 未能获取外部存储卡读写权限异常
     * @throws NoExternalStorageMountedException    外部存储卡未挂载异常
     * @throws DirHasNoFreeSpaceException           目录缺少足够的存储空间异常
     */
    @Throws(
        IOException::class,
        NoExternalStoragePermissionException::class,
        DirHasNoFreeSpaceException::class,
        NoExternalStorageMountedException::class
    )
    private fun writeInputStreamToFile(
        context: Context,
        isPrivate: Boolean,
        dirPath: String,
        targetFileName: String,
        inputStream: InputStream
    ): File {
        val targetFile = createFile(
            context,
            isPrivate,
            dirPath,
            targetFileName,
            inputStream.available().toLong()
        )
        val buffer = ByteArray(2048)
        var byteCount = 0
        val out = FileOutputStream(targetFile)
        while (inputStream.read(buffer, 0, buffer.size).also { byteCount = it } != -1) {
            out.write(buffer, 0, byteCount)
        }
        out.flush()
        out.close()
        inputStream.close()
        return targetFile
    }

    /**
     * 写入字节流到指定目录的指定文件
     *
     * @param context
     * @param isPrivate      是否私有
     * @param dirPath        目录相对当前类型的根目录路径
     * @param targetFileName 目标文件名
     * @return 写入完成的文件
     * @throws NoExternalStoragePermissionException 未能获取外部存储卡读写权限异常
     * @throws NoExternalStorageMountedException    外部存储卡未挂载异常
     * @throws DirHasNoFreeSpaceException           目录缺少足够的存储空间异常
     * @throws IOException                          文件读写IO异常
     */
    @Throws(
        NoExternalStoragePermissionException::class,
        DirHasNoFreeSpaceException::class,
        NoExternalStorageMountedException::class,
        IOException::class
    )
    fun writeByteDataToTargetFile(
        context: Context,
        data: ByteArray?,
        isPrivate: Boolean,
        dirPath: String,
        targetFileName: String
    ): File {
        val inputStream: InputStream = ByteArrayInputStream(data)
        return writeInputStreamToFile(context, isPrivate, dirPath, targetFileName, inputStream)
    }

    /**
     * 读取指定目录的指定文件到字节数组
     *
     * @param context
     * @param isPrivate      是否私有
     * @param dirPath        目录相对当前类型的根目录路径
     * @param targetFileName 目标文件名
     * @return 读取的字节数组
     * @throws IOException           IO异常
     * @throws FileNotFoundException 目标文件未找到
     */
    @Throws(
        IOException::class,
        NoExternalStoragePermissionException::class,
        NoExternalStorageMountedException::class
    )
    fun readByteDateFromTargetFile(
        context: Context,
        isPrivate: Boolean,
        dirPath: String?,
        targetFileName: String?
    ): ByteArray {
        var dir: File? = null
        dir = if (isPrivate) {
            createPrivateDir(context, dirPath)
        } else {
            createExternalDir(context, dirPath)
        }
        val file = File(dir, targetFileName)
        return if (file.exists()) {
            val inputStream = FileInputStream(file)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            var byteCount = 0
            while (inputStream.read(buffer, 0, buffer.size).also { byteCount = it } != -1) {
                out.write(buffer, 0, byteCount)
            }
            out.flush()
            out.close()
            inputStream.close()
            out.toByteArray()
        } else {
            throw FileNotFoundException("未找到指定文件")
        }
    }

    @Throws(NoExternalStorageMountedException::class, IOException::class)
    fun readSdcardFile(fileName: String): String {
        //如果手机插入了SD卡，而且应用程序具有访问SD的权限
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            //获取SD卡对应的存储目录
            val sdCardDir = Environment.getExternalStorageDirectory()
            //获取指定文件对应的输入流
            val file = File(sdCardDir, fileName)
            if (file.exists()) {
                val fis = FileInputStream(sdCardDir.canonicalPath + "/" + fileName)
                //将指定输入流包装成BufferedReader
                val br = BufferedReader(InputStreamReader(fis))
                val sb = StringBuilder("")
                var line: String? = null
                while (br.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                sb.toString()
            } else {
                throw FileNotFoundException("未在存储卡根目录找到7invensun许可文件,即将退出应用")
            }
        } else {
            throw NoExternalStorageMountedException("外部存储卡未挂载")
        }
    }

    //    /**
    //     * 删除已存储的文件
    //     */
    //    public static boolean deletefile(String fileName) {
    //        try {
    //            // 找到文件所在的路径并删除该文件
    //            Log.e("AAAAA", "fileName:" + fileName);
    //            File file = new File(Environment.getExternalStorageDirectory(), fileName);
    //            if (!file.exists()) {
    //                Log.e(TAG, "FileNotFound when delete VideoInfoFile");
    //                Log.e("AAAAA", "1111111");
    //                return false;
    //            }
    //            file.delete();
    //            Log.e("AAAAA", "2222");
    //            return true;
    //        } catch (Exception e) {
    //            Log.e("AAAAA", "33333");
    //            Log.e("AAAA", e.getMessage().toLowerCase().toString());
    //            e.printStackTrace();
    //            return false;
    //        }
    //    }
    //删除文件夹和文件夹里面的文件
    fun deletefile(mContext: Context, fileName: String): Boolean {
        Log.e(TAG, "fileName:$fileName")
        val dir = File(getExternalPath(mContext), fileName)
        return deleteDirWihtFile(dir)
    }

    fun deleteDirWihtFile(dir: File?): Boolean {
        Log.e(TAG, "dir:$dir")
        Log.e(TAG, "dir.exists():" + dir!!.exists())
        Log.e(TAG, "dir.isDirectory():" + dir.isDirectory)
        if (dir == null || !dir.exists() || !dir.isDirectory) return false
        for (file in dir.listFiles()) {
            if (file.isFile) file.delete() // 删除所有文件
            else if (file.isDirectory) deleteDirWihtFile(file) // 递规的方式删除文件夹
        }
        dir.delete() // 删除目录本身
        return true
    }

    /**
     * 无外部存储卡访问权限
     */
    class NoExternalStoragePermissionException(message: String?) : Exception(message)

    /**
     * 无存储卡挂载异常
     */
    class NoExternalStorageMountedException(message: String?) : Exception(message)

    /**
     * 文件夹无足够空闲空间异常
     */
    class DirHasNoFreeSpaceException(message: String?) : Exception(message)
}