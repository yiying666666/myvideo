package com.example.myapplication.cover

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps [MediaMetadataRetriever] to pull frames out of a video off the main thread.
 *
 * A single [MediaMetadataRetriever] instance is not safe to drive from more than one
 * thread at a time, so every call is serialized through [mutex] - this also means a
 * cancelled caller (e.g. a superseded drag position) simply drops out of the queue
 * instead of racing a still-running extraction.
 */
class FrameExtractor(context: Context, private val videoUri: Uri) {

    private val appContext = context.applicationContext
    private val retriever = MediaMetadataRetriever()
    private val mutex = Mutex()

    private var prepared = false
    private var durationUs: Long = 0L

    /** Opens the video and reads its duration. Returns false if the video can't be read. */
    suspend fun prepare(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (prepared) return@withContext true
            try {
                retriever.setDataSource(appContext, videoUri)
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                if (durationMs == null || durationMs <= 0L) return@withContext false
                durationUs = durationMs * 1_000L
                prepared = true
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getDurationUs(): Long = durationUs

    /**
     * Extracts the frame nearest [timestampUs].
     * [precise] = false uses [MediaMetadataRetriever.OPTION_CLOSEST_SYNC] (nearest keyframe,
     * fast - used while actively dragging). [precise] = true uses
     * [MediaMetadataRetriever.OPTION_CLOSEST] (decodes to the exact frame, slower - used for
     * the filmstrip thumbnails and to lock in the final selection on release).
     */
    suspend fun extractFrame(timestampUs: Long, precise: Boolean): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!prepared) return@withContext null
            try {
                val option = if (precise) {
                    MediaMetadataRetriever.OPTION_CLOSEST
                } else {
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                }
                retriever.getFrameAtTime(timestampUs.coerceIn(0L, durationUs), option)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun release() {
        try {
            retriever.release()
        } catch (e: Exception) {
            // Already released, or never successfully prepared - safe to ignore.
        }
    }
}
