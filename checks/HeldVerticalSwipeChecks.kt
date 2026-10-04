package com.goodwy.filemanager.views

private val up = HeldVerticalSwipeTracker.Direction.Up
private val down = HeldVerticalSwipeTracker.Direction.Down
private var passed = 0

private fun scenario(name: String, block: () -> Unit) {
    block()
    passed++
    println("PASS: $name")
}

private fun tracker(allowUp: Boolean = true, allowDown: Boolean = true) =
    HeldVerticalSwipeTracker(8f, 48f, 500L).apply { start(200f, 300f, 0L, allowUp, allowDown) }

fun main() {
    scenario("normal vertical scrolling never arms an action") {
        val t = tracker()
        check(!t.move(200f, 250f))
        check(!t.arm(500L))
        check(t.finish() == null)
    }
    scenario("normal horizontal swipes remain outside the vertical tracker") {
        val t = tracker()
        check(!t.move(280f, 300f))
        check(!t.arm(500L))
        check(t.finish() == null)
    }
    scenario("diagonal movement before the hold cancels arming") {
        val t = tracker()
        check(!t.move(220f, 330f))
        check(!t.arm(500L))
        check(t.finish() == null)
    }
    scenario("a tap does not trigger an action") { check(tracker().finish() == null) }
    scenario("a stationary hold has no swipe action") {
        val t = tracker()
        check(!t.arm(499L))
        check(t.arm(500L))
        check(t.isHeld)
        check(t.finish() == null)
    }
    scenario("held up swipe is ready, then fires once on release") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 240f))
        check(t.readyDirection == up)
        check(t.finish() == up)
        check(t.finish() == null)
    }
    scenario("held down swipe fires on release") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 360f))
        check(t.finish() == down)
    }
    scenario("movement below the action threshold does not fire") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 260f))
        check(t.readyDirection == null)
        check(t.finish() == null)
    }
    scenario("the activation threshold is inclusive") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 252f))
        check(t.finish() == up)
    }
    scenario("small finger jitter still permits a stationary hold") {
        val t = tracker()
        check(!t.move(207f, 303f))
        check(t.arm(500L))
        check(t.move(207f, 350f))
        check(t.finish() == down)
    }
    scenario("movement beyond touch slop cancels the pending hold") {
        val t = tracker()
        check(!t.move(200f, 309f))
        check(!t.arm(500L))
    }
    scenario("horizontal movement after holding cancels the vertical gesture") {
        val t = tracker()
        check(t.arm(500L))
        check(!t.move(230f, 302f))
        check(!t.move(230f, 240f))
        check(t.finish() == null)
    }
    scenario("ambiguous diagonal movement after holding does not fire") {
        val t = tracker()
        check(t.arm(500L))
        check(!t.move(240f, 260f))
        check(t.finish() == null)
    }
    scenario("returning to the starting position cancels the ready action") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 240f))
        check(t.readyDirection == up)
        check(t.move(200f, 298f))
        check(t.readyDirection == null)
        check(t.finish() == null)
    }
    scenario("reversing direction uses the final direction at release") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 240f))
        check(t.move(200f, 360f))
        check(t.finish() == down)
    }
    scenario("Nothing in both slots disables held vertical gestures") {
        val t = tracker(false, false)
        check(!t.isWatching)
        check(!t.arm(500L))
        check(!t.move(200f, 240f))
        check(t.finish() == null)
    }
    scenario("a disabled down slot cannot start an action") {
        val t = tracker(true, false)
        check(t.arm(500L))
        check(!t.move(200f, 360f))
        check(t.finish() == null)
    }
    scenario("a disabled up slot cannot start an action") {
        val t = tracker(false, true)
        check(t.arm(500L))
        check(!t.move(200f, 240f))
        check(t.finish() == null)
    }
    scenario("reversing into a disabled slot does not fall back to the other action") {
        val t = tracker(true, false)
        check(t.arm(500L))
        check(t.move(200f, 240f))
        check(t.move(200f, 360f))
        check(t.readyDirection == null)
        check(t.finish() == null)
    }
    scenario("cancellation prevents release from firing") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 240f))
        t.cancel()
        check(t.finish() == null)
        check(!t.arm(1000L))
    }
    scenario("starting a recycled item clears the old gesture") {
        val t = tracker()
        check(t.arm(500L))
        check(t.move(200f, 240f))
        t.start(400f, 900f, 2000L, false, true)
        check(!t.isDragging)
        check(!t.arm(2499L))
        check(t.arm(2500L))
        check(t.move(400f, 960f))
        check(t.finish() == down)
    }
    scenario("new disabled configuration does not inherit enabled directions") {
        val t = tracker()
        t.start(200f, 300f, 1000L, false, false)
        check(!t.arm(1500L))
        check(t.finish() == null)
    }
    scenario("RTL screen coordinates do not reverse physical up and down") {
        val t = tracker()
        t.start(420f, 300f, 0L, true, true)
        check(t.arm(500L))
        check(t.move(420f, 240f))
        check(t.finish() == up)
    }
    println("$passed gesture scenarios passed. No app APK was built.")
}
