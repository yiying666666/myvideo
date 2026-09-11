package com.example.myapplication.cover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 仿剪映/CapCut 的封面选择页：沿视频时间轴拖动方框选取一帧作为封面，
 * 也可以从相册选一张静态图片顶替。选择结果通过 [CoverSelectionContract] 返回。
 */
class CoverSelectionActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var filmstripContainer: LinearLayout
    private lateinit var selectionOverlay: CoverSelectionOverlayView

    private var frameExtractor: FrameExtractor? = null
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
        filmstripContainer = findViewById(R.id.filmstrip_container)
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
            selectionOverlay.durationUs = durationUs

            if (pendingResult == null) {
                pendingResult = CoverResult.VideoFrame(0L)
                val firstFrame = extractor.extractFrame(0L, precise = true)
                firstFrame?.let { imagePreview.setImageBitmap(it) }
                selectionOverlay.setPositionForTimestamp(0L)
            } else {
                val restored = pendingResult
                if (restored is CoverResult.VideoFrame) {
                    selectionOverlay.setPositionForTimestamp(restored.timestampUs)
                    val frame = extractor.extractFrame(restored.timestampUs, precise = true)
                    frame?.let { imagePreview.setImageBitmap(it) }
                } else if (restored is CoverResult.StaticImage) {
                    imagePreview.setImageURI(restored.imageUri)
                }
            }

            buildFilmstrip(extractor, durationUs)
        }
    }

    /** 每秒视频采样一张缩略图，放进一条可横向滚动的时间轴。 */
    private fun buildFilmstrip(extractor: FrameExtractor, durationUs: Long) {
        val thumbnailCount = max(1, ceil(durationUs.toDouble() / THUMBNAIL_INTERVAL_US).toInt())
        val thumbnailWidthPx = (THUMBNAIL_WIDTH_DP * resources.displayMetrics.density).roundToInt()

        selectionOverlay.thumbnailCount = thumbnailCount
        (selectionOverlay.layoutParams as FrameLayout.LayoutParams).width = thumbnailWidthPx * thumbnailCount
        selectionOverlay.requestLayout()

        filmstripContainer.removeAllViews()
        val thumbnailViews = ArrayList<ImageView>(thumbnailCount)
        repeat(thumbnailCount) {
            val thumbnailView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbnailWidthPx, LinearLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_thumb_placeholder)
            }
            filmstripContainer.addView(thumbnailView)
            thumbnailViews.add(thumbnailView)
        }

        lifecycleScope.launch {
            for (i in 0 until thumbnailCount) {
                val timestampUs = (i.toLong() * THUMBNAIL_INTERVAL_US).coerceAtMost(durationUs)
                val bitmap = extractor.extractFrame(timestampUs, precise = true)
                bitmap?.let { thumbnailViews[i].setImageBitmap(it) }
            }
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
        frameExtractor?.release()
    }

    private companion object {
        /** 每秒视频采样一张缩略图。 */
        const val THUMBNAIL_INTERVAL_US = 1_000_000L
        const val THUMBNAIL_WIDTH_DP = 56f
        const val STATE_PENDING_RESULT = "pending_result"
    }
}
