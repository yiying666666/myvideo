package com.example.myapplication.cover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 绘制在 RecyclerView 上方的可拖动"取景框"，与 RecyclerView 同为兄弟节点
 *（两者都是 match_parent，覆盖同一块屏幕区域）。
 *
 * 方框宽度固定为一张缩略图的宽度（[thumbnailWidthPx]），位置用屏幕空间坐标
 * [currentLeftPx] 表示（0 … width - blockWidth）。对应的实际时间点需要结合
 * [getScrollOffset] 提供的 RecyclerView 横向滚动偏移一起换算。
 *
 * 手指按在方框上才消费本次触摸事件；按在时间轴空白处时返回 false，触摸事件
 * 穿透到下层的 RecyclerView，当作普通的横向滚动处理。
 *
 * 拖动到可见区边缘时，通过 [onScrollBy] 回调请求 RecyclerView 继续滚动，
 * 实现不松手就能把方框从头拖到尾的效果。
 */
class CoverSelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 视频总时长（微秒）。 */
    var durationUs: Long = 0L

    /** 一张缩略图的像素宽度，同时也是方框的宽度。 */
    var thumbnailWidthPx: Int = 0

    /** 时间轴内容的总像素宽度（= thumbnailWidthPx × 缩略图总数）。 */
    var totalContentWidthPx: Int = 0

    /** 返回 RecyclerView 当前的横向滚动偏移（像素）。 */
    var getScrollOffset: (() -> Int) = { 0 }

    /** 请求 RecyclerView 横向滚动 dx 像素（负值向左，正值向右）。 */
    var onScrollBy: ((dx: Int) -> Unit) = {}

    /** 拖动过程中持续触发（已做节流），带上方框当前对应的时间点。 */
    var onDragMoved: ((timestampUs: Long) -> Unit)? = null

    /** 手指抬起时触发一次，带上最终要精确锁定的时间点。 */
    var onDragReleased: ((timestampUs: Long) -> Unit)? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dpToPx(2.5f)
    }
    private val cornerRadiusPx = dpToPx(6f)
    private val drawRect = RectF()

    /** 方框左边缘在屏幕空间的位置（0 … width - blockWidth）。 */
    private var currentLeftPx = 0f
    private var grabOffsetX = 0f
    private var isDragging = false

    private val grabSlopPx = dpToPx(16f)
    private val autoScrollEdgeMarginPx = dpToPx(24f)
    private val autoScrollStepPx = dpToPx(8f).roundToInt()

    private val moveThrottleMs = 16L
    private var lastMoveSentAtMs = 0L

    private fun dpToPx(value: Float): Float = value * resources.displayMetrics.density

    private fun blockWidth(): Float = thumbnailWidthPx.toFloat()

    /** 方框左边缘在屏幕空间允许的最大值。 */
    private fun maxVisualLeft(): Float = (width - blockWidth()).coerceAtLeast(0f)

    /** 方框左边缘在时间轴全局坐标（含滚动偏移）中的绝对像素位置。 */
    private fun absoluteLeftPx(): Float = getScrollOffset().toFloat() + currentLeftPx

    /** 方框左边缘在时间轴全局坐标中允许的最大值（= 总内容宽 - 方框宽）。 */
    private fun maxAbsoluteLeft(): Float = (totalContentWidthPx - blockWidth()).coerceAtLeast(0f)

    /** 根据当前位置和滚动偏移换算出对应的视频时间点。 */
    private fun currentTimestampUs(): Long {
        val maxAbs = maxAbsoluteLeft()
        if (durationUs <= 0L || maxAbs <= 0f) return 0L
        return (absoluteLeftPx() / maxAbs * durationUs).roundToLong().coerceIn(0L, durationUs)
    }

    /**
     * 将方框移到 [timestampUs] 对应的屏幕位置，不触发任何拖动回调。
     * 若需先滚动 RecyclerView 到合适位置，应在调用本方法前完成。
     */
    fun setPositionForTimestamp(timestampUs: Long) {
        val maxAbs = maxAbsoluteLeft()
        val targetAbsolute = if (durationUs <= 0L || maxAbs <= 0f) 0f
                             else (timestampUs.toFloat() / durationUs) * maxAbs
        currentLeftPx = (targetAbsolute - getScrollOffset()).coerceIn(0f, maxVisualLeft())
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val grabLeft = currentLeftPx - grabSlopPx
                val grabRight = currentLeftPx + blockWidth() + grabSlopPx
                if (event.x < grabLeft || event.x > grabRight) {
                    // 没有按在方框上——放行给下层 RecyclerView 处理普通滚动
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

    /** 拖到可见区边缘时，通过回调请求 RecyclerView 继续横向滚动。 */
    private fun autoScrollIfNearEdge() {
        when {
            currentLeftPx < autoScrollEdgeMarginPx ->
                onScrollBy(-autoScrollStepPx)
            currentLeftPx + blockWidth() > width - autoScrollEdgeMarginPx ->
                onScrollBy(autoScrollStepPx)
        }
    }

    private fun applyTouchX(touchX: Float) {
        currentLeftPx = (touchX - grabOffsetX).coerceIn(0f, maxVisualLeft())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bw = blockWidth()
        if (bw <= 0f) return
        val inset = borderPaint.strokeWidth / 2f
        drawRect.set(
            currentLeftPx + inset,
            inset,
            currentLeftPx + bw - inset,
            height - inset
        )
        canvas.drawRoundRect(drawRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
    }
}
