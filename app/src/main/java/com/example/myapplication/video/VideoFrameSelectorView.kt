package com.example.myapplication.video

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VideoFrameSelectorView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private lateinit var previewImage: ImageView
    private lateinit var scrollView: HorizontalScrollView
    private lateinit var recyclerView: RecyclerView
    private lateinit var timeTextView: TextView
    private lateinit var methodTextView: TextView
    private var adapter: FrameThumbnailAdapter? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var frames: List<Bitmap> = emptyList()
    private var onCoverSelected: ((Bitmap, Long) -> Unit)? = null
    private var extractorName = ""

    init {
        inflate(context, R.layout.view_video_frame_selector, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        previewImage = findViewById(R.id.previewImage)
        scrollView = findViewById(R.id.frameScrollView)
        recyclerView = findViewById(R.id.frameRecyclerView)
        timeTextView = findViewById(R.id.timeTextView)
        methodTextView = findViewById(R.id.methodTextView)

        recyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        setupScrollListener()
    }

    fun setFrames(frames: List<Bitmap>, extractorName: String) {
        this.frames = frames
        this.extractorName = extractorName
        methodTextView.text = "方案: $extractorName"

        if (frames.isNotEmpty()) {
            adapter = FrameThumbnailAdapter(frames) { position, timeMs ->
                updatePreview(position, frames[position], timeMs)
            }
            recyclerView.adapter = adapter
            updatePreview(0, frames.first(), 0)
        }
    }

    fun setOnCoverSelected(callback: (Bitmap, Long) -> Unit) {
        this.onCoverSelected = callback
    }

    private fun setupScrollListener() {
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                val scrollX = scrollView.scrollX
                val thumbWidth = 100  // 缩略图宽度
                val selectedPosition = (scrollX + thumbWidth / 2) / thumbWidth
                adapter?.setSelectedPosition(selectedPosition.coerceIn(0, frames.size - 1))
            }
            false
        }
    }

    private fun updatePreview(position: Int, bitmap: Bitmap, timeMs: Long) {
        previewImage.setImageBitmap(bitmap)
        timeTextView.text = formatTime(timeMs)
        onCoverSelected?.invoke(bitmap, timeMs)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun release() {
        scope.cancel()
        adapter = null
    }
}
