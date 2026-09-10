package com.example.myapplication.video

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ExtractionResult(
    val extractorName: String,
    val frames: List<Bitmap>,
    val durationMs: Long,
    val memoryUsedMB: Float
)

class FrameExtractorBenchmark(private val context: Context) {

    suspend fun benchmarkAll(videoPath: String): List<ExtractionResult> =
        withContext(Dispatchers.Default) {
            val results = mutableListOf<ExtractionResult>()

            // 测试1: MediaMetadataRetriever
            val mmResult = benchmark("MediaMetadataRetriever") {
                MediaMetadataRetrieverExtractor().use { extractor ->
                    extractor.extractFrames(videoPath)
                }
            }
            mmResult?.let { results.add(it) }

            // 测试2: FFmpeg
            val ffmpegResult = benchmark("FFmpeg") {
                FFmpegExtractor(context.cacheDir).use { extractor ->
                    extractor.extractFrames(videoPath)
                }
            }
            ffmpegResult?.let { results.add(it) }

            // 测试3: ExoPlayer
            val exoResult = benchmark("ExoPlayer") {
                ExoPlayerExtractor(context).use { extractor ->
                    extractor.extractFrames(videoPath)
                }
            }
            exoResult?.let { results.add(it) }

            results
        }

    private suspend fun benchmark(
        name: String,
        block: suspend () -> List<Bitmap>
    ): ExtractionResult? = withContext(Dispatchers.Default) {
        return@withContext try {
            val startTime = System.currentTimeMillis()
            val startMemory = Runtime.getRuntime().totalMemory()

            val frames = block()

            val duration = System.currentTimeMillis() - startTime
            val endMemory = Runtime.getRuntime().totalMemory()
            val memoryUsed = (endMemory - startMemory) / (1024f * 1024f)

            Log.d(
                "FrameExtractorBenchmark",
                "$name - 耗时: ${duration}ms, 内存: ${String.format("%.2f", memoryUsed)}MB, 帧数: ${frames.size}"
            )

            ExtractionResult(
                extractorName = name,
                frames = frames,
                durationMs = duration,
                memoryUsedMB = memoryUsed
            )
        } catch (e: Exception) {
            Log.e("FrameExtractorBenchmark", "$name 失败: ${e.message}", e)
            null
        }
    }
}

private inline fun <T : FrameExtractor> T.use(block: (T) -> Unit) {
    try {
        block(this)
    } finally {
        release()
    }
}
