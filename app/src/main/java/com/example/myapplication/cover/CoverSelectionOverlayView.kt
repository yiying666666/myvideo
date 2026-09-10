package com.example.myapplication.cover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Draws a draggable rounded-rect "viewfinder" over a filmstrip of [thumbnailCount] equal-width
 * thumbnails and reports the timestamp under it, mimicking JianYing/CapCut's cover picker.
 *
 * The block's width always matches one thumbnail's width; dragging is clamped so it can never
 * move past either end of the strip, and the right edge maps exactly to [durationUs].
 *
 * The filmstrip may be wider than the screen (long videos sample one thumbnail per second) and
 * sits inside a [HorizontalScrollView]. This view only claims a touch gesture that starts on top
 * of the block itself - a touch elsewhere on the strip is left alone so the surrounding
 * [HorizontalScrollView] can handle it as a normal horizontal swipe. While the block is actually
 * being dragged, it auto-scrolls the ancestor [HorizontalScrollView] when dragged near the edge
 * of the currently visible area, so a long timeline stays reachable without letting go.
 */
class CoverSelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Total video duration in microseconds. Must be set once known, before dragging is meaningful. */
    var durationUs: Long = 0L

    /** Number of thumbnails laid out in the strip beneath this view. */
    var thumbnailCount: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    /** Fired repeatedly while dragging, throttled, with the timestamp under the block. */
    var onDragMoved: ((timestampUs: Long) -> Unit)? = null

    /** Fired once when the finger lifts, with the final timestamp to lock in precisely. */
    var onDragReleased: ((timestampUs: Long) -> Unit)? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dpToPx(2.5f)
    }
    private val cornerRadiusPx = dpToPx(6f)
    private val drawRect = RectF()

    private var currentLeftPx = 0f
    private var grabOffsetX = 0f
    private var isDragging = false

    private val grabSlopPx = dpToPx(16f)
    private val autoScrollEdgeMarginPx = dpToPx(24f)
    private val autoScrollStepPx = dpToPx(8f).roundToInt()

    private val moveThrottleMs = 16L
    private var lastMoveSentAtMs = 0L

    private fun dpToPx(value: Float): Float = value * resources.displayMetrics.density

    private fun blockWidthPx(): Float =
        if (width == 0) 0f else width.toFloat() / thumbnailCount

    private fun maxLeftPx(): Float = (width - blockWidthPx()).coerceAtLeast(0f)

    /** Moves the block to match [timestampUs] without firing any drag callbacks. */
    fun setPositionForTimestamp(timestampUs: Long) {
        val maxLeft = maxLeftPx()
        currentLeftPx = if (durationUs <= 0L || maxLeft <= 0f) {
            0f
        } else {
            (timestampUs.toFloat() / durationUs).coerceIn(0f, 1f) * maxLeft
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val grabLeft = currentLeftPx - grabSlopPx
                val grabRight = currentLeftPx + blockWidthPx() + grabSlopPx
                if (event.x < grabLeft || event.x > grabRight) {
                    // Not touching the block - let the enclosing HorizontalScrollView
                    // handle this as a normal swipe over the strip instead.
                    isDragging = false
                    return false
                }
                isDragging = true
                grabOffsetX = event.x - currentLeftPx
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                applyTouchX(event.x)
                autoScrollIfNearEdge()
                val now = System.currentTimeMillis()
                if (now - lastMoveSentAtMs >= moveThrottleMs) {
                    lastMoveSentAtMs = now
                    onDragMoved?.invoke(currentTimestampUs())
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                applyTouchX(event.x)
                onDragReleased?.invoke(currentTimestampUs())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Nudges the ancestor [HorizontalScrollView] while the block is dragged near its edge. */
    private fun autoScrollIfNearEdge() {
        val scrollView = parent?.parent as? HorizontalScrollView ?: return
        val visibleLeft = scrollView.scrollX
        val visibleRight = visibleLeft + scrollView.width
        val blockRight = currentLeftPx + blockWidthPx()
        when {
            currentLeftPx < visibleLeft + autoScrollEdgeMarginPx -> scrollView.scrollBy(-autoScrollStepPx, 0)
            blockRight > visibleRight - autoScrollEdgeMarginPx -> scrollView.scrollBy(autoScrollStepPx, 0)
        }
    }

    private fun applyTouchX(touchX: Float) {
        currentLeftPx = (touchX - grabOffsetX).coerceIn(0f, maxLeftPx())
        invalidate()
    }

    private fun currentTimestampUs(): Long {
        val maxLeft = maxLeftPx()
        if (durationUs <= 0L || maxLeft <= 0f) return 0L
        val progress = currentLeftPx / maxLeft
        return (progress * durationUs).roundToLong().coerceIn(0L, durationUs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val blockWidth = blockWidthPx()
        if (blockWidth <= 0f) return
        val inset = borderPaint.strokeWidth / 2f
        drawRect.set(
            currentLeftPx + inset,
            inset,
            currentLeftPx + blockWidth - inset,
            height - inset
        )
        canvas.drawRoundRect(drawRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
    }
}
