package com.example.myapplication.cover

import android.graphics.Bitmap
import android.util.LruCache
import android.util.SparseArray
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 封面选择时间轴的缩略图适配器。
 *
 * 只加载当前可见区域内（以及即将滑入的相邻区域）的缩略图帧，RecyclerView 回收一个
 * ViewHolder 时立即取消该位置的提取协程，避免长视频一次性占满内存。
 *
 * 内存上限约 20 MB，超出后由 [LruCache] 自动淘汰最久未用的帧。
 */
class ThumbnailAdapter(
    private val count: Int,
    private val widthPx: Int,
    private val extractor: FrameExtractor,
    private val durationUs: Long,
    private val intervalUs: Long,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<ThumbnailAdapter.VH>() {

    /** 20 MB 的位图缓存；淘汰时不主动 recycle，由 GC 管理，避免画面仍持有已回收 Bitmap。 */
    private val bitmapCache = object : LruCache<Int, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    /** 每个 position 对应的正在运行的提取协程，用于在 ViewHolder 被回收时及时取消。 */
    private val jobs = SparseArray<Job>()

    class VH(val image: ImageView) : RecyclerView.ViewHolder(image) {
        /** 当前绑定的 position；回收或重绑时用于精确取消对应的加载任务。 */
        var loadingPosition: Int = RecyclerView.NO_ID.toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_thumb_placeholder)
        }
    )

    override fun getItemCount() = count

    override fun onBindViewHolder(holder: VH, position: Int) {
        // 取消前一次绑定遗留的加载任务（ViewHolder 被复用时）
        val prev = holder.loadingPosition
        if (prev != position && prev != RecyclerView.NO_ID.toInt()) {
            jobs[prev]?.cancel()
            jobs.remove(prev)
        }
        holder.loadingPosition = position

        val cached = bitmapCache.get(position)
        if (cached != null) {
            holder.image.setImageBitmap(cached)
            return
        }

        holder.image.setImageDrawable(null)   // 展示占位色块

        val timestampUs = (position.toLong() * intervalUs).coerceAtMost(durationUs)
        jobs[position] = scope.launch {
            val bm = extractor.extractFrame(timestampUs, precise = true)
            if (bm != null) {
                bitmapCache.put(position, bm)
                withContext(Dispatchers.Main) {
                    // ViewHolder 可能在协程挂起期间已被复用到其他 position，需二次校验
                    if (holder.loadingPosition == position) holder.image.setImageBitmap(bm)
                }
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val pos = holder.loadingPosition
        if (pos != RecyclerView.NO_ID.toInt()) {
            jobs[pos]?.cancel()
            jobs.remove(pos)
            holder.loadingPosition = RecyclerView.NO_ID.toInt()
        }
    }

    /** 页面销毁时调用，取消所有未完成的提取任务并清空位图缓存。 */
    fun releaseAll() {
        for (i in 0 until jobs.size()) jobs.valueAt(i)?.cancel()
        jobs.clear()
        bitmapCache.evictAll()
    }
}
