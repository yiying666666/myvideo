# 快速集成指南

## ⚡ 5分钟快速开始

### Step 1: 添加依赖

你的 `build.gradle` 已更新，包含:
```gradle
implementation 'com.google.android.exoplayer:exoplayer-core:2.19.1'
implementation 'com.arthenica:mobile-ffmpeg-full:4.4.LTS'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'
```

### Step 2: 在Activity中跳转到演示

```kotlin
// 在你的Activity中
val intent = Intent(this, VideoSelectorActivity::class.java)
startActivity(intent)
```

### Step 3: 运行并测试

点击"选择视频"按钮选择一个视频，等待三个方案完成提取，查看对比数据。

---

## 🎯 根据场景选择方案

### 使用场景: 仿剪映视频封面选择

建议方案: **FFmpeg** ✓

理由:
- 任意位置提取帧(精度最高)
- 性能最佳(用户体验最好)
- 支持长视频无压力

---

## 📦 集成单个方案到你的业务代码

### 选项A: MediaMetadataRetriever (推荐新手)

```kotlin
class VideoCoverSelector {
    private val extractor = MediaMetadataRetrieverExtractor()
    
    suspend fun selectCover(videoPath: String, position: Int): Bitmap? {
        val frames = extractor.extractFrames(videoPath)
        return frames.getOrNull(position)
    }
}
```

**优点**: 一个文件，无额外依赖
**缺点**: 精度不如其他方案

---

### 选项B: FFmpeg (推荐生产环境)

```kotlin
class VideoCoverSelector(context: Context) {
    private val extractor = FFmpegExtractor(context.cacheDir)
    
    suspend fun selectCover(videoPath: String, position: Int): Bitmap? {
        val frames = extractor.extractFrames(videoPath)
        return frames.getOrNull(position)
    }
}
```

**优点**: 最快最精确，支持所有格式
**缺点**: 包体积增加50MB

---

## 🔄 集成UI到你的Fragment

### 简单示例

```xml
<!-- 在你的layout中 -->
<com.example.myapplication.video.VideoFrameSelectorView
    android:id="@+id/frameSelectorView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
// 在Fragment/Activity中
lifecycleScope.launch {
    val extractor = FFmpegExtractor(context.cacheDir)
    val frames = extractor.extractFrames(videoPath)
    
    val selectorView = findViewById<VideoFrameSelectorView>(R.id.frameSelectorView)
    selectorView.setFrames(frames, "FFmpeg")
    selectorView.setOnCoverSelected { bitmap, timeMs ->
        // 用户选择了这个帧作为封面
        saveCoverImage(bitmap)
        updateVideoInfo(timeMs)
    }
}
```

---

## 🛠️ 自定义集成

如果你的UI不同，可以只使用提取器:

```kotlin
// 仅提取关键帧，其他UI交给你自己
val extractor = MediaMetadataRetrieverExtractor()
val frames = extractor.extractFrames(videoPath)

// 现在frames就是所有帧的列表
// 你可以自己用RecyclerView展示
// 用ViewPager切换预览等
```

---

## ⚠️ 常见问题

### Q: 我需要所有三个方案吗?
**A**: 不需要。选一个最适合的就行。可以在FrameExtractor接口下随时切换。

### Q: 如何处理超大视频(>500MB)?
**A**: 建议分段提取。比如只提取前30秒的帧：
```kotlin
// 修改时间步长为5秒一帧
for (i in 0..30000 step 5000) { // 只到30秒
    // 提取帧
}
```

### Q: FFmpeg包太大了?
**A**: 可以使用轻量版本:
```gradle
// 替换为轻量版本
implementation 'com.arthenica:mobile-ffmpeg-min:4.4.LTS'
```

### Q: 如何保存选中的图片?
**A**: 
```kotlin
val bitmap = selectedBitmap
val file = File(context.cacheDir, "cover.jpg")
bitmap.compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
```

---

## 📊 性能预期

**测试条件**: 10分钟 MP4视频，1秒一帧（共600帧）

| 方案 | 耗时 | 内存 | 首帧时间 |
|------|------|------|---------|
| MediaMetadataRetriever | 8-12s | 50-80MB | 快 |
| FFmpeg | 2-4s | 100-150MB | 快 |
| ExoPlayer | 5-8s | 80-120MB | 中等 |

*实际数据会因设备和视频而异*

---

## 🚀 下一步

1. 运行 `VideoSelectorActivity` 测试三个方案
2. 根据实际性能数据选择最适合的
3. 集成到你的业务逻辑中
4. 处理边界情况(旋转视频、特殊格式等)

---

## 📞 需要帮助?

查看完整指南: `VIDEO_FRAME_SELECTOR_GUIDE.md`
