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
 * 在由 [thumbnailCount] 张等宽缩略图组成的时间轴上绘制一个可拖动的圆角"取景框"，
 * 并把该框所在位置对应的时间点报告出去，模仿剪映/CapCut 的封面选择交互。
 *
 * 方框宽度始终等于一张缩略图的宽度；拖动会被限制在时间轴范围内，右边缘正好对应 [durationUs]。
 *
 * 视频较长时（每秒采样一张缩略图）时间轴可能比屏幕更宽，因此外面包了一层
 * [HorizontalScrollView]。本 View 只在手指按下的位置恰好在方框上时才接管这次触摸手势——
 * 按在时间轴其它地方的触摸不会被消费，会交给外层的 [HorizontalScrollView] 当作普通的
 * 横向滑动来处理。当方框正在被拖动、且拖到当前可见区域的边缘附近时，会自动带动
 * [HorizontalScrollView] 滚动，这样即使时间轴很长也不用松手就能一直拖过去。
 */
class CoverSelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 视频总时长（微秒）。必须在有明确值之后才设置，否则拖动没有意义。 */
    var durationUs: Long = 0L

    /** 下方时间轴上排布的缩略图数量。 */
    var thumbnailCount: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    /** 拖动过程中持续触发（已做节流），带上方框当前所在位置对应的时间点。 */
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

    /** 将方框移动到与 [timestampUs] 对应的位置，且不触发任何拖动回调。 */
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
                    // 没有按在方框上——交给外层的 HorizontalScrollView
                    // 当作普通的时间轴滑动来处理。
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

    /** 方框拖动到可见区域边缘附近时，带动外层的 [HorizontalScrollView] 滚动一点。 */
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
