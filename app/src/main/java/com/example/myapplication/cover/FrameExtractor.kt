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
 * 包装 [MediaMetadataRetriever]，在非主线程上从视频中取帧。
 *
 * 同一个 [MediaMetadataRetriever] 实例不能被多个线程同时调用，所以每次调用都通过
 * [mutex] 串行化——这也意味着一个被取消的调用（比如被更新的拖动位置取代）只会
 * 直接从队列里退出，不会和还在进行中的取帧操作产生竞争。
 */
class FrameExtractor(context: Context, private val videoUri: Uri) {

    private val appContext = context.applicationContext
    private val retriever = MediaMetadataRetriever()
    private val mutex = Mutex()

    private var prepared = false
    private var durationUs: Long = 0L

    /** 打开视频并读取其时长；如果视频无法读取则返回 false。 */
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
     * 取出离 [timestampUs] 最近的一帧。
     * [precise] = false 时使用 [MediaMetadataRetriever.OPTION_CLOSEST_SYNC]（最近的关键帧，
     * 速度快——用于拖动过程中实时刷新）。[precise] = true 时使用
     * [MediaMetadataRetriever.OPTION_CLOSEST]（解码出精确帧，速度慢——用于生成时间轴缩略图，
     * 以及松手后锁定最终选中的封面）。
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
            // 已经释放过，或从未成功 prepare 过——可以安全忽略。
        }
    }
}
