# 视频封面选择 - 三方案对比指南

## 📊 方案概览

### 方案1️⃣: MediaMetadataRetriever
**优点:**
- ✅ Android原生API，无需额外依赖
- ✅ 体积小，集成简单
- ✅ 支持大多数视频格式
- ✅ 低开销

**缺点:**
- ❌ 只支持关键帧提取，可能跳帧
- ❌ 性能一般，大视频时较慢
- ❌ 精度不高，无法任意位置取帧

**适用场景:** 小视频，对精度要求不高，想快速集成

---

### 方案2️⃣: FFmpeg
**优点:**
- ✅ 任意位置提取任意帧，精度高
- ✅ 性能最佳，速度快
- ✅ 支持格式广泛
- ✅ 可高度定制参数

**缺点:**
- ❌ 包体积大(~50MB for so文件)
- ❌ 集成复杂，需要配置so库
- ❌ 内存占用较多
- ❌ 初次加载较慢

**适用场景:** 对性能要求高，追求最佳体验，视频较长

---

### 方案3️⃣: ExoPlayer
**优点:**
- ✅ Google官方维护，稳定可靠
- ✅ 支持流媒体播放
- ✅ 性能稳定
- ✅ 与播放器无缝集成

**缺点:**
- ❌ 依赖库较大
- ❌ 帧提取不如FFmpeg直接
- ❌ 学习曲线陡峭
- ❌ 内存占用中等偏高

**适用场景:** 已使用ExoPlayer播放器，需要统一方案

---

## 📈 性能对比(参考)

| 指标 | MediaMetadataRetriever | FFmpeg | ExoPlayer |
|------|------------------------|--------|-----------|
| **速度** | 中 | 🏆快 | 中 |
| **内存** | 低 | 中 | 中 |
| **体积** | 小 | 大 | 中等 |
| **精度** | 低 | 🏆高 | 中等 |
| **集成复杂度** | 简单 | 复杂 | 中等 |
| **格式支持** | 中等 | 🏆广泛 | 广泛 |

---

## 🚀 快速开始

### 选择VideoSelectorActivity查看对比

```kotlin
// 在需要的地方跳转
val intent = Intent(this, VideoSelectorActivity::class.java)
startActivity(intent)
```

### 集成单个方案

#### 使用MediaMetadataRetriever
```kotlin
val extractor = MediaMetadataRetrieverExtractor()
val frames = extractor.extractFrames(videoPath)
val coverBitmap = frames.first()
extractor.release()
```

#### 使用FFmpeg
```kotlin
val extractor = FFmpegExtractor(context.cacheDir)
val frames = extractor.extractFrames(videoPath)
val coverBitmap = frames[selectedIndex]
extractor.release()
```

#### 使用ExoPlayer
```kotlin
val extractor = ExoPlayerExtractor(context)
val frames = extractor.extractFrames(videoPath)
val coverBitmap = frames[selectedIndex]
extractor.release()
```

---

## 💡 推荐方案选择

### 🎯 我需要最快的方案
→ **FFmpeg** (最快的帧提取速度)

### 🎯 我是小团队，想快速集成
→ **MediaMetadataRetriever** (原生API，0依赖)

### 🎯 我已经用了ExoPlayer播放器
→ **ExoPlayer** (代码统一)

### 🎯 我想要最佳体验+功能完整
→ **FFmpeg** (精度高，性能好，功能强)

---

## 🔧 技术细节

### MediaMetadataRetriever
```kotlin
val retriever = MediaMetadataRetriever()
retriever.setDataSource(videoPath)
// 每1000ms(1秒)取一帧
for (i in 0..duration step 1000) {
    val bitmap = retriever.getFrameAtTime(
        i * 1000,
        MediaMetadataRetriever.OPTION_CLOSEST  // 取最接近的关键帧
    )
}
```

### FFmpeg
```kotlin
// 使用fps=1提取每秒一帧
val cmd = arrayOf(
    "-i", videoPath,
    "-vf", "fps=1",          // 每秒1帧
    "-q:v", "2",             // 质量(1-5)
    "frame_%04d.jpg"
)
FFmpeg.execute(cmd)
```

### ExoPlayer
```kotlin
// 使用VideoProcessor接口接收视频帧
// 需要与播放器实例配合使用
// 较为复杂，建议结合播放器使用
```

---

## 📱 测试视频推荐

- **小视频**: <10MB, <2分钟 → 三方案都可
- **中等视频**: 10-100MB, 2-10分钟 → FFmpeg优势显著
- **大视频**: >100MB, >10分钟 → 必选FFmpeg

---

## ⚠️ 注意事项

1. **权限**: 需要 `READ_EXTERNAL_STORAGE`
2. **内存**: 处理大视频时注意内存使用，建议分批加载
3. **线程**: 所有提取操作都在后台线程执行(Coroutine)
4. **缓存**: FFmpeg会在cacheDir生成临时文件，记得清理
5. **格式支持**: 优先测试目标视频格式兼容性

---

## 📝 实现参考

完整代码已包含在以下文件中:
- `FrameExtractor.kt` - 接口定义
- `MediaMetadataRetrieverExtractor.kt` - 方案1实现
- `FFmpegExtractor.kt` - 方案2实现  
- `ExoPlayerExtractor.kt` - 方案3实现
- `VideoSelectorActivity.kt` - UI展示和对比
- `FrameExtractorBenchmark.kt` - 基准测试工具
