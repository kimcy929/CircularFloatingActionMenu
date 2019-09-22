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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import java.util.*

/**
 * FloatingViewを扱うクラスです。
 * TODO:動作がカクカクなので原因を探す
 * TODO:移動を追従する複数表示サポートは第2弾で対応
 */
class FloatingViewManager
/**
 * コンストラクタ
 *
 * @param context  Context
 * @param listener FloatingViewListener
 */
(
    /**
     * [Context]
     */
    private val mContext: Context,
    /**
     * FloatingViewListener
     */
    private val mFloatingViewListener: FloatingViewListener?) : ScreenChangedListener, View.OnTouchListener, TrashViewListener, FloatingView.RealTimePositionListener {

    /**
     * [Resources]
     */
    private val mResources = mContext.resources

    /**
     * WindowManager
     */
    private val mWindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * [DisplayMetrics]
     */
    private val mDisplayMetrics = DisplayMetrics()

    /**
     * 操作状態のFloatingView
     */
    private var mTargetFloatingView: FloatingView? = null

    /**
     * フルスクリーンを監視するViewです。
     */
    private val mFullscreenObserverView: FullscreenObserverView

    /**
     * FloatingViewを削除するViewです。
     */
    private val mTrashView: TrashView

    /**
     * FloatingViewの当たり判定用矩形
     */
    private val mFloatingViewRect = Rect()

    /**
     * TrashViewの当たり判定用矩形
     */
    private val mTrashViewRect = Rect()

    /**
     * タッチの移動を許可するフラグ
     * 画面回転時にタッチ処理を受け付けないようにするためのフラグです
     */
    private var mIsMoveAccept: Boolean = false

    /**
     * 現在の表示モード
     */
    @DisplayMode
    private var mDisplayMode: Int = 0

    /**
     * Cutout safe inset rect
     */
    private val mSafeInsetRect: Rect

    /**
     * Windowに貼り付けられたFloatingViewのリスト
     * TODO:第2弾のFloatingViewの複数表示で意味を発揮する予定
     */
    private val mFloatingViewList: ArrayList<FloatingView>

    /**
     * 削除Viewと重なっているかチェックします。
     *
     * @return 削除Viewと重なっている場合はtrue
     */
    private// 無効の場合は重なり判定を行わない
    // INFO:TrashViewとFloatingViewは同じGravityにする必要があります
    val isIntersectWithTrash: Boolean
        get() {
            if (!mTrashView.isTrashEnabled) {
                return false
            }
            mTrashView.getWindowDrawingRect(mTrashViewRect)
            mTargetFloatingView!!.getWindowDrawingRect(mFloatingViewRect)
            return Rect.intersects(mTrashViewRect, mFloatingViewRect)
        }

    /**
     * TrashViewの表示非表示状態を取得します。
     *
     * @return trueの場合は表示状態（重なり判定が有効の状態）
     */
    /**
     * TrashViewの表示・非表示を設定します。
     *
     * @param enabled trueの場合は表示
     */
    var isTrashViewEnabled: Boolean
        get() = mTrashView.isTrashEnabled
        set(enabled) {
            mTrashView.isTrashEnabled = enabled
        }

    /**
     * 表示モード
     */
    @IntDef(DISPLAY_MODE_SHOW_ALWAYS, DISPLAY_MODE_HIDE_ALWAYS, DISPLAY_MODE_HIDE_FULLSCREEN)
    @Retention(AnnotationRetention.SOURCE)
    annotation class DisplayMode

    /**
     * Moving direction
     */
    @IntDef(MOVE_DIRECTION_DEFAULT, MOVE_DIRECTION_LEFT, MOVE_DIRECTION_RIGHT, MOVE_DIRECTION_NEAREST, MOVE_DIRECTION_NONE, MOVE_DIRECTION_THROWN)
    @Retention(AnnotationRetention.SOURCE)
    annotation class MoveDirection

    init {
        mIsMoveAccept = false
        mDisplayMode = DISPLAY_MODE_HIDE_FULLSCREEN
        mSafeInsetRect = Rect()

        // FloatingViewと連携するViewの構築
        mFloatingViewList = ArrayList()
        mFullscreenObserverView = FullscreenObserverView(mContext, this)
        mTrashView = TrashView(mContext)
        //mTrashView.setTrashEnabled(false);
    }

    /**
     * 画面がフルスクリーンになった場合はViewを非表示にします。
     */
    override fun onScreenChanged(windowRect: Rect, visibility: Int) {
        // detect status bar
        val isFitSystemWindowTop = windowRect.top == 0
        val isHideStatusBar: Boolean
        isHideStatusBar = isFitSystemWindowTop

        // detect navigation bar
        val isHideNavigationBar = if (visibility == FullscreenObserverView.NO_LAST_VISIBILITY) {
            // At the first it can not get the correct value, so do special processing
            mWindowManager.defaultDisplay.getRealMetrics(mDisplayMetrics)
            windowRect.width() - mDisplayMetrics.widthPixels == 0 && windowRect.bottom - mDisplayMetrics.heightPixels == 0
        } else {
            visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }

        val isPortrait = mResources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        // update FloatingView layout
        mTargetFloatingView!!.onUpdateSystemLayout(isHideStatusBar, isHideNavigationBar, isPortrait, windowRect)

        // フルスクリーンでの非表示モードでない場合は何もしない
        if (mDisplayMode != DISPLAY_MODE_HIDE_FULLSCREEN) {
            return
        }

        mIsMoveAccept = false
        val state = mTargetFloatingView!!.state
        // 重なっていない場合は全て非表示処理
        if (state == FloatingView.STATE_NORMAL) {
            val size = mFloatingViewList.size
            for (i in 0 until size) {
                val floatingView = mFloatingViewList[i]
                floatingView.visibility = if (isFitSystemWindowTop) View.GONE else View.VISIBLE
            }
            mTrashView.dismiss()
        } else if (state == FloatingView.STATE_INTERSECTING) {
            mTargetFloatingView!!.setFinishing()
            mTrashView.dismiss()
        }// 重なっている場合は削除
    }

    /**
     * Update ActionTrashIcon
     */
    override fun onUpdateActionTrashIcon() {
        mTrashView.updateActionTrashIcon(mTargetFloatingView!!.measuredWidth.toFloat(), mTargetFloatingView!!.measuredHeight.toFloat(), mTargetFloatingView!!.shape)
    }

    /**
     * FloatingViewのタッチをロックします。
     */
    override fun onTrashAnimationStarted(@TrashView.AnimationState animationCode: Int) {
        // クローズまたは強制クローズの場合はすべてのFloatingViewをタッチさせない
        if (animationCode == TrashView.ANIMATION_CLOSE || animationCode == TrashView.ANIMATION_FORCE_CLOSE) {
            val size = mFloatingViewList.size
            for (i in 0 until size) {
                val floatingView = mFloatingViewList[i]
                floatingView.setDraggable(false)
            }
        }
    }

    /**
     * FloatingViewのタッチロックの解除を行います。
     */
    override fun onTrashAnimationEnd(@TrashView.AnimationState animationCode: Int) {

        val state = mTargetFloatingView!!.state
        // 終了していたらViewを削除する
        if (state == FloatingView.STATE_FINISHING) {
            removeViewToWindow(mTargetFloatingView!!)
        }

        // すべてのFloatingViewのタッチ状態を戻す
        val size = mFloatingViewList.size
        for (i in 0 until size) {
            val floatingView = mFloatingViewList[i]
            floatingView.setDraggable(true)
        }

    }

    /**
     * 削除ボタンの表示・非表示を処理します。
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val action = event.action

        // 押下状態でないのに移動許可が出ていない場合はなにもしない(回転直後にACTION_MOVEが来て、FloatingViewが消えてしまう現象に対応)
        if (action != MotionEvent.ACTION_DOWN && !mIsMoveAccept) {
            return false
        }

        val state = mTargetFloatingView!!.state
        mTargetFloatingView = v as FloatingView

        // 押下
        if (action == MotionEvent.ACTION_DOWN) {
            // 処理なし
            mIsMoveAccept = true
        } else if (action == MotionEvent.ACTION_MOVE) {
            // 今回の状態
            val isIntersecting = isIntersectWithTrash
            // これまでの状態
            val isIntersect = state == FloatingView.STATE_INTERSECTING
            // 重なっている場合は、FloatingViewをTrashViewに追従させる
            if (isIntersecting) {
                mTargetFloatingView!!.setIntersecting(mTrashView.trashIconCenterX.toInt(), mTrashView.trashIconCenterY.toInt())
            }
            // 重なり始めの場合
            if (isIntersecting && !isIntersect) {
                mTargetFloatingView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                mTrashView.setScaleTrashIcon(true)
            } else if (!isIntersecting && isIntersect) {
                mTargetFloatingView!!.setNormal()
                mTrashView.setScaleTrashIcon(false)
            }// 重なり終わりの場合
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 重なっている場合
            if (state == FloatingView.STATE_INTERSECTING) {
                // FloatingViewを削除し、拡大状態を解除
                mTargetFloatingView!!.setFinishing()
                mTrashView.setScaleTrashIcon(false)
            }
            mIsMoveAccept = false

            // Touch finish callback
            if (mFloatingViewListener != null) {
                rewritePositionY()
            }
        }// 押上、キャンセル
        // 移動

        // TrashViewにイベントを通知
        // 通常状態の場合は指の位置を渡す
        // 重なっている場合はTrashViewの位置を渡す
        if (state == FloatingView.STATE_INTERSECTING) {
            mTrashView.onTouchFloatingView(event, mFloatingViewRect.left.toFloat(), mFloatingViewRect.top.toFloat())
        } else {
            val params = mTargetFloatingView!!.windowLayoutParams
            mTrashView.onTouchFloatingView(event, params.x.toFloat(), params.y.toFloat())
        }

        return false
    }

    /**
     * 固定削除アイコンの画像を設定します。
     *
     * @param resId drawable ID
     */
    fun setFixedTrashIconImage(@DrawableRes resId: Int) {
        mTrashView.setFixedTrashIconImage(resId)
    }

    /**
     * アクションする削除アイコンの画像を設定します。
     *
     * @param resId drawable ID
     */
    fun setActionTrashIconImage(@DrawableRes resId: Int) {
        mTrashView.setActionTrashIconImage(resId)
    }

    /**
     * 固定削除アイコンを設定します。
     *
     * @param drawable Drawable
     */
    fun setFixedTrashIconImage(drawable: Drawable) {
        mTrashView.setFixedTrashIconImage(drawable)
    }

    /**
     * アクション用削除アイコンを設定します。
     *
     * @param drawable Drawable
     */
    fun setActionTrashIconImage(drawable: Drawable) {
        mTrashView.setActionTrashIconImage(drawable)
    }

    /**
     * 表示モードを変更します。
     *
     * @param displayMode [.DISPLAY_MODE_SHOW_ALWAYS] or [.DISPLAY_MODE_HIDE_ALWAYS] or [.DISPLAY_MODE_HIDE_FULLSCREEN]
     */
    fun setDisplayMode(@DisplayMode displayMode: Int) {
        mDisplayMode = displayMode
        // 常に表示/フルスクリーン時に非表示にするモードの場合
        if (mDisplayMode == DISPLAY_MODE_SHOW_ALWAYS || mDisplayMode == DISPLAY_MODE_HIDE_FULLSCREEN) {
            for (floatingView in mFloatingViewList) {
                floatingView.visibility = View.VISIBLE
            }
        } else if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            for (floatingView in mFloatingViewList) {
                floatingView.visibility = View.GONE
            }
            mTrashView.dismiss()
        }// 常に非表示にするモードの場合
    }

    /**
     * Set the DisplayCutout's safe area
     * Note:You must set the Cutout obtained on portrait orientation.
     *
     * @param safeInsetRect DisplayCutout#getSafeInsetXXX
     */
    fun setSafeInsetRect(safeInsetRect: Rect?) {
        if (safeInsetRect == null) {
            mSafeInsetRect.setEmpty()
        } else {
            mSafeInsetRect.set(safeInsetRect)
        }

        val size = mFloatingViewList.size
        if (size == 0) {
            return
        }

        // update floating view
        for (i in 0 until size) {
            val floatingView = mFloatingViewList[i]
            floatingView.setSafeInsetRect(mSafeInsetRect)
        }
        // dirty hack
        mFullscreenObserverView.onGlobalLayout()
    }

    /**
     * ViewをWindowに貼り付けます。
     *
     * @param view    フローティングさせるView
     * @param options Options
     */
    @SuppressLint("ClickableViewAccessibility")
    fun addViewToWindow(view: View, options: Options) {
        val isFirstAttach = mFloatingViewList.isEmpty()
        // FloatingView
        val floatingView = FloatingView(mContext)
        floatingView.setInitCoords(options.floatingViewX, options.floatingViewY)
        floatingView.setOnTouchListener(this)
        floatingView.setRealTimePositionListener(this)
        floatingView.shape = options.shape
        floatingView.setOverMargin(options.overMargin)
        floatingView.setMoveDirection(options.moveDirection)
        floatingView.usePhysics(options.usePhysics)
        floatingView.setAnimateInitialMove(options.animateInitialMove)
        floatingView.setSafeInsetRect(mSafeInsetRect)

        // set FloatingView size
        val targetParams = FrameLayout.LayoutParams(options.floatingViewWidth, options.floatingViewHeight)
        view.layoutParams = targetParams
        floatingView.addView(view)

        // 非表示モードの場合
        if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            floatingView.visibility = View.GONE
        }
        mFloatingViewList.add(floatingView)
        // TrashView
        mTrashView.setTrashViewListener(this)

        // Viewの貼り付け
        mWindowManager.addView(floatingView, floatingView.windowLayoutParams)
        // 最初の貼り付け時の場合のみ、フルスクリーン監視Viewと削除Viewを貼り付け
        if (isFirstAttach) {
            mWindowManager.addView(mFullscreenObserverView, mFullscreenObserverView.windowLayoutParams)
            mTargetFloatingView = floatingView
        } else {
            removeViewImmediate(mTrashView)
        }
        // 必ずトップに来て欲しいので毎回貼り付け
        mWindowManager.addView(mTrashView, mTrashView.windowLayoutParams)
    }

    /**
     * ViewをWindowから取り外します。
     *
     * @param floatingView FloatingView
     */
    private fun removeViewToWindow(floatingView: FloatingView) {
        val matchIndex = mFloatingViewList.indexOf(floatingView)
        // 見つかった場合は表示とリストから削除
        if (matchIndex != -1) {
            removeViewImmediate(floatingView)
            mFloatingViewList.removeAt(matchIndex)
        }

        // 残りのViewをチェック
        if (mFloatingViewList.isEmpty()) {
            // 終了を通知
            mFloatingViewListener?.onFinishFloatingView()
        }
    }

    /**
     * ViewをWindowから全て取り外します。
     */
    fun removeAllViewToWindow() {
        removeViewImmediate(mFullscreenObserverView)
        removeViewImmediate(mTrashView)
        // FloatingViewの削除
        val size = mFloatingViewList.size
        for (i in 0 until size) {
            val floatingView = mFloatingViewList[i]
            removeViewImmediate(floatingView)
        }
        mFloatingViewList.clear()
    }

    /**
     * Safely remove the View (issue #89)
     *
     * @param view [View]
     */
    private fun removeViewImmediate(view: View) {
        // fix #100(crashes on Android 8)
        try {
            mWindowManager.removeViewImmediate(view)
        } catch (e: IllegalArgumentException) {
            //do nothing
        }

    }

    /**
     * FloatingViewを貼り付ける際のオプションを表すクラスです。
     */
    class Options {

        /**
         * フローティングさせるViewの矩形（SHAPE_RECTANGLE or SHAPE_CIRCLE）
         */
        var shape: Float = 0.toFloat()

        /**
         * 画面外のはみ出しマージン(px)
         */
        var overMargin: Int = 0

        /**
         * 画面左下を原点とするFloatingViewのX座標
         */
        var floatingViewX: Int = 0

        /**
         * 画面左下を原点とするFloatingViewのY座標
         */
        var floatingViewY: Int = 0

        /**
         * Width of FloatingView(px)
         */
        var floatingViewWidth: Int = 0

        /**
         * Height of FloatingView(px)
         */
        var floatingViewHeight: Int = 0

        /**
         * FloatingViewが吸着する方向
         * ※座標を指定すると自動的にMOVE_DIRECTION_NONEになります
         */
        @MoveDirection
        var moveDirection: Int = 0

        /**
         * Use of physics-based animations or (default) ValueAnimation
         */
        var usePhysics: Boolean = false

        /**
         * 初期表示時にアニメーションするフラグ
         */
        var animateInitialMove: Boolean = false

        /**
         * オプションのデフォルト値を設定します。
         */
        init {
            shape = SHAPE_CIRCLE
            overMargin = 0
            floatingViewX = FloatingView.DEFAULT_X
            floatingViewY = FloatingView.DEFAULT_Y
            floatingViewWidth = FloatingView.DEFAULT_WIDTH
            floatingViewHeight = FloatingView.DEFAULT_HEIGHT
            moveDirection = MOVE_DIRECTION_DEFAULT
            usePhysics = true
            animateInitialMove = true
        }

    }

    override fun currentPosition() {
        rewritePositionY()
    }

    private fun rewritePositionY() {
        val isFinishing = mTargetFloatingView!!.state == FloatingView.STATE_FINISHING
        val params = mTargetFloatingView!!.windowLayoutParams
        mFloatingViewListener!!.onTouchFinished(isFinishing, params.x, mTargetFloatingView!!.heightLimit - params.y)
    }

    companion object {

        /**
         * 常に表示するモード
         */
        const val DISPLAY_MODE_SHOW_ALWAYS = 1

        /**
         * 常に非表示にするモード
         */
        const val DISPLAY_MODE_HIDE_ALWAYS = 2

        /**
         * フルスクリーン時に非表示にするモード
         */
        const val DISPLAY_MODE_HIDE_FULLSCREEN = 3

        /**
         * 左右の近い方向に移動
         */
        const val MOVE_DIRECTION_DEFAULT = 0
        /**
         * 常に左に移動
         */
        const val MOVE_DIRECTION_LEFT = 1
        /**
         * 常に右に移動
         */
        const val MOVE_DIRECTION_RIGHT = 2

        /**
         * 移動しない
         */
        const val MOVE_DIRECTION_NONE = 3

        /**
         * 側に近づく方向に移動します
         */
        const val MOVE_DIRECTION_NEAREST = 4

        /**
         * Goes in the direction in which it is thrown
         */
        const val MOVE_DIRECTION_THROWN = 5

        /**
         * Viewの形が円形の場合
         */
        const val SHAPE_CIRCLE = 1.0f

        /**
         * Viewの形が四角形の場合
         */
        const val SHAPE_RECTANGLE = 1.4142f

        /**
         * Find the safe area of DisplayCutout.
         *
         * @param activity [Activity] (Portrait and `windowLayoutInDisplayCutoutMode` != never)
         * @return Safe cutout insets.
         */
        fun findCutoutSafeArea(activity: Activity): Rect {
            val safeInsetRect = Rect()
            // TODO:Rewrite with android-x
            // TODO:Consider alternatives
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return safeInsetRect
            } else {
                // Fix: getDisplayCutout() on a null object reference (issue #110)
                val windowInsets = activity.window.decorView.rootWindowInsets ?: return safeInsetRect

                // set safeInsetRect
                val displayCutout = windowInsets.displayCutout
                if (displayCutout != null) {
                    safeInsetRect.set(displayCutout.safeInsetLeft, displayCutout.safeInsetTop, displayCutout.safeInsetRight, displayCutout.safeInsetBottom)
                }
            }
            return safeInsetRect
        }
    }
}
