## Fragment nav  
![UML用例图-导出2](https://github.com/YHCnb/camera2/assets/112797916/5cde095c-5f5e-4737-874d-2f45274d48f1)   
参照谷歌官方示例项目[Camera2Basic](https://github.com/googlearchive/android-Camera2Basic)，把Fragment简单地分为三块：**权限请求**、**相机主页**、**照片预览**。  
**TODO:可加入视频预览Fragment**  

## 画面处理流程  
![画面流程](https://github.com/YHCnb/camera2/assets/112797916/b8e91bc6-677c-4cf7-8bb3-6f3c86b8d412)  
通过多步处理得到最终画面：  
1. **摄像头->Camera**，通过previewRequestBuilder设置一些基础的相机模式:  
    + 对焦模式
    + 白平衡模式
    + 降噪算法
    + ...
2. **Camera->Surface**，Camera将初步处理后的画面传给目标surface:
    + previewSurface:与MyRenderer里的SurfaceTexture是同一个，由其调用openCamera函数时传递给CameraHelper，传相机画面给MyRenderer用于进一步预处理。
    + imageReader.surface:通过onPreviewListener传处理后的画面给FaceTracker，专门用来定位人脸。
3. **Sureface->Filter**，用openGL对画面进一步进行多样化处理：
    + cameraFilter:获取初始画面
    + screenFilter:将处理后的画面绘制到设备屏幕
    + 其他Filter:处理画面，完成美颜、大眼、贴纸、饱和度、对比对、亮度、曝光度等功能（可自主拓展）
3. **Filter->photo/video**，对预处理画面捕捉，直接得到预览同效果照片/视频：
    + PHOTO:直接捕捉一帧数据并通过处理，转换成照片并保存
    + VIDEO:通过AvcRecorder，控制其持续捕捉画面，录制成视频并保存

项目主要参考来源：[OpenGlCameraRender](https://github.com/wangchao0837/OpenGlCameraRender)、[Camera2Basic](https://github.com/googlearchive/android-Camera2Basic)

## 结构预览
![UML用例图-导出](https://github.com/YHCnb/camera2/assets/112797916/542b67e0-c713-409f-826b-3ff5d472f7d5)  
