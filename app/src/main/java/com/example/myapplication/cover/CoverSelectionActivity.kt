package com.example.myapplication.cover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 仿剪映/CapCut 的封面选择页：沿视频时间轴拖动方框选取一帧作为封面，
 * 也可以从相册选一张静态图片顶替。选择结果通过 [CoverSelectionContract] 返回。
 */
class CoverSelectionActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var filmstripRecycler: RecyclerView
    private lateinit var selectionOverlay: CoverSelectionOverlayView

    private var frameExtractor: FrameExtractor? = null
    private var thumbnailAdapter: ThumbnailAdapter? = null
    private var previewJob: Job? = null
    private var pendingResult: CoverResult? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pendingResult = CoverResult.StaticImage(uri)
                imagePreview.setImageURI(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cover_selection)

        val videoUri: Uri? = intent.getParcelableExtraCompat(CoverSelectionContract.EXTRA_VIDEO_URI)
        if (videoUri == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        imagePreview = findViewById(R.id.image_preview)
        filmstripRecycler = findViewById(R.id.filmstrip_recycler)
        selectionOverlay = findViewById(R.id.selection_overlay)

        findViewById<View>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        findViewById<View>(R.id.btn_confirm).setOnClickListener { confirmSelection() }
        findViewById<View>(R.id.btn_pick_album).setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        }

        selectionOverlay.onDragMoved = { timestampUs -> onDragMoved(timestampUs) }
        selectionOverlay.onDragReleased = { timestampUs -> onDragReleased(timestampUs) }
        // RecyclerView 的滚动偏移和滚动请求通过 lambda 注入，避免 Overlay 直接依赖 RecyclerView
        selectionOverlay.getScrollOffset = {
            if (filmstripRecycler.layoutManager != null)
                filmstripRecycler.computeHorizontalScrollOffset()
            else 0
        }
        selectionOverlay.onScrollBy = { dx -> filmstripRecycler.scrollBy(dx, 0) }

        savedInstanceState?.getParcelableCompat<CoverResult>(STATE_PENDING_RESULT)?.let {
            pendingResult = it
        }

        val extractor = FrameExtractor(applicationContext, videoUri)
        frameExtractor = extractor

        lifecycleScope.launch {
            if (!extractor.prepare()) {
                Toast.makeText(
                    this@CoverSelectionActivity,
                    R.string.cover_selection_load_failed,
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_CANCELED)
                finish()
                return@launch
            }

            val durationUs = extractor.getDurationUs()

            // 必须先初始化适配器，才能让 Overlay 获取到正确的 thumbnailWidthPx / totalContentWidthPx
            buildFilmstrip(extractor, durationUs)

            if (pendingResult == null) {
                pendingResult = CoverResult.VideoFrame(0L)
                val firstFrame = extractor.extractFrame(0L, precise = true)
                firstFrame?.let { imagePreview.setImageBitmap(it) }
                selectionOverlay.setPositionForTimestamp(0L)
            } else {
                val restored = pendingResult
                if (restored is CoverResult.VideoFrame) {
                    scrollToTimestampThenSetOverlay(restored.timestampUs, durationUs)
                    val frame = extractor.extractFrame(restored.timestampUs, precise = true)
                    frame?.let { imagePreview.setImageBitmap(it) }
                } else if (restored is CoverResult.StaticImage) {
                    imagePreview.setImageURI(restored.imageUri)
                    selectionOverlay.setPositionForTimestamp(0L)
                }
            }
        }
    }

    /**
     * 构建时间轴：创建 RecyclerView 适配器，按需懒加载缩略图，避免长视频一次性占满内存。
     * 每秒采样一帧，时间轴可横向滚动。
     */
    private fun buildFilmstrip(extractor: FrameExtractor, durationUs: Long) {
        val thumbnailCount = THUMBNAIL_COUNT
        val thumbnailWidthPx = (THUMBNAIL_WIDTH_DP * resources.displayMetrics.density).roundToInt()

        selectionOverlay.thumbnailWidthPx = thumbnailWidthPx
        selectionOverlay.totalContentWidthPx = thumbnailWidthPx * thumbnailCount
        selectionOverlay.durationUs = durationUs

        // 均匀分布：0, durationUs/(count-1), 2*durationUs/(count-1), …, durationUs
        val intervalUs = if (thumbnailCount <= 1) 0L else durationUs / (thumbnailCount - 1)

        val adapter = ThumbnailAdapter(
            count = thumbnailCount,
            widthPx = thumbnailWidthPx,
            extractor = extractor,
            durationUs = durationUs,
            intervalUs = intervalUs,
            scope = lifecycleScope
        )
        thumbnailAdapter = adapter

        filmstripRecycler.apply {
            layoutManager = LinearLayoutManager(
                this@CoverSelectionActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            this.adapter = adapter
            setHasFixedSize(true)
        }
    }

    /**
     * 旋转/重建后将 RecyclerView 滚到目标时间点附近（居中显示），再更新方框位置。
     * 用 post{} 等待 RecyclerView 完成布局后再操作，确保 computeHorizontalScrollOffset
     * 能返回正确值。
     */
    private fun scrollToTimestampThenSetOverlay(timestampUs: Long, durationUs: Long) {
        filmstripRecycler.post {
            val thumbWidthPx = selectionOverlay.thumbnailWidthPx
            val totalPx = selectionOverlay.totalContentWidthPx
            val maxAbsolute = (totalPx - thumbWidthPx).toFloat().coerceAtLeast(0f)
            val targetAbsolute = if (durationUs <= 0L) 0f
                                 else (timestampUs.toFloat() / durationUs) * maxAbsolute
            // 让方框目标位置出现在可见区中央
            val targetScroll = (targetAbsolute - filmstripRecycler.width / 2f)
                .toInt().coerceAtLeast(0)
            filmstripRecycler.scrollBy(targetScroll, 0)
            selectionOverlay.setPositionForTimestamp(timestampUs)
        }
    }

    private fun onDragMoved(timestampUs: Long) {
        val extractor = frameExtractor ?: return
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val bitmap = extractor.extractFrame(timestampUs, precise = false)
            bitmap?.let {
                imagePreview.setImageBitmap(it)
                pendingResult = CoverResult.VideoFrame(timestampUs)
            }
        }
    }

    private fun onDragReleased(timestampUs: Long) {
        val extractor = frameExtractor ?: return
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val bitmap = extractor.extractFrame(timestampUs, precise = true)
            bitmap?.let { imagePreview.setImageBitmap(it) }
            pendingResult = CoverResult.VideoFrame(timestampUs)
        }
    }

    private fun confirmSelection() {
        val result = pendingResult
        if (result == null) {
            setResult(RESULT_CANCELED)
        } else {
            setResult(RESULT_OK, Intent().putExtra(CoverSelectionContract.EXTRA_COVER_RESULT, result))
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingResult?.let { outState.putParcelable(STATE_PENDING_RESULT, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        previewJob?.cancel()
        thumbnailAdapter?.releaseAll()
        frameExtractor?.release()
    }

    private companion object {
        /** 无论视频多长，时间轴始终均匀取 10 帧缩略图。 */
        const val THUMBNAIL_COUNT = 10
        const val THUMBNAIL_WIDTH_DP = 56f
        const val STATE_PENDING_RESULT = "pending_result"
    }
}
