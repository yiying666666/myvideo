package com.example.myapplication.video

import android.content.Context
import android.graphics.Bitmap
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExoPlayerExtractor(context: Context) : FrameExtractor {
    private val context = context.applicationContext
    private var videoProcessor: ExoPlayerVideoProcessor? = null

    override suspend fun extractFrames(videoPath: String): List<Bitmap> =
        withContext(Dispatchers.Default) {
            val frames = mutableListOf<Bitmap>()

            try {
                val userAgent = Util.getUserAgent(context, "MyApp")
                val dataSourceFactory = DefaultDataSourceFactory(context, userAgent)
                val mediaItem = MediaItem.fromUri(videoPath)
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)

                videoProcessor = ExoPlayerVideoProcessor(context)
                frames.addAll(videoProcessor?.extractFrames(mediaSource, 1000) ?: emptyList())
            } catch (e: Exception) {
                e.printStackTrace()
            }

            frames
        }

    override suspend fun getFrameAt(videoPath: String, timeMs: Long): Bitmap? =
        withContext(Dispatchers.Default) {
            try {
                val userAgent = Util.getUserAgent(context, "MyApp")
                val dataSourceFactory = DefaultDataSourceFactory(context, userAgent)
                val mediaItem = MediaItem.fromUri(videoPath)
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)

                if (videoProcessor == null) {
                    videoProcessor = ExoPlayerVideoProcessor(context)
                }
                videoProcessor?.extractFrameAt(mediaSource, timeMs)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override fun release() {
        videoProcessor?.release()
        videoProcessor = null
    }

    override fun getName(): String = "ExoPlayer"
}
