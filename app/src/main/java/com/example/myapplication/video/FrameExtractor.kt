package com.example.myapplication.video

import android.graphics.Bitmap

interface FrameExtractor {
    suspend fun extractFrames(videoPath: String): List<Bitmap>
    suspend fun getFrameAt(videoPath: String, timeMs: Long): Bitmap?
    fun release()
    fun getName(): String
}
