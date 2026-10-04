package com.goodwy.filemanager.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs
import kotlin.math.min

/** Adds held vertical swipes while preserving the child's tap and horizontal swipe handling. */
class HeldVerticalSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    data class ActionPreview(val icon: Int, val color: Int, val tint: Int)

    private val holdTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val tracker = HeldVerticalSwipeTracker(
        ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        48f * resources.displayMetrics.density,
        holdTimeout
    )
    private val backgroundPaint = Paint()
    private var itemKey: String? = null
    private var upAction: ActionPreview? = null
    private var downAction: ActionPreview? = null
    private var upIcon: Drawable? = null
    private var downIcon: Drawable? = null
    private var intercepting = false
    private var vibrate = true
    private var selectionPending = false
    private var onSwipe: ((HeldVerticalSwipeTracker.Direction) -> Unit)? = null
    private var lastReadyDirection: HeldVerticalSwipeTracker.Direction? = null
    private val armGesture = Runnable {
        if (tracker.arm(SystemClock.uptimeMillis())) {
            selectionPending = true
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    init {
        setWillNotDraw(false)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun configure(
        key: String,
        up: ActionPreview?,
        down: ActionPreview?,
        vibration: Boolean,
        swipe: (HeldVerticalSwipeTracker.Direction) -> Unit
    ) {
        // Rebinding the same row must not interrupt a hold.
        if (itemKey != key || upAction != up || downAction != down) {
            // Consume the remainder of a recycled/reconfigured item's old touch stream.
            intercepting = intercepting || tracker.isWatching
            resetGesture()
        }
        if (upAction != up) upIcon = loadIcon(up)
        if (downAction != down) downIcon = loadIcon(down)
        itemKey = key
        upAction = up
        downAction = down
        vibrate = vibration
        onSwipe = swipe
    }

    override fun setOnClickListener(listener: View.OnClickListener?) {
        super.setOnClickListener(listener)
        // Only the child owns ordinary touch clicks. Explicit performClick() still invokes
        // this stored listener, including when the child receives an accessibility click.
        isClickable = false
        getChildAt(0)?.setOnClickListener(if (listener == null) null else View.OnClickListener { performClick() })
    }

    override fun setOnLongClickListener(listener: View.OnLongClickListener?) {
        super.setOnLongClickListener(listener)
        isLongClickable = false
        getChildAt(0)?.setOnLongClickListener(if (listener == null) null else View.OnLongClickListener {
            deferLongPress() || performLongClick()
        })
    }

    override fun performClick(): Boolean = super.performClick()

    fun deferLongPress(): Boolean {
        if (!tracker.isHeld) return false
        selectionPending = true
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) intercepting = false
        if (event.pointerCount > 1) resetGesture()
        if (event.actionMasked == MotionEvent.ACTION_UP && tracker.isDragging) {
            tracker.move(event.rawX, event.rawY)
        }
        val selectOnRelease = event.actionMasked == MotionEvent.ACTION_UP &&
            selectionPending && tracker.isHeld && tracker.readyDirection == null
        val handled = super.dispatchTouchEvent(event)
        val verticalHandled = intercepting
        if (verticalHandled) handleVerticalTouch(event)
        // MyRecyclerView ends drag selection after dispatching UP. Select inside that dispatch,
        // rather than enabling its drag selector while a vertical action gesture is still held.
        if (selectOnRelease) performLongClick()
        // Children may disallow interception during horizontal swipes. Always remove the timer.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetGesture()
            intercepting = false
        }
        return handled || verticalHandled || selectOnRelease
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (intercepting && event.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (event.pointerCount > 1) {
            resetGesture()
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                tracker.start(event.rawX, event.rawY, event.eventTime, upAction != null, downAction != null)
                if (tracker.isWatching) postDelayed(armGesture, holdTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracker.move(event.rawX, event.rawY)) {
                    removeCallbacks(armGesture)
                    intercepting = true
                    return true
                }
                if (!tracker.isWatching) {
                    removeCallbacks(armGesture)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Our hold arms before the child's long-press callback. Cancel the child's UP
                // even in that short interval, so selecting on release cannot also open the item.
                if (event.actionMasked == MotionEvent.ACTION_UP && selectionPending && tracker.isHeld) {
                    intercepting = true
                    return true
                }
                if (intercepting) return true
                resetGesture()
            }
        }
        return false
    }

    private fun handleVerticalTouch(event: MotionEvent) {
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetGesture()
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                tracker.move(event.rawX, event.rawY)
                updatePreview()
            }
            MotionEvent.ACTION_UP -> {
                tracker.move(event.rawX, event.rawY)
                val direction = tracker.finish()
                val callback = onSwipe
                resetGesture()
                if (direction != null) callback?.invoke(direction)
            }
        }
    }

    private fun updatePreview() {
        getChildAt(0)?.translationY = tracker.distance.coerceIn(-height * 0.65f, height * 0.65f)
        val ready = tracker.readyDirection
        if (ready != null && ready != lastReadyDirection && vibrate) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        lastReadyDirection = ready
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!tracker.isDragging) return
        val action = if (tracker.direction == HeldVerticalSwipeTracker.Direction.Up) upAction else downAction
        action ?: return
        backgroundPaint.color = action.color
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val translation = getChildAt(0)?.translationY ?: 0f
        val size = min(28f * resources.displayMetrics.density, abs(translation)).toInt()
        val centerY = if (translation > 0) translation / 2 else height + translation / 2
        val left = (width - size) / 2
        val top = (centerY - size / 2).toInt()
        val icon = if (tracker.direction == HeldVerticalSwipeTracker.Direction.Up) upIcon else downIcon
        icon?.apply {
            setAlpha(if (tracker.readyDirection != null) 255 else 140)
            setBounds(left, top, left + size, top + size)
            draw(canvas)
        }
    }

    private fun loadIcon(action: ActionPreview?): Drawable? = action?.let {
        AppCompatResources.getDrawable(context, it.icon)?.mutate()?.apply { DrawableCompat.setTint(this, it.tint) }
    }

    private fun resetGesture() {
        removeCallbacks(armGesture)
        tracker.cancel()
        selectionPending = false
        lastReadyDirection = null
        getChildAt(0)?.translationY = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        resetGesture()
        intercepting = false
        super.onDetachedFromWindow()
    }
}
