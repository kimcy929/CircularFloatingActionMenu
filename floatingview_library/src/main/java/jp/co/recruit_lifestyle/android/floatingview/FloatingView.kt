/**
 * Copyright 2015 RECRUIT LIFESTYLE CO., LTD.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.recruit_lifestyle.android.floatingview

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.*
import java.lang.ref.WeakReference
import kotlin.math.*

/**
 * フローティングViewを表すクラスです。
 * http://stackoverflow.com/questions/18503050/how-to-create-draggabble-system-alert-in-android
 * FIXME:Nexus5＋YouTubeアプリの場合にナビゲーションバーよりも前面に出てきてしまう
 */
internal class FloatingView
/**
 * コンストラクタ
 *
 * @param context [android.content.Context]
 */
(context: Context) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {

    /**
     * WindowManager
     */
    private val mWindowManager: WindowManager

    /**
     * LayoutParams
     */
    /**
     * WindowManager.LayoutParamsを取得します。
     */
    val windowLayoutParams: WindowManager.LayoutParams

    /**
     * VelocityTracker
     */
    private var mVelocityTracker: VelocityTracker? = null

    /**
     * [ViewConfiguration]
     */
    private var mViewConfiguration: ViewConfiguration? = null

    /**
     * Minimum threshold required for movement(px)
     */
    private var mMoveThreshold: Float = 0.toFloat()

    /**
     * Maximum fling velocity
     */
    private var mMaximumFlingVelocity: Float = 0.toFloat()

    /**
     * Maximum x coordinate velocity
     */
    private var mMaximumXVelocity: Float = 0.toFloat()

    /**
     * Maximum x coordinate velocity
     */
    private var mMaximumYVelocity: Float = 0.toFloat()

    /**
     * Threshold to move when throwing
     */
    private var mThrowMoveThreshold: Float = 0.toFloat()

    /**
     * DisplayMetrics
     */
    private val mMetrics: DisplayMetrics

    /**
     * 押下処理を通過しているかチェックするための時間
     */
    private var mTouchDownTime: Long = 0

    /**
     * スクリーン押下X座標(移動量判定用)
     */
    private var mScreenTouchDownX: Float = 0.toFloat()
    /**
     * スクリーン押下Y座標(移動量判定用)
     */
    private var mScreenTouchDownY: Float = 0.toFloat()
    /**
     * 一度移動を始めたフラグ
     */
    private var mIsMoveAccept: Boolean = false

    /**
     * スクリーンのタッチX座標
     */
    private var mScreenTouchX: Float = 0.toFloat()
    /**
     * スクリーンのタッチY座標
     */
    private var mScreenTouchY: Float = 0.toFloat()
    /**
     * ローカルのタッチX座標
     */
    private var mLocalTouchX: Float = 0.toFloat()
    /**
     * ローカルのタッチY座標
     */
    private var mLocalTouchY: Float = 0.toFloat()
    /**
     * 初期表示のX座標
     */
    private var mInitX: Int = 0
    /**
     * 初期表示のY座標
     */
    private var mInitY: Int = 0

    /**
     * Initial animation running flag
     */
    private var mIsInitialAnimationRunning: Boolean = false

    /**
     * 初期表示時にアニメーションするフラグ
     */
    private var mAnimateInitialMove: Boolean = false

    /**
     * status bar's height
     */
    private val mBaseStatusBarHeight: Int

    /**
     * status bar's height(landscape)
     */
    private val mBaseStatusBarRotatedHeight: Int

    /**
     * Current status bar's height
     */
    private var mStatusBarHeight: Int = 0

    /**
     * Navigation bar's height(portrait)
     */
    private val mBaseNavigationBarHeight: Int

    /**
     * Navigation bar's height
     * Placed bottom on the screen(tablet)
     * Or placed vertically on the screen(phone)
     */
    private val mBaseNavigationBarRotatedHeight: Int

    /**
     * Current Navigation bar's vertical size
     */
    private var mNavigationBarVerticalOffset: Int = 0

    /**
     * Current Navigation bar's horizontal size
     */
    private var mNavigationBarHorizontalOffset: Int = 0

    /**
     * Offset of touch X coordinate
     */
    private var mTouchXOffset: Int = 0

    /**
     * Offset of touch Y coordinate
     */
    private var mTouchYOffset: Int = 0

    /**
     * 左・右端に寄せるアニメーション
     */
    private var mMoveEdgeAnimator: ValueAnimator? = null

    /**
     * Interpolator
     */
    private val mMoveEdgeInterpolator: TimeInterpolator

    /**
     * 移動限界を表すRect
     */
    private val mMoveLimitRect: Rect

    /**
     * 表示位置（画面端）の限界を表すRect
     */
    private val mPositionLimitRect: Rect

    /**
     * ドラッグ可能フラグ
     */
    private var mIsDraggable: Boolean = false

    /**
     * 形を表す係数
     */
    /**
     * Viewの形を取得します。
     *
     * @return SHAPE_CIRCLE or SHAPE_RECTANGLE
     */
    /**
     * Viewの形を表す定数
     *
     * @param shape SHAPE_CIRCLE or SHAPE_RECTANGLE
     */
    var shape: Float = 0.toFloat()

    /**
     * FloatingViewのアニメーションを行うハンドラ
     */
    private val mAnimationHandler: FloatingAnimationHandler

    /**
     * 長押しを判定するためのハンドラ
     */
    private val mLongPressHandler: LongPressHandler

    /**
     * 画面端をオーバーするマージン
     */
    private var mOverMargin: Int = 0

    /**
     * OnTouchListener
     */
    private var mOnTouchListener: OnTouchListener? = null

    /**
     * 長押し状態の場合
     */
    private var mIsLongPressed: Boolean = false

    /**
     * 移動方向
     */
    private var mMoveDirection: Int = 0

    /**
     * Use dynamic physics-based animations or not
     */
    private var mUsePhysics: Boolean = false

    /**
     * If true, it's a tablet. If false, it's a phone
     */
    private val mIsTablet: Boolean

    /**
     * Surface.ROTATION_XXX
     */
    private var mRotation: Int = 0

    /**
     * Cutout safe inset rect(Same as FloatingViewManager's mSafeInsetRect)
     */
    private val mSafeInsetRect: Rect

    private var realTimePositionListener: RealTimePositionListener? = null

    /**
     * タッチ座標から算出されたFloatingViewのX座標
     *
     * @return FloatingViewのX座標
     */
    private val xByTouch: Int
        get() = (mScreenTouchX - mLocalTouchX - mTouchXOffset.toFloat()).toInt()

    /**
     * タッチ座標から算出されたFloatingViewのY座標
     *
     * @return FloatingViewのY座標
     */
    private val yByTouch: Int
        get() = (mMetrics.heightPixels + mNavigationBarVerticalOffset - (mScreenTouchY - mLocalTouchY + height - mTouchYOffset)).toInt()

    val state: Int
        get() = mAnimationHandler.state

    val heightLimit: Int
        get() = mPositionLimitRect.height()

    /**
     * AnimationState
     */
    @IntDef(STATE_NORMAL, STATE_INTERSECTING, STATE_FINISHING)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    internal annotation class AnimationState

    init {
        mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowLayoutParams = WindowManager.LayoutParams()
        mMetrics = DisplayMetrics()
        mWindowManager.defaultDisplay.getMetrics(mMetrics)
        windowLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        windowLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        windowLayoutParams.type = OVERLAY_TYPE
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        windowLayoutParams.format = PixelFormat.TRANSLUCENT
        // 左下の座標を0とする
        windowLayoutParams.gravity = Gravity.START or Gravity.BOTTOM
        mAnimationHandler = FloatingAnimationHandler(this)
        mLongPressHandler = LongPressHandler(this)
        mMoveEdgeInterpolator = OvershootInterpolator(MOVE_TO_EDGE_OVERSHOOT_TENSION)
        mMoveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT
        mUsePhysics = false
        val resources = context.resources
        mIsTablet = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE
        mRotation = mWindowManager.defaultDisplay.rotation

        mMoveLimitRect = Rect()
        mPositionLimitRect = Rect()
        mSafeInsetRect = Rect()

        // ステータスバーの高さを取得
        mBaseStatusBarHeight = getSystemUiDimensionPixelSize(resources, "status_bar_height")
        // Check landscape resource id
        val statusBarLandscapeResId = resources.getIdentifier("status_bar_height_landscape", "dimen", "android")
        mBaseStatusBarRotatedHeight = if (statusBarLandscapeResId > 0) {
            getSystemUiDimensionPixelSize(resources, "status_bar_height_landscape")
        } else {
            mBaseStatusBarHeight
        }

        // Init physics-based animation properties
        updateViewConfiguration()

        // Detect NavigationBar
        if (hasSoftNavigationBar()) {
            mBaseNavigationBarHeight = getSystemUiDimensionPixelSize(resources, "navigation_bar_height")
            val resName = if (mIsTablet) "navigation_bar_height_landscape" else "navigation_bar_width"
            mBaseNavigationBarRotatedHeight = getSystemUiDimensionPixelSize(resources, resName)
        } else {
            mBaseNavigationBarHeight = 0
            mBaseNavigationBarRotatedHeight = 0
        }

        // 初回描画処理用
        viewTreeObserver.addOnPreDrawListener(this)
    }

    /**
     * Check if there is a software navigation bar(including the navigation bar in the screen).
     *
     * @return True if there is a software navigation bar
     */
    private fun hasSoftNavigationBar(): Boolean {
        val realDisplayMetrics = DisplayMetrics()
        mWindowManager.defaultDisplay.getRealMetrics(realDisplayMetrics)
        return realDisplayMetrics.heightPixels > mMetrics.heightPixels || realDisplayMetrics.widthPixels > mMetrics.widthPixels

        // old device check flow
        // Navigation bar exists (config_showNavigationBar is true, or both the menu key and the back key are not exists)
    }


    /**
     * 表示位置を決定します。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshLimitRect()
    }

    /**
     * 画面回転時にレイアウトの調整をします。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateViewConfiguration()
        refreshLimitRect()
    }

    /**
     * 初回描画時の座標設定を行います。
     */
    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        // X座標に初期値が設定されていればデフォルト値を入れる(マージンは考慮しない)
        if (mInitX == DEFAULT_X) {
            mInitX = 0
        }
        // Y座標に初期値が設定されていればデフォルト値を入れる
        if (mInitY == DEFAULT_Y) {
            mInitY = mMetrics.heightPixels - mStatusBarHeight - measuredHeight
        }

        // 初期位置を設定
        windowLayoutParams.x = mInitX
        windowLayoutParams.y = mInitY

        // 画面端に移動しない場合は指定座標に移動
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NONE) {
            moveTo(mInitX, mInitY, mInitX, mInitY, false)
        } else {
            mIsInitialAnimationRunning = true
            // 初期位置から画面端に移動
            moveToEdge(mInitX, mInitY, mAnimateInitialMove)
        }
        mIsDraggable = true
        updateViewLayout()
        return true
    }

    /**
     * Called when the layout of the system has changed.
     *
     * @param isHideStatusBar     If true, the status bar is hidden
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param isPortrait          If true, the device orientation is portrait
     * @param windowRect          [Rect] of system window
     */
    fun onUpdateSystemLayout(isHideStatusBar: Boolean, isHideNavigationBar: Boolean, isPortrait: Boolean, windowRect: Rect) {
        // status bar
        updateStatusBarHeight(isHideStatusBar, isPortrait)
        // touch X offset(support Cutout)
        updateTouchXOffset(isHideNavigationBar, windowRect.left)
        // touch Y offset(support Cutout)
        mTouchYOffset = if (isPortrait) mSafeInsetRect.top else 0
        // navigation bar
        updateNavigationBarOffset(isHideNavigationBar, isPortrait, windowRect)
        refreshLimitRect()
    }

    /**
     * Update height of StatusBar.
     *
     * @param isHideStatusBar If true, the status bar is hidden
     * @param isPortrait      If true, the device orientation is portrait
     */
    private fun updateStatusBarHeight(isHideStatusBar: Boolean, isPortrait: Boolean) {
        if (isHideStatusBar) {
            // 1.(No Cutout)No StatusBar(=0)
            // 2.(Has Cutout)StatusBar is not included in mMetrics.heightPixels (=0)
            mStatusBarHeight = 0
            return
        }

        // Has Cutout
        val hasTopCutout = mSafeInsetRect.top != 0
        if (hasTopCutout) {
            mStatusBarHeight = if (isPortrait) {
                0
            } else {
                mBaseStatusBarRotatedHeight
            }
            return
        }

        // No cutout
        mStatusBarHeight = if (isPortrait) {
            mBaseStatusBarHeight
        } else {
            mBaseStatusBarRotatedHeight
        }
    }

    /**
     * Update of touch X coordinate
     *
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param windowLeftOffset    Left side offset of device display
     */
    private fun updateTouchXOffset(isHideNavigationBar: Boolean, windowLeftOffset: Int) {
        val hasBottomCutout = mSafeInsetRect.bottom != 0
        if (hasBottomCutout) {
            mTouchXOffset = windowLeftOffset
            return
        }

        // No cutout
        // touch X offset(navigation bar is displayed and it is on the left side of the device)
        mTouchXOffset = if (!isHideNavigationBar && windowLeftOffset > 0) mBaseNavigationBarRotatedHeight else 0
    }

    /**
     * Update offset of NavigationBar.
     *
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param isPortrait          If true, the device orientation is portrait
     * @param windowRect          [Rect] of system window
     */
    private fun updateNavigationBarOffset(isHideNavigationBar: Boolean, isPortrait: Boolean, windowRect: Rect) {
        val currentNavigationBarHeight: Int
        val currentNavigationBarWidth: Int
        val navigationBarVerticalDiff: Int
        val hasSoftNavigationBar = hasSoftNavigationBar()
        // auto hide navigation bar(Galaxy S8, S9 and so on.)
        val realDisplayMetrics = DisplayMetrics()
        mWindowManager.defaultDisplay.getRealMetrics(realDisplayMetrics)
        currentNavigationBarHeight = realDisplayMetrics.heightPixels - windowRect.bottom
        currentNavigationBarWidth = realDisplayMetrics.widthPixels - mMetrics.widthPixels
        navigationBarVerticalDiff = mBaseNavigationBarHeight - currentNavigationBarHeight

        if (!isHideNavigationBar) {
            // auto hide navigation bar
            // 他デバイスとの矛盾をもとに推測する
            // 1.デバイスに組み込まれたナビゲーションバー（mBaseNavigationBarHeight == 0）はシステムの状態によって高さに差が発生しない
            // 2.デバイスに組み込まれたナビゲーションバー(!hasSoftNavigationBar)は意図的にBaseを0にしているので、矛盾している
            mNavigationBarVerticalOffset = if (navigationBarVerticalDiff != 0
                && mBaseNavigationBarHeight == 0 || !hasSoftNavigationBar
                && mBaseNavigationBarHeight != 0) {
                if (hasSoftNavigationBar) {
                    // 1.auto hide mode -> show mode
                    // 2.show mode -> auto hide mode -> home
                    0
                } else {
                    // show mode -> home
                    -currentNavigationBarHeight
                }
            } else {
                // normal device
                0
            }

            mNavigationBarHorizontalOffset = 0
            return
        }

        // If the portrait, is displayed at the bottom of the screen
        if (isPortrait) {
            // auto hide navigation bar
            mNavigationBarVerticalOffset = if (!hasSoftNavigationBar && mBaseNavigationBarHeight != 0) {
                0
            } else {
                mBaseNavigationBarHeight
            }
            mNavigationBarHorizontalOffset = 0
            return
        }

        // If it is a Tablet, it will appear at the bottom of the screen.
        // If it is Phone, it will appear on the side of the screen
        if (mIsTablet) {
            mNavigationBarVerticalOffset = mBaseNavigationBarRotatedHeight
            mNavigationBarHorizontalOffset = 0
        } else {
            mNavigationBarVerticalOffset = 0
            // auto hide navigation bar
            // 他デバイスとの矛盾をもとに推測する
            // 1.デバイスに組み込まれたナビゲーションバー(!hasSoftNavigationBar)は、意図的にBaseを0にしているので、矛盾している
            mNavigationBarHorizontalOffset = if (!hasSoftNavigationBar && mBaseNavigationBarRotatedHeight != 0) {
                0
            } else if (hasSoftNavigationBar && mBaseNavigationBarRotatedHeight == 0) {
                // 2.ソフトナビゲーションバーの場合、Baseが設定されるため矛盾している
                currentNavigationBarWidth
            } else {
                mBaseNavigationBarRotatedHeight
            }
        }
    }

    /**
     * Update [ViewConfiguration]
     */
    private fun updateViewConfiguration() {
        mViewConfiguration = ViewConfiguration.get(context)
        mMoveThreshold = mViewConfiguration!!.scaledTouchSlop.toFloat()
        mMaximumFlingVelocity = mViewConfiguration!!.scaledMaximumFlingVelocity.toFloat()
        mMaximumXVelocity = mMaximumFlingVelocity / MAX_X_VELOCITY_SCALE_DOWN_VALUE
        mMaximumYVelocity = mMaximumFlingVelocity / MAX_Y_VELOCITY_SCALE_DOWN_VALUE
        mThrowMoveThreshold = mMaximumFlingVelocity / THROW_THRESHOLD_SCALE_DOWN_VALUE
    }

    /**
     * Update the PositionLimitRect and MoveLimitRect according to the screen size change.
     */
    private fun refreshLimitRect() {
        cancelAnimation()

        // 前の画面座標を保存
        val oldPositionLimitWidth = mPositionLimitRect.width()
        val oldPositionLimitHeight = mPositionLimitRect.height()

        // 新しい座標情報に切替
        mWindowManager.defaultDisplay.getMetrics(mMetrics)
        val width = measuredWidth
        val height = measuredHeight
        val newScreenWidth = mMetrics.widthPixels
        val newScreenHeight = mMetrics.heightPixels

        // 移動範囲の設定
        mMoveLimitRect.set(-width, -height * 2, newScreenWidth + width + mNavigationBarHorizontalOffset, newScreenHeight + height + mNavigationBarVerticalOffset)
        mPositionLimitRect.set(-mOverMargin, 0, newScreenWidth - width + mOverMargin + mNavigationBarHorizontalOffset, newScreenHeight - mStatusBarHeight - height + mNavigationBarVerticalOffset)

        // Initial animation stop when the device rotates
        val newRotation = mWindowManager.defaultDisplay.rotation
        if (mAnimateInitialMove && mRotation != newRotation) {
            mIsInitialAnimationRunning = false
        }

        // When animation is running and the device is not rotating
        if (mIsInitialAnimationRunning && mRotation == newRotation) {
            moveToEdge(windowLayoutParams.x, windowLayoutParams.y, true)
        } else {
            // If there is a screen change during the operation, move to the appropriate position
            if (mIsMoveAccept) {
                moveToEdge(windowLayoutParams.x, windowLayoutParams.y, false)
            } else {
                val newX = (windowLayoutParams.x * mPositionLimitRect.width() / oldPositionLimitWidth.toFloat() + 0.5f).toInt()
                val goalPositionX = min(max(mPositionLimitRect.left, newX), mPositionLimitRect.right)
                val newY = (windowLayoutParams.y * mPositionLimitRect.height() / oldPositionLimitHeight.toFloat() + 0.5f).toInt()
                val goalPositionY = min(max(mPositionLimitRect.top, newY), mPositionLimitRect.bottom)
                moveTo(windowLayoutParams.x, windowLayoutParams.y, goalPositionX, goalPositionY, false)
            }
        }
        mRotation = newRotation
    }

    /**
     * {@inheritDoc}
     */
    override fun onDetachedFromWindow() {
        if (mMoveEdgeAnimator != null) {
            mMoveEdgeAnimator!!.removeAllUpdateListeners()
        }
        super.onDetachedFromWindow()
    }

    /**
     * {@inheritDoc}
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Viewが表示されていなければ何もしない
        if (visibility != View.VISIBLE) {
            return true
        }

        // タッチ不能な場合は何もしない
        if (!mIsDraggable) {
            return true
        }

        // Block while initial display animation is running
        if (mIsInitialAnimationRunning) {
            return true
        }

        // 現在位置のキャッシュ
        mScreenTouchX = event.rawX
        mScreenTouchY = event.rawY
        val action = event.action
        var isWaitForMoveToEdge = false
        // 押下
        if (action == MotionEvent.ACTION_DOWN) {
            // アニメーションのキャンセル
            cancelAnimation()
            mScreenTouchDownX = mScreenTouchX
            mScreenTouchDownY = mScreenTouchY
            mLocalTouchX = event.x
            mLocalTouchY = event.y
            mIsMoveAccept = false
            setScale(SCALE_PRESSED)

            if (mVelocityTracker == null) {
                // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                mVelocityTracker = VelocityTracker.obtain()
            } else {
                // Reset the velocity tracker back to its initial state.
                mVelocityTracker!!.clear()
            }

            // タッチトラッキングアニメーションの開始
            mAnimationHandler.updateTouchPosition(xByTouch.toFloat(), yByTouch.toFloat())
            mAnimationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            mAnimationHandler.sendAnimationMessage(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            // 長押し判定の開始
            mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
            mLongPressHandler.sendEmptyMessageDelayed(LongPressHandler.LONG_PRESSED, LONG_PRESS_TIMEOUT.toLong())
            // 押下処理の通過判定のための時間保持
            // mIsDraggableやgetVisibility()のフラグが押下後に変更された場合にMOVE等を処理させないようにするため
            mTouchDownTime = event.downTime
            // compute offset and restore
            addMovement(event)
            mIsInitialAnimationRunning = false
        } else if (action == MotionEvent.ACTION_MOVE) {
            // 移動判定の場合は長押しの解除
            if (mIsMoveAccept) {
                mIsLongPressed = false
                mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
            }
            // 押下処理が行われていない場合は処理しない
            if (mTouchDownTime != event.downTime) {
                return true
            }
            // 移動受付状態でない、かつX,Y軸ともにしきい値よりも小さい場合
            if (!mIsMoveAccept && abs(mScreenTouchX - mScreenTouchDownX) < mMoveThreshold && abs(mScreenTouchY - mScreenTouchDownY) < mMoveThreshold) {
                return true
            }
            mIsMoveAccept = true
            mAnimationHandler.updateTouchPosition(xByTouch.toFloat(), yByTouch.toFloat())
            // compute offset and restore
            addMovement(event)
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // compute velocity tracker
            if (mVelocityTracker != null) {
                mVelocityTracker!!.computeCurrentVelocity(CURRENT_VELOCITY_UNITS)
            }

            // 判定のため長押しの状態を一時的に保持
            val tmpIsLongPressed = mIsLongPressed
            // 長押しの解除
            mIsLongPressed = false
            mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
            // 押下処理が行われていない場合は処理しない
            if (mTouchDownTime != event.downTime) {
                return true
            }
            // アニメーションの削除
            mAnimationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            // 拡大率をもとに戻す
            setScale(SCALE_NORMAL)

            // destroy VelocityTracker (#103)
            if (!mIsMoveAccept && mVelocityTracker != null) {
                mVelocityTracker!!.recycle()
                mVelocityTracker = null
            }

            // When ACTION_UP is done (when not pressed or moved)
            if (action == MotionEvent.ACTION_UP && !tmpIsLongPressed && !mIsMoveAccept) {
                val size = childCount
                for (i in 0 until size) {
                    getChildAt(i).performClick()
                }
            } else {
                // Make a move after checking whether it is finished or not
                isWaitForMoveToEdge = true
            }
        }// 押上、キャンセル
        // 移動

        // タッチリスナを通知
        if (mOnTouchListener != null) {
            mOnTouchListener!!.onTouch(this, event)
        }

        // Lazy execution of moveToEdge
        if (isWaitForMoveToEdge && mAnimationHandler.state != STATE_FINISHING) {
            // include device rotation
            moveToEdge(true)
            if (mVelocityTracker != null) {
                mVelocityTracker!!.recycle()
                mVelocityTracker = null
            }
        }

        return true
    }

    /**
     * Call addMovement and restore MotionEvent coordinate
     *
     * @param event [MotionEvent]
     */
    private fun addMovement(event: MotionEvent) {
        val deltaX = event.rawX - event.x
        val deltaY = event.rawY - event.y
        event.offsetLocation(deltaX, deltaY)
        mVelocityTracker!!.addMovement(event)
        event.offsetLocation(-deltaX, -deltaY)
    }

    /**
     * 長押しされた場合の処理です。
     */
    private fun onLongClick() {
        mIsLongPressed = true
        // 長押し処理
        val size = childCount
        for (i in 0 until size) {
            getChildAt(i).performLongClick()
        }
    }

    /**
     * 画面から消す際の処理を表します。
     */
    override fun setVisibility(visibility: Int) {
        // 画面表示時
        if (visibility != View.VISIBLE) {
            // 画面から消す時は長押しをキャンセルし、画面端に強制的に移動します。
            cancelLongPress()
            setScale(SCALE_NORMAL)
            if (mIsMoveAccept) {
                moveToEdge(false)
            }
            mAnimationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH)
            mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED)
        }
        super.setVisibility(visibility)
    }

    /**
     * {@inheritDoc}
     */
    override fun setOnTouchListener(listener: OnTouchListener) {
        mOnTouchListener = listener
    }

    /**
     * 左右の端に移動します。
     *
     * @param withAnimation アニメーションを行う場合はtrue.行わない場合はfalse
     */
    private fun moveToEdge(withAnimation: Boolean) {
        val currentX = xByTouch
        val currentY = yByTouch
        moveToEdge(currentX, currentY, withAnimation)
    }

    /**
     * 始点を指定して左右の端に移動します。
     *
     * @param startX        X座標の初期値
     * @param startY        Y座標の初期値
     * @param withAnimation アニメーションを行う場合はtrue.行わない場合はfalse
     */
    private fun moveToEdge(startX: Int, startY: Int, withAnimation: Boolean) {
        // 指定座標に移動
        val goalPositionX = getGoalPositionX(startX, startY)
        val goalPositionY = getGoalPositionY(startX, startY)
        moveTo(startX, startY, goalPositionX, goalPositionY, withAnimation)
    }

    /**
     * 指定座標に移動します。<br></br>
     * 画面端の座標を超える場合は、自動的に画面端に移動します。
     *
     * @param currentX      現在のX座標（アニメーションの始点用に使用）
     * @param currentY      現在のY座標（アニメーションの始点用に使用）
     * @param goalPositionX 移動先のX座標
     * @param goalPositionY 移動先のY座標
     * @param withAnimation アニメーションを行う場合はtrue.行わない場合はfalse
     */
    private fun moveTo(currentX: Int, currentY: Int, goalPositionX: Int, goalPositionY: Int, withAnimation: Boolean) {
        var goalPositionX1 = goalPositionX
        var goalPositionY1 = goalPositionY
        // 画面端からはみ出さないように調整
        goalPositionX1 = min(max(mPositionLimitRect.left, goalPositionX1), mPositionLimitRect.right)
        goalPositionY1 = min(max(mPositionLimitRect.top, goalPositionY1), mPositionLimitRect.bottom)
        // アニメーションを行う場合
        if (withAnimation) {
            // Use physics animation
            val usePhysicsAnimation = mUsePhysics && mVelocityTracker != null && mMoveDirection != FloatingViewManager.MOVE_DIRECTION_NEAREST
            if (usePhysicsAnimation) {
                startPhysicsAnimation(goalPositionX1, currentY)
            } else {
                startObjectAnimation(currentX, currentY, goalPositionX1, goalPositionY1)
            }
        } else {
            // 位置が変化した時のみ更新
            if (windowLayoutParams.x != goalPositionX1 || windowLayoutParams.y != goalPositionY1) {
                windowLayoutParams.x = goalPositionX1
                windowLayoutParams.y = goalPositionY1
                updateViewLayout()
            }
        }
        // タッチ座標を初期化
        mLocalTouchX = 0f
        mLocalTouchY = 0f
        mScreenTouchDownX = 0f
        mScreenTouchDownY = 0f
        mIsMoveAccept = false
    }

    /**
     * Start Physics-based animation
     *
     * @param goalPositionX goal position X coordinate
     * @param currentY      current Y coordinate
     */
    private fun startPhysicsAnimation(goalPositionX: Int, currentY: Int) {
        // start X coordinate animation
        val containsLimitRectWidth = windowLayoutParams.x < mPositionLimitRect.right && windowLayoutParams.x > mPositionLimitRect.left
        // If MOVE_DIRECTION_NONE, play fling animation
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NONE && containsLimitRectWidth) {
            val velocityX = min(max(mVelocityTracker!!.xVelocity, -mMaximumXVelocity), mMaximumXVelocity)
            startFlingAnimationX(velocityX)
        } else {
            startSpringAnimationX(goalPositionX)
        }

        // start Y coordinate animation
        val containsLimitRectHeight = windowLayoutParams.y < mPositionLimitRect.bottom && windowLayoutParams.y > mPositionLimitRect.top
        val velocityY = -min(max(mVelocityTracker!!.yVelocity, -mMaximumYVelocity), mMaximumYVelocity)
        if (containsLimitRectHeight) {
            startFlingAnimationY(velocityY)
        } else {
            startSpringAnimationY(currentY, velocityY)
        }
    }

    /**
     * Start object animation
     *
     * @param currentX      current X coordinate
     * @param currentY      current Y coordinate
     * @param goalPositionX goal position X coordinate
     * @param goalPositionY goal position Y coordinate
     */
    private fun startObjectAnimation(currentX: Int, currentY: Int, goalPositionX: Int, goalPositionY: Int) {
        if (goalPositionX == currentX) {
            //to move only y coord
            mMoveEdgeAnimator = ValueAnimator.ofInt(currentY, goalPositionY)
            mMoveEdgeAnimator!!.addUpdateListener { animation ->
                windowLayoutParams.y = animation.animatedValue as Int
                updateViewLayout()
                updateInitAnimation(animation)
            }
        } else {
            // To move only x coord (to left or right)
            windowLayoutParams.y = goalPositionY
            mMoveEdgeAnimator = ValueAnimator.ofInt(currentX, goalPositionX)
            mMoveEdgeAnimator!!.addUpdateListener { animation ->
                windowLayoutParams.x = animation.animatedValue as Int
                updateViewLayout()
                updateInitAnimation(animation)
            }
        }
        // X軸のアニメーション設定
        mMoveEdgeAnimator!!.duration = MOVE_TO_EDGE_DURATION
        mMoveEdgeAnimator!!.interpolator = mMoveEdgeInterpolator
        mMoveEdgeAnimator!!.start()
    }

    /**
     * Start spring animation(X coordinate)
     *
     * @param goalPositionX goal position X coordinate
     */
    private fun startSpringAnimationX(goalPositionX: Int) {
        // springX
        val springX = SpringForce(goalPositionX.toFloat())
        springX.dampingRatio = ANIMATION_SPRING_X_DAMPING_RATIO
        springX.stiffness = ANIMATION_SPRING_X_STIFFNESS
        // springAnimation
        SpringAnimation(FloatValueHolder()).apply {
            setStartVelocity(mVelocityTracker!!.xVelocity)
            setStartValue(windowLayoutParams.x.toFloat())
            spring = springX
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
            addUpdateListener(DynamicAnimation.OnAnimationUpdateListener { _, value, _ ->
                val x = value.roundToInt()
                // Not moving, or the touch operation is continuing
                if (windowLayoutParams.x == x || mVelocityTracker != null) {
                    return@OnAnimationUpdateListener
                }
                // update x coordinate
                windowLayoutParams.x = x
                updateViewLayout()
            })
            start()
        }
    }

    /**
     * Start spring animation(Y coordinate)
     *
     * @param currentY  current Y coordinate
     * @param velocityY velocity Y coordinate
     */
    private fun startSpringAnimationY(currentY: Int, velocityY: Float) {
        // Create SpringForce
        val springY = SpringForce((if (currentY < mMetrics.heightPixels / 2) mPositionLimitRect.top else mPositionLimitRect.bottom).toFloat())
        springY.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        springY.stiffness = SpringForce.STIFFNESS_LOW

        // Create SpringAnimation
        SpringAnimation(FloatValueHolder()).apply {
            setStartVelocity(velocityY)
            setStartValue(windowLayoutParams.y.toFloat())
            spring = springY
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
            addUpdateListener(DynamicAnimation.OnAnimationUpdateListener { _, value, _ ->
                val y = value.roundToInt()
                // Not moving, or the touch operation is continuing
                if (windowLayoutParams.y == y || mVelocityTracker != null) {
                    return@OnAnimationUpdateListener
                }
                // update y coordinate
                windowLayoutParams.y = y
                updateViewLayout()
            })
            start()
        }
    }

    /**
     * Start fling animation(X coordinate)
     *
     * @param velocityX velocity X coordinate
     */
    private fun startFlingAnimationX(velocityX: Float) {
        FlingAnimation(FloatValueHolder()).apply {
            setStartVelocity(velocityX)
            setMaxValue(mPositionLimitRect.right.toFloat())
            setMinValue(mPositionLimitRect.left.toFloat())
            setStartValue(windowLayoutParams.x.toFloat())
            friction = ANIMATION_FLING_X_FRICTION
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
            addUpdateListener(DynamicAnimation.OnAnimationUpdateListener { _, value, _ ->
                val x = value.roundToInt()
                // Not moving, or the touch operation is continuing
                if (windowLayoutParams.x == x || mVelocityTracker != null) {
                    return@OnAnimationUpdateListener
                }
                // update y coordinate
                windowLayoutParams.x = x
                updateViewLayout()
            })
            start()
        }
    }

    /**
     * Start fling animation(Y coordinate)
     *
     * @param velocityY velocity Y coordinate
     */
    private fun startFlingAnimationY(velocityY: Float) {
        FlingAnimation(FloatValueHolder()).apply {
            setStartVelocity(velocityY)
            setMaxValue(mPositionLimitRect.bottom.toFloat())
            setMinValue(mPositionLimitRect.top.toFloat())
            setStartValue(windowLayoutParams.y.toFloat())
            friction = ANIMATION_FLING_Y_FRICTION
            minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
            addUpdateListener(DynamicAnimation.OnAnimationUpdateListener { _, value, _ ->
                val y = value.roundToInt()
                // Not moving, or the touch operation is continuing
                if (windowLayoutParams.y == y || mVelocityTracker != null) {
                    return@OnAnimationUpdateListener
                }
                // update y coordinate
                windowLayoutParams.y = y
                updateViewLayout()
            })
            start()
        }
    }

    /**
     * Check if it is attached to the Window and call WindowManager.updateLayout()
     */
    private fun updateViewLayout() {
        if (!ViewCompat.isAttachedToWindow(this)) {
            return
        }
        mWindowManager.updateViewLayout(this, windowLayoutParams)
        realTimePositionListener!!.currentPosition()
    }

    /**
     * Update animation initialization flag
     *
     * @param animation [ValueAnimator]
     */
    private fun updateInitAnimation(animation: ValueAnimator) {
        if (mAnimateInitialMove && animation.duration <= animation.currentPlayTime) {
            mIsInitialAnimationRunning = false
        }
    }

    /**
     * Get the final point of movement (X coordinate)
     *
     * @param startX Initial value of X coordinate
     * @param startY Initial value of Y coordinate
     * @return End point of X coordinate
     */
    private fun getGoalPositionX(startX: Int, startY: Int): Int {
        var goalPositionX = startX

        // Move to left or right edges
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_DEFAULT) {
            val isMoveRightEdge = startX > (mMetrics.widthPixels - width) / 2
            goalPositionX = if (isMoveRightEdge) mPositionLimitRect.right else mPositionLimitRect.left
        } else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_LEFT) {
            goalPositionX = mPositionLimitRect.left
        } else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_RIGHT) {
            goalPositionX = mPositionLimitRect.right
        } else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NEAREST) {
            val distLeftRight = min(startX, mPositionLimitRect.width() - startX)
            val distTopBottom = min(startY, mPositionLimitRect.height() - startY)
            if (distLeftRight < distTopBottom) {
                val isMoveRightEdge = startX > (mMetrics.widthPixels - width) / 2
                goalPositionX = if (isMoveRightEdge) mPositionLimitRect.right else mPositionLimitRect.left
            }
        } else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_THROWN) {
            goalPositionX = if (mVelocityTracker != null && mVelocityTracker!!.xVelocity > mThrowMoveThreshold) {
                mPositionLimitRect.right
            } else if (mVelocityTracker != null && mVelocityTracker!!.xVelocity < -mThrowMoveThreshold) {
                mPositionLimitRect.left
            } else {
                val isMoveRightEdge = startX > (mMetrics.widthPixels - width) / 2
                if (isMoveRightEdge) mPositionLimitRect.right else mPositionLimitRect.left
            }
        }// Move in the direction in which it is thrown
        // Move to top/bottom/left/right edges
        // Move to right edges
        // Move to left edges

        return goalPositionX
    }

    /**
     * Get the final point of movement (Y coordinate)
     *
     * @param startX Initial value of X coordinate
     * @param startY Initial value of Y coordinate
     * @return End point of Y coordinate
     */
    private fun getGoalPositionY(startX: Int, startY: Int): Int {
        var goalPositionY = startY

        // Move to top/bottom/left/right edges
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NEAREST) {
            val distLeftRight = min(startX, mPositionLimitRect.width() - startX)
            val distTopBottom = min(startY, mPositionLimitRect.height() - startY)
            if (distLeftRight >= distTopBottom) {
                val isMoveTopEdge = startY < (mMetrics.heightPixels - height) / 2
                goalPositionY = if (isMoveTopEdge) mPositionLimitRect.top else mPositionLimitRect.bottom
            }
        }

        return goalPositionY
    }

    /**
     * アニメーションをキャンセルします。
     */
    private fun cancelAnimation() {
        if (mMoveEdgeAnimator != null && mMoveEdgeAnimator!!.isStarted) {
            mMoveEdgeAnimator!!.cancel()
            mMoveEdgeAnimator = null
        }
    }

    /**
     * 拡大・縮小を行います。
     *
     * @param newScale 設定する拡大率
     */
    private fun setScale(newScale: Float) {
        // INFO:childにscaleを設定しないと拡大率が変わらない現象に対処するための修正
        scaleX = newScale
        scaleY = newScale
    }

    /**
     * ドラッグ可能フラグ
     *
     * @param isDraggable ドラッグ可能にする場合はtrue
     */
    fun setDraggable(isDraggable: Boolean) {
        mIsDraggable = isDraggable
    }

    /**
     * 画面端をオーバーするマージンです。
     *
     * @param margin マージン
     */
    fun setOverMargin(margin: Int) {
        mOverMargin = margin
    }

    /**
     * 移動方向を設定します。
     *
     * @param moveDirection 移動方向
     */
    fun setMoveDirection(moveDirection: Int) {
        mMoveDirection = moveDirection
    }

    /**
     * Use dynamic physics-based animations or not
     * Warning: Can not be used before API 16
     *
     * @param usePhysics Setting this to false will revert to using a ValueAnimator (default is true)
     */
    fun usePhysics(usePhysics: Boolean) {
        mUsePhysics = usePhysics
    }

    /**
     * 初期座標を設定します。
     *
     * @param x FloatingViewの初期X座標
     * @param y FloatingViewの初期Y座標
     */
    fun setInitCoords(x: Int, y: Int) {
        mInitX = x
        mInitY = y
    }

    /**
     * 初期表示時にアニメーションするフラグを設定します。
     *
     * @param animateInitialMove 初期表示時にアニメーションする場合はtrue
     */
    fun setAnimateInitialMove(animateInitialMove: Boolean) {
        mAnimateInitialMove = animateInitialMove
    }

    /**
     * Window上での描画領域を取得します。
     *
     * @param outRect 変更を加えるRect
     */
    fun getWindowDrawingRect(outRect: Rect) {
        val currentX = xByTouch
        val currentY = yByTouch
        outRect.set(currentX, currentY, currentX + width, currentY + height)
    }

    /**
     * 通常状態に変更します。
     */
    fun setNormal() {
        mAnimationHandler.state = STATE_NORMAL
        mAnimationHandler.updateTouchPosition(xByTouch.toFloat(), yByTouch.toFloat())
    }

    /**
     * 重なった状態に変更します。
     *
     * @param centerX 対象の中心座標X
     * @param centerY 対象の中心座標Y
     */
    fun setIntersecting(centerX: Int, centerY: Int) {
        mAnimationHandler.state = STATE_INTERSECTING
        mAnimationHandler.updateTargetPosition(centerX.toFloat(), centerY.toFloat())
    }

    /**
     * 終了状態に変更します。
     */
    fun setFinishing() {
        mAnimationHandler.state = STATE_FINISHING
        mIsMoveAccept = false
        visibility = View.GONE
    }

    /**
     * Set the cutout's safe inset area
     *
     * @param safeInsetRect [FloatingViewManager.setSafeInsetRect]
     */
    fun setSafeInsetRect(safeInsetRect: Rect) {
        mSafeInsetRect.set(safeInsetRect)
    }

    /**
     * アニメーションの制御を行うハンドラです。
     */
    internal class FloatingAnimationHandler
    /**
     * コンストラクタ
     */
    (floatingView: FloatingView) : Handler() {

        /**
         * アニメーションを開始した時間
         */
        private var mStartTime: Long = 0

        /**
         * アニメーションを始めた時点のTransitionX
         */
        private var mStartX: Float = 0.toFloat()

        /**
         * アニメーションを始めた時点のTransitionY
         */
        private var mStartY: Float = 0.toFloat()

        /**
         * 実行中のアニメーションのコード
         */
        private var mStartedCode: Int = 0

        /**
         * アニメーション状態フラグ
         */
        private var mState: Int = 0

        /**
         * 現在の状態
         */
        private var mIsChangeState: Boolean = false

        /**
         * 追従対象のX座標
         */
        private var mTouchPositionX: Float = 0.toFloat()

        /**
         * 追従対象のY座標
         */
        private var mTouchPositionY: Float = 0.toFloat()

        /**
         * 追従対象のX座標
         */
        private var mTargetPositionX: Float = 0.toFloat()

        /**
         * 追従対象のY座標
         */
        private var mTargetPositionY: Float = 0.toFloat()

        /**
         * FloatingView
         */
        private val mFloatingView: WeakReference<FloatingView> = WeakReference(floatingView)

        /**
         * 現在の状態を返します。
         *
         * @return STATE_NORMAL or STATE_INTERSECTING or STATE_FINISHING
         */
        /**
         * アニメーション状態を設定します。
         *
         * @param newState STATE_NORMAL or STATE_INTERSECTING or STATE_FINISHING
         */
        // 状態が異なった場合のみ状態を変更フラグを変える
        var state: Int
            get() = mState
            set(@AnimationState newState) {
                if (mState != newState) {
                    mIsChangeState = true
                }
                mState = newState
            }

        init {
            mStartedCode = ANIMATION_NONE
            mState = STATE_NORMAL
        }

        /**
         * アニメーションの処理を行います。
         */
        override fun handleMessage(msg: Message) {
            val floatingView = mFloatingView.get()
            if (floatingView == null) {
                removeMessages(ANIMATION_IN_TOUCH)
                return
            }

            val animationCode = msg.what
            val animationType = msg.arg1
            val params = floatingView.windowLayoutParams

            // 状態変更またはアニメーションを開始した場合の初期化
            if (mIsChangeState || animationType == TYPE_FIRST) {
                // 状態変更時のみアニメーション時間を使う
                mStartTime = if (mIsChangeState) SystemClock.uptimeMillis() else 0
                mStartX = params.x.toFloat()
                mStartY = params.y.toFloat()
                mStartedCode = animationCode
                mIsChangeState = false
            }
            // 経過時間
            val elapsedTime = (SystemClock.uptimeMillis() - mStartTime).toFloat()
            val trackingTargetTimeRate = min(elapsedTime / CAPTURE_DURATION_MILLIS, 1.0f)

            // 重なっていない場合のアニメーション
            if (mState == FloatingView.STATE_NORMAL) {
                val basePosition = calcAnimationPosition(trackingTargetTimeRate)
                // 画面外へのオーバーを認める
                val moveLimitRect = floatingView.mMoveLimitRect
                // 最終的な到達点
                val targetPositionX = min(max(moveLimitRect.left, mTouchPositionX.toInt()), moveLimitRect.right).toFloat()
                val targetPositionY = min(max(moveLimitRect.top, mTouchPositionY.toInt()), moveLimitRect.bottom).toFloat()
                params.x = (mStartX + (targetPositionX - mStartX) * basePosition).toInt()
                params.y = (mStartY + (targetPositionY - mStartY) * basePosition).toInt()
                floatingView.updateViewLayout()
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
            } else if (mState == FloatingView.STATE_INTERSECTING) {
                val basePosition = calcAnimationPosition(trackingTargetTimeRate)
                // 最終的な到達点
                val targetPositionX = mTargetPositionX - floatingView.width / 2
                val targetPositionY = mTargetPositionY - floatingView.height / 2
                // 現在地からの移動
                params.x = (mStartX + (targetPositionX - mStartX) * basePosition).toInt()
                params.y = (mStartY + (targetPositionY - mStartY) * basePosition).toInt()
                floatingView.updateViewLayout()
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
            }// 重なった場合のアニメーション

        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation   ANIMATION_IN_TOUCH
         * @param delayMillis メッセージの送信時間
         */
        fun sendAnimationMessageDelayed(animation: Int, delayMillis: Long) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis)
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation ANIMATION_IN_TOUCH
         */
        fun sendAnimationMessage(animation: Int) {
            sendMessage(newMessage(animation, TYPE_FIRST))
        }

        /**
         * タッチ座標の位置を更新します。
         *
         * @param positionX タッチX座標
         * @param positionY タッチY座標
         */
        fun updateTouchPosition(positionX: Float, positionY: Float) {
            mTouchPositionX = positionX
            mTouchPositionY = positionY
        }

        /**
         * 追従対象の位置を更新します。
         *
         * @param centerX 追従対象のX座標
         * @param centerY 追従対象のY座標
         */
        fun updateTargetPosition(centerX: Float, centerY: Float) {
            mTargetPositionX = centerX
            mTargetPositionY = centerY
        }

        companion object {

            /**
             * アニメーションをリフレッシュするミリ秒
             */
            private const val ANIMATION_REFRESH_TIME_MILLIS = 10L

            /**
             * FloatingViewの吸着の着脱時間
             */
            private const val CAPTURE_DURATION_MILLIS = 300L

            /**
             * アニメーションなしの状態を表す定数
             */
            private const val ANIMATION_NONE = 0

            /**
             * タッチ時に発生するアニメーションの定数
             */
            const val ANIMATION_IN_TOUCH = 1

            /**
             * アニメーション開始を表す定数
             */
            private const val TYPE_FIRST = 1
            /**
             * アニメーション更新を表す定数
             */
            private const val TYPE_UPDATE = 2

            /**
             * アニメーション時間から求められる位置を計算します。
             *
             * @param timeRate 時間比率
             * @return ベースとなる係数(0.0から1.0 ＋ α)
             */
            private fun calcAnimationPosition(timeRate: Float): Float {
                // y=0.55sin(8.0564x-π/2)+0.55
                // y=4(0.417x-0.341)^2-4(0.417-0.341)^2+1
                return if (timeRate <= 0.4) {
                    (0.55 * sin(8.0564 * timeRate - Math.PI / 2) + 0.55).toFloat()
                } else {
                    (4 * (0.417 * timeRate - 0.341).pow(2.0) - 4 * (0.417 - 0.341).pow(2.0) + 1).toFloat()
                }
            }

            /**
             * 送信するメッセージを生成します。
             *
             * @param animation ANIMATION_IN_TOUCH
             * @param type      TYPE_FIRST,TYPE_UPDATE
             * @return Message
             */
            private fun newMessage(animation: Int, type: Int): Message {
                val message = Message.obtain()
                message.what = animation
                message.arg1 = type
                return message
            }
        }
    }

    /**
     * 長押し処理を制御するハンドラです。<br></br>
     * dispatchTouchEventで全てのタッチ処理を実装しているので、長押しも独自実装しています。
     */
    internal class LongPressHandler
    /**
     * コンストラクタ
     *
     * @param view FloatingView
     */
    (view: FloatingView) : Handler() {

        /**
         * TrashView
         */
        private val mFloatingView: WeakReference<FloatingView> = WeakReference(view)

        override fun handleMessage(msg: Message) {
            val view = mFloatingView.get()
            if (view == null) {
                removeMessages(LONG_PRESSED)
                return
            }

            view.onLongClick()
        }

        companion object {

            /**
             * アニメーションなしの状態を表す定数
             */
            const val LONG_PRESSED = 0
        }
    }

    fun setRealTimePositionListener(realTimePositionListener: RealTimePositionListener) {
        this.realTimePositionListener = realTimePositionListener
    }

    internal interface RealTimePositionListener {
        fun currentPosition()
    }

    companion object {

        /**
         * 押下時の拡大率
         */
        private const val SCALE_PRESSED = 0.9f

        /**
         * 通常時の拡大率
         */
        private const val SCALE_NORMAL = 1.0f

        /**
         * 画面端移動アニメーションの時間
         */
        private const val MOVE_TO_EDGE_DURATION = 450L

        /**
         * 画面端移動アニメーションの係数
         */
        private const val MOVE_TO_EDGE_OVERSHOOT_TENSION = 1.25f

        /**
         * Damping ratio constant for spring animation (X coordinate)
         */
        private const val ANIMATION_SPRING_X_DAMPING_RATIO = 0.7f

        /**
         * Stiffness constant for spring animation (X coordinate)
         */
        private const val ANIMATION_SPRING_X_STIFFNESS = 350f

        /**
         * Friction constant for fling animation (X coordinate)
         */
        private const val ANIMATION_FLING_X_FRICTION = 1.7f

        /**
         * Friction constant for fling animation (Y coordinate)
         */
        private const val ANIMATION_FLING_Y_FRICTION = 1.7f

        /**
         * Current velocity units
         */
        private const val CURRENT_VELOCITY_UNITS = 1000

        /**
         * 通常状態
         */
        const val STATE_NORMAL = 0

        /**
         * 重なり状態
         */
        const val STATE_INTERSECTING = 1

        /**
         * 終了状態
         */
        const val STATE_FINISHING = 2

        /**
         * 長押し判定とする時間(移動操作も考慮して通常の1.5倍)
         */
        private val LONG_PRESS_TIMEOUT = (1.5f * ViewConfiguration.getLongPressTimeout()).toInt()

        /**
         * Constant for scaling down X coordinate velocity
         */
        private const val MAX_X_VELOCITY_SCALE_DOWN_VALUE = 9f

        /**
         * Constant for scaling down Y coordinate velocity
         */
        private const val MAX_Y_VELOCITY_SCALE_DOWN_VALUE = 8f

        /**
         * Constant for calculating the threshold to move when throwing
         */
        private const val THROW_THRESHOLD_SCALE_DOWN_VALUE = 9f

        /**
         * デフォルトのX座標を表す値
         */
        const val DEFAULT_X = Integer.MIN_VALUE

        /**
         * デフォルトのY座標を表す値
         */
        const val DEFAULT_Y = Integer.MIN_VALUE

        /**
         * Default width size
         */
        const val DEFAULT_WIDTH = ViewGroup.LayoutParams.WRAP_CONTENT

        /**
         * Default height size
         */
        const val DEFAULT_HEIGHT = ViewGroup.LayoutParams.WRAP_CONTENT

        /**
         * Overlay Type
         */
        private val OVERLAY_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
        }

        /**
         * Get the System ui dimension(pixel)
         *
         * @param resources [Resources]
         * @param resName   dimension resource name
         * @return pixel size
         */
        private fun getSystemUiDimensionPixelSize(resources: Resources, resName: String): Int {
            var pixelSize = 0
            val resId = resources.getIdentifier(resName, "dimen", "android")
            if (resId > 0) {
                pixelSize = resources.getDimensionPixelSize(resId)
            }
            return pixelSize
        }
    }
}
