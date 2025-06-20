package com.petterp.floatingx.imp

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
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
    var allActDestroyCallback: (() -> Unit)? = null
    private val backgroundCallbacks = CopyOnWriteArrayList<(background: Boolean) -> Unit>()

    fun addBackgroundCallback(callback: (background: Boolean) -> Unit) {
        backgroundCallbacks.add(callback)
    }
    fun removeBackgroundCallback(callback: (background: Boolean) -> Unit) {
        backgroundCallbacks.remove(callback)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!isActValid(activity)) return
        if (!isActivityOnTop(activity)) {
            return
        }

        updateTopActivity(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!isActValid(activity)) return
        if (!isActivityOnTop(activity)) {
            return
        }

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
        //判断activity是否在top 不在top则不处理
        if (!isActivityOnTop(activity)) {
            return
        }

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
        if (allActDestroyCallback == null || onStartedCount > 0) return

        val activityManager =
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?) ?: return
        val tasks = activityManager.appTasks
        if (tasks != null && tasks.size > 0) {
            kotlin.runCatching {
                var actNum = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    tasks.forEach { actNum += it.taskInfo.numActivities }
                } else {
                    val runningTasks = activityManager.getRunningTasks(10)
                    runningTasks.forEach { actNum += it.numActivities }
                }

                if (actNum == 0) {
                    allActDestroyCallback?.invoke()
                }
            }
        }
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

    fun isActivityOnTop(activity: Activity): Boolean {
        //如果服务获取不到则直接返回true
        val activityManager =
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?) ?: return true
        val tasks = activityManager.appTasks
        if (tasks != null && tasks.size > 0) {
            val topActivity = kotlin.runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    tasks[0].taskInfo.topActivity
                } else {
                    val runningTasks = activityManager.getRunningTasks(1)
                    runningTasks[0].topActivity
                }
            }.getOrNull()
            if (topActivity != null && topActivity.className == activity.javaClass.name) {
                return true
            }
        }
        return false
    }

    private fun updateTopActivity(activity: Activity) {
        if (_currentActivity?.get() === activity) return
        _currentActivity = WeakReference(activity)
        updateCallback?.invoke(activity)
    }

}
