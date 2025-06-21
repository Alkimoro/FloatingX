package com.petterp.floatingx.imp.app

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import com.petterp.floatingx.assist.helper.FxAppHelper
import com.petterp.floatingx.listener.provider.IFxPlatformProvider
import com.petterp.floatingx.util.decorView
import com.petterp.floatingx.util.safeAddView
import com.petterp.floatingx.util.safeRemoveView
import com.petterp.floatingx.util.topActivity
import com.petterp.floatingx.view.FxDefaultContainerView
import java.lang.ref.WeakReference

/**
 * 免权限的浮窗提供者
 * @author petterp
 */
class FxAppPlatformProvider(
    override val helper: FxAppHelper,
    override val control: FxAppControlImp,
) : IFxPlatformProvider<FxAppHelper> {

    private var _lifecycleImp: FxAppLifecycleImp? = null
    private var _internalView: FxDefaultContainerView? = null
    private var _containerGroup: WeakReference<ViewGroup>? = null

    private val windowsInsetsListener = OnApplyWindowInsetsListener { _, insets ->
        val statusBar = insets.stableInsetTop
        if (helper.statsBarHeight != statusBar) {
            helper.fxLog.v("System--StatusBar---old-(${helper.statsBarHeight}),new-($statusBar))")
            helper.statsBarHeight = statusBar
        }
        insets
    }

    private val containerGroupView: ViewGroup?
        get() = _containerGroup?.get()

    override val context: Context
        get() = helper.context
    override val internalView: FxDefaultContainerView?
        get() = _internalView

    init {
        // 这里仅仅是为了兼容旧版逻辑
        checkRegisterAppLifecycle()
    }

    override fun checkOrInit(): Boolean {
        checkRegisterAppLifecycle()
        // topActivity==null,依然返回true,因为在某些情况下，可能会在Activity未创建时，就调用show
        val act = topActivity ?: return true
        if (!helper.isCanInstall(act)) {
            helper.fxLog.d("fx not show,This ${act.javaClass.simpleName} is not in the list of allowed inserts!")
            return false
        }
        if (_internalView == null) {
            _internalView = FxDefaultContainerView(helper, helper.context)
            _internalView?.initView()
            checkOrInitSafeArea(act)
            attach(act)
        }
        return true
    }

    override fun show() {
        val fxView = _internalView ?: return
        if (!ViewCompat.isAttachedToWindow(fxView)) {
            fxView.visibility = View.VISIBLE
            checkOrReInitGroupView()?.safeAddView(fxView)
        } else if (fxView.visibility != View.VISIBLE) {
            fxView.visibility = View.VISIBLE
        }
    }

    override fun hide() {
        detach()
    }

    private fun checkOrReInitGroupView(): ViewGroup? {
        val curGroup = containerGroupView
        if (curGroup == null || curGroup !== topActivity?.decorView) {
            _containerGroup = WeakReference(topActivity?.decorView)
            helper.fxLog.v("view-----> reinitialize the fx container")
        }
        return containerGroupView
    }

    private val aniLeft = AnimationSet(true).apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 120
        addAnimation(TranslateAnimation(
            Animation.RELATIVE_TO_SELF, -1F,
            Animation.RELATIVE_TO_SELF, 0F,
            Animation.RELATIVE_TO_SELF, 0F,
            Animation.RELATIVE_TO_SELF, 0F
        ))
        addAnimation(AlphaAnimation(0F, 1F))
    }
    private val aniRight = AnimationSet(true).apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 120
        addAnimation(TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 1F,
            Animation.RELATIVE_TO_SELF, 0F,
            Animation.RELATIVE_TO_SELF, 0F,
            Animation.RELATIVE_TO_SELF, 0F
        ))
        addAnimation(AlphaAnimation(0F, 1F))
    }
    private val preDrawListener = object : OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            preDrawCallback?.invoke()
            return false
        }
    }
    private var preDrawCallback: (() -> Unit)? = null


    private fun attach(activity: Activity): Boolean {
        val fxView = _internalView ?: return false
        val decorView = activity.decorView ?: return false
        if (containerGroupView === decorView) return false
        if (ViewCompat.isAttachedToWindow(fxView)) containerGroupView?.safeRemoveView(fxView)
        _containerGroup = WeakReference(decorView)
        decorView.safeAddView(fxView)

        fxView.animation?.cancel()
        if (helper.enableAttachExpandAni) {
            setAttachExpandAni(fxView, decorView)
        } else {
            val xOffset = helper.rootViewXOffset?.invoke() ?: 0
            fxView.translationX = xOffset.toFloat()
            fxView.animation = AnimationSet(true).apply {
                interpolator = AccelerateDecelerateInterpolator()
                duration = 200
                addAnimation(TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1F,
                    Animation.RELATIVE_TO_SELF, 0F,
                    Animation.RELATIVE_TO_SELF, 0F,
                    Animation.RELATIVE_TO_SELF, 0F
                ))
                addAnimation(AlphaAnimation(0F, 1F))
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        helper.appScopeShowAniStart?.invoke()
                        helper.appScopeShowAniStart = null
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        fxView.translationX = xOffset.toFloat()
                        helper.appScopeShowAniEnd?.invoke()
                        helper.appScopeShowAniEnd = null
                    }

                    override fun onAnimationRepeat(animation: Animation?) { }
                })
            }
            fxView.animation?.start()
        }

        return true
    }

    private fun setAttachExpandAni(fxView: FxDefaultContainerView, decorView: FrameLayout) {
        fxView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        fxView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        //fxView.background = ColorDrawable(Color.RED)
        val xOffset = helper.rootViewXOffset?.invoke() ?: 0
        preDrawCallback = {
            AnimatorSet().apply {
                duration = 400
                val endTranslationY = fxView.translationY
                fxView.translationY = 0F
                fxView.translationX = 0F
                playTogether(
                    ValueAnimator.ofInt(decorView.width, fxView.width).apply {
                        this.addUpdateListener {
                            fxView.setAniR((it.animatedValue as? Int) ?: 0)
                        }
                    },
                    ValueAnimator.ofInt(0, xOffset).apply {
                        this.addUpdateListener {
                            fxView.setAniL((it.animatedValue as? Int) ?: 0)
                        }
                    },
                    ValueAnimator.ofInt((fxView.helper.statsBarHeight * 1.5).toInt(), endTranslationY.toInt()).apply {
                        this.addUpdateListener {
                            fxView.setAniT((it.animatedValue as? Int) ?: 0)
                        }
                    },
                    ValueAnimator.ofInt(decorView.height, endTranslationY.toInt() + fxView.height).apply {
                        this.addUpdateListener {
                            fxView.setAniB((it.animatedValue as? Int) ?: 0)
                        }
                    }
                )
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        fxView.skipLayout = false
                        fxView.translationY = endTranslationY
                        fxView.translationX = xOffset.toFloat()
                        helper.appScopeShowAniEnd?.invoke()
                        helper.appScopeShowAniEnd = null
                        fxView.requestLayout()
                    }
                    override fun onAnimationCancel(animation: Animator) { }
                    override fun onAnimationStart(animation: Animator) {
                        fxView.skipLayout = true
                        helper.appScopeShowAniStart?.invoke()
                        helper.appScopeShowAniStart = null
                    }
                    override fun onAnimationRepeat(animation: Animator) { }
                })
            }.start()
            fxView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            preDrawCallback = null
        }
    }

    fun reAttach(activity: Activity): Boolean {
        val nContainer = activity.decorView ?: return false
        if (_internalView == null) {
            _containerGroup = WeakReference(nContainer)
            return true
        } else {
            if (nContainer === containerGroupView) return false
            containerGroupView?.safeRemoveView(_internalView)
            nContainer.safeAddView(_internalView)
            _containerGroup = WeakReference(nContainer)

            helper.onReAttach?.invoke()
//            _internalView?.animation?.cancel()
//            if (_internalView?.locationHelper?.selfNearestLeft() == false) {
//                _internalView?.animation = aniRight
//            } else {
//                _internalView?.animation = aniLeft
//            }
//            _internalView?.animation?.startOffset = 350
//            _internalView?.animation?.start()
        }
        return false
    }

    fun destroyToDetach(activity: Activity): Boolean {
        val fxView = _internalView ?: return false
        val oldContainer = containerGroupView ?: return false
        if (!ViewCompat.isAttachedToWindow(fxView)) return false
        val nContainer = activity.decorView ?: return false
        if (nContainer !== oldContainer) return false
        oldContainer.safeRemoveView(_internalView)
        return true
    }

    override fun reset() {
        hide()
        clearWindowsInsetsListener()
        _internalView = null
        _containerGroup?.clear()
        _containerGroup = null
        helper.context.unregisterActivityLifecycleCallbacks(_lifecycleImp)
        _lifecycleImp = null
    }

    private fun detach() {
        _internalView?.visibility = View.GONE
        containerGroupView?.safeRemoveView(_internalView)
        _containerGroup?.clear()
        _containerGroup = null
    }

    private fun checkRegisterAppLifecycle() {
        if (!helper.enableFx || _lifecycleImp != null) return
        _lifecycleImp = FxAppLifecycleImp(helper, control)
        helper.context.registerActivityLifecycleCallbacks(_lifecycleImp)
    }

    private fun checkOrInitSafeArea(act: Activity) {
        if (!helper.enableSafeArea) return
        helper.updateStatsBar(act)
        helper.updateNavigationBar(act)
        val fxView = _internalView ?: return
        ViewCompat.setOnApplyWindowInsetsListener(fxView, windowsInsetsListener)
        fxView.requestApplyInsets()
    }

    private fun clearWindowsInsetsListener() {
        val managerView = _internalView ?: return
        ViewCompat.setOnApplyWindowInsetsListener(managerView, null)
    }
}
