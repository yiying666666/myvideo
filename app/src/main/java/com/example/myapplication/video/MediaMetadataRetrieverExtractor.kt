package com.example.myapplication.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaMetadataRetrieverExtractor : FrameExtractor {
    private var retriever: MediaMetadataRetriever? = null

    override suspend fun extractFrames(videoPath: String): List<Bitmap> =
        withContext(Dispatchers.Default) {
            val frames = mutableListOf<Bitmap>()
            retriever = MediaMetadataRetriever().apply {
                setDataSource(videoPath)
            }

            val duration = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0

            var currentTime = 0L
            while (currentTime <= duration) {
                val bitmap = retriever?.getFrameAtTime(
                    currentTime * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                bitmap?.let { frames.add(it) }
                currentTime += 1000  // 1秒一帧
            }

            frames
        }

    override suspend fun getFrameAt(videoPath: String, timeMs: Long): Bitmap? =
        withContext(Dispatchers.Default) {
            if (retriever == null) {
                retriever = MediaMetadataRetriever().apply {
                    setDataSource(videoPath)
                }
            }
            retriever?.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        }

    override fun release() {
        try {
            retriever?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        retriever = null
    }

    override fun getName(): String = "MediaMetadataRetriever"
}
