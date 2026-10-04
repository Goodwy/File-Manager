package com.goodwy.filemanager.views

import kotlin.math.abs

/** Tracks one stationary hold followed by a vertical drag, independently of Android views. */
class HeldVerticalSwipeTracker(
    private val touchSlop: Float,
    private val activationDistance: Float,
    private val holdTimeout: Long
) {
    enum class Direction { Up, Down }

    private enum class State { Idle, Waiting, Held, Dragging, Abandoned }

    private var state = State.Idle
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var upEnabled = false
    private var downEnabled = false

    var distance = 0f
        private set

    val isHeld get() = state == State.Held || state == State.Dragging
    val isDragging get() = state == State.Dragging
    val isWatching get() = state == State.Waiting || isHeld
    val direction get() = if (distance < 0f) Direction.Up else Direction.Down
    val readyDirection: Direction?
        get() = direction.takeIf { isDragging && abs(distance) >= activationDistance && isEnabled(it) }

    fun start(x: Float, y: Float, time: Long, allowUp: Boolean, allowDown: Boolean) {
        cancel()
        startX = x
        startY = y
        downTime = time
        upEnabled = allowUp
        downEnabled = allowDown
        state = if (allowUp || allowDown) State.Waiting else State.Idle
    }

    fun arm(time: Long): Boolean {
        if (state != State.Waiting || time - downTime < holdTimeout) return false
        state = State.Held
        return true
    }

    fun move(x: Float, y: Float): Boolean {
        val dx = abs(x - startX)
        val dy = abs(y - startY)
        if (state == State.Waiting && (dx > touchSlop || dy > touchSlop)) {
            state = State.Abandoned
        } else if (state == State.Held && (dx > touchSlop || dy > touchSlop)) {
            val proposedDirection = if (y < startY) Direction.Up else Direction.Down
            state = if (dy > dx && isEnabled(proposedDirection)) State.Dragging else State.Abandoned
        }

        if (isDragging) distance = y - startY
        return isDragging
    }

    fun finish(): Direction? {
        val result = readyDirection
        cancel()
        return result
    }

    fun cancel() {
        state = State.Idle
        distance = 0f
    }

    private fun isEnabled(direction: Direction) = when (direction) {
        Direction.Up -> upEnabled
        Direction.Down -> downEnabled
    }
}
