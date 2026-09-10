package com.example.myapplication.video

import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

class FrameThumbnailAdapter(
    private val frames: List<Bitmap>,
    private val onFrameSelected: (Int, Long) -> Unit,
    private val timePerFrame: Long = 1000  // 1秒一帧
) : RecyclerView.Adapter<FrameThumbnailAdapter.FrameViewHolder>() {

    private var selectedPosition = 0

    inner class FrameViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.frameThumbnail)
        private val timeText: TextView = itemView.findViewById(R.id.frameTime)

        fun bind(position: Int, bitmap: Bitmap) {
            imageView.setImageBitmap(bitmap)
            val timeMs = position * timePerFrame
            timeText.text = formatTime(timeMs)

            itemView.isSelected = position == selectedPosition
            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(oldPosition)
                notifyItemChanged(position)
                onFrameSelected(position, timeMs)
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FrameViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_frame_thumbnail, parent, false)
        return FrameViewHolder(view)
    }

    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        holder.bind(position, frames[position])
    }

    override fun getItemCount(): Int = frames.size

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position.coerceIn(0, frames.size - 1)
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }
}
