package com.petterp.floatingx.imp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fx基础Provider提供者
 * @author petterp
 */
object FxAppLifecycleProvider : Application.ActivityLifecycleCallbacks {
    val blockList = arrayListOf("cn.jiguang.privates.common.component.JCommonActivity")

    private var onStartedCount = 0

    private var _currentActivity: WeakReference<Activity>? = null
    var updateCallback: ((activity: Activity) -> Unit)? = null
    private val backgroundCallbacks = CopyOnWriteArrayList<(background: Boolean) -> Unit>()

    fun addBackgroundCallback(callback: (background: Boolean) -> Unit) {
        backgroundCallbacks.add(callback)
    }
    fun removeBackgroundCallback(callback: (background: Boolean) -> Unit) {
        backgroundCallbacks.remove(callback)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!isActValid(activity)) return

        updateTopActivity(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!isActValid(activity)) return

        updateTopActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (!isActValid(activity)) return

        onStartedCount++
        if (onStartedCount == 1) {
            backgroundCallbacks.forEach { it.invoke(false) }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isActValid(activity)) return

        updateTopActivity(activity)
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
        if (!isActValid(activity)) return

        onStartedCount--
        if (isOnBackground()) {
            backgroundCallbacks.forEach { it.invoke(true) }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    private fun isActValid(activity: Activity?): Boolean {
        if (activity == null) return false
        if (blockList.contains(activity.javaClass.name)) return false
        return true
    }

    fun isOnBackground(): Boolean {
        return onStartedCount <= 0
    }

    fun getTopActivity(): Activity? = _currentActivity?.get()

    private fun updateTopActivity(activity: Activity) {
        if (_currentActivity?.get() === activity) return
        _currentActivity = WeakReference(activity)
        updateCallback?.invoke(activity)
    }

}
