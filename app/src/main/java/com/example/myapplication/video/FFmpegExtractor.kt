package com.example.myapplication.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FFmpegExtractor(private val cacheDir: File) : FrameExtractor {

    override suspend fun extractFrames(videoPath: String): List<Bitmap> =
        withContext(Dispatchers.Default) {
            val frames = mutableListOf<Bitmap>()
            val outputPattern = "${cacheDir.absolutePath}/frame_%04d.jpg"

            // FFmpeg命令：每1秒提取一帧
            val cmd = arrayOf(
                "-i",
                videoPath,
                "-vf",
                "fps=1",  // 1秒一帧
                "-q:v",
                "2",  // 质量 (1-5, 数字越小质量越好)
                outputPattern
            )

            val returnCode = FFmpeg.execute(cmd)

            if (returnCode == 0) {
                val frameDir = cacheDir
                frameDir.listFiles { file ->
                    file.name.matches(Regex("frame_\\d{4}\\.jpg"))
                }?.sortedBy { it.name }?.forEach { file ->
                    try {
                        BitmapFactory.decodeFile(file.absolutePath)?.let {
                            frames.add(it)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            frames
        }

    override suspend fun getFrameAt(videoPath: String, timeMs: Long): Bitmap? =
        withContext(Dispatchers.Default) {
            val frameFile = File(cacheDir, "frame_temp.jpg")
            val timeStr = convertMsToTimeFormat(timeMs)

            val cmd = arrayOf(
                "-i",
                videoPath,
                "-ss",
                timeStr,
                "-vframes",
                "1",
                "-q:v",
                "2",
                frameFile.absolutePath
            )

            val returnCode = FFmpeg.execute(cmd)

            if (returnCode == 0 && frameFile.exists()) {
                try {
                    BitmapFactory.decodeFile(frameFile.absolutePath).also {
                        frameFile.delete()
                    }
                } catch (e: Exception) {
                    frameFile.delete()
                    null
                }
            } else {
                null
            }
        }

    override fun release() {
        // FFmpeg无需特殊释放
        cleanCacheDir()
    }

    override fun getName(): String = "FFmpeg"

    private fun convertMsToTimeFormat(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun cleanCacheDir() {
        try {
            cacheDir.listFiles { file ->
                file.name.startsWith("frame_")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

