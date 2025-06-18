package com.petterp.floatingx.view.helper

import android.animation.Animator
import android.animation.ValueAnimator
import com.petterp.floatingx.util.DEFAULT_MOVE_ANIMATOR_DURATION

/**
 * Fx动画助手，处理移动等动画
 * @author petterp
 */
class FxViewAnimationHelper : FxViewBasicHelper() {
    private var valueAnimator: ValueAnimator? = null
    private var startX: Float = 0f
    private var startY: Float = 0f
    private var endX: Float = 0f
    private var endY: Float = 0f
    private val callbacks = ArrayList<() -> Unit>()

    fun start(endX: Float, endY: Float, onEnd: (() -> Unit)? = null) {
        val startX = basicView?.x ?: 0f
        val startY = basicView?.y ?: 0f
        if (startX == endX && startY == endY) {
            onEnd?.invoke()
            return
        }
        this.startX = startX
        this.startY = startY
        this.endX = endX
        this.endY = endY
        checkOrInitAnimator()
        onEnd?.let { callbacks.add(onEnd) }
        if (valueAnimator?.isRunning == true) valueAnimator?.cancel()
        valueAnimator?.start()
    }

    private fun checkOrInitAnimator() {
        if (valueAnimator == null) {
            valueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DEFAULT_MOVE_ANIMATOR_DURATION
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    val x = calculationNumber(startX, endX, fraction)
                    val y = calculationNumber(startY, endY, fraction)
                    basicView?.updateXY(x, y)
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) { }

                    override fun onAnimationEnd(animation: Animator) {
                        callbacks.forEach { it.invoke() }
                        callbacks.clear()
                    }

                    override fun onAnimationCancel(animation: Animator) { }

                    override fun onAnimationRepeat(animation: Animator) { }
                })
            }
        }
    }

    private fun calculationNumber(start: Float, end: Float, fraction: Float): Float {
        val currentX = if (start == end) {
            start
        } else {
            start + (end - start) * fraction
        }
        return currentX
    }

    override fun onPreCancel() {
        valueAnimator?.cancel()
        valueAnimator = null
    }
}
