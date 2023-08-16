## Fragment nav  
![UML用例图-导出](https://github.com/YHCnb/camera2/assets/112797916/ba0c59db-db48-49dc-b6eb-0451c4cbef33)
参照谷歌官方示例项目[Camera2Basic](https://github.com/googlearchive/android-Camera2Basic)，把Fragment简单地分为三块：**权限请求**、**相机主页**、**照片预览**。  
**TODO:可加入视频预览Fragment**  

## 画面处理流程  
![未命名文件-导出3](https://github.com/YHCnb/camera2/assets/112797916/09ca6650-1f70-4108-b69b-b57dc614cae4)

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

## TODO_LIST
+ **加入视频预览Fragment**
+ **预览尺寸未与智慧屏适配，仅固定4:3**
+ **人脸识别存在问题，导致BigEye和Sticker无法正常运行**
+ **UI设计**

## 结构预览
![UML用例图-导出2](https://github.com/YHCnb/camera2/assets/112797916/e786e855-e0c7-41b7-b70b-8daad5744ff8)

