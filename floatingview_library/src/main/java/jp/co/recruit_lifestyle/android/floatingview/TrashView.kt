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

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.IntDef
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * FloatingViewを消すためのViewです。
 */
internal class TrashView
/**
 * コンストラクタ
 *
 * @param context Context
 */
(context: Context) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {

    /**
     * WindowManager
     */
    private val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * LayoutParams
     */
    /**
     * WindowManager.LayoutParams
     *
     * @return WindowManager.LayoutParams
     */
    val windowLayoutParams: WindowManager.LayoutParams

    /**
     * DisplayMetrics
     */
    private val mMetrics: DisplayMetrics = DisplayMetrics()

    /**
     * ルートView（背景、削除アイコンを含むView）
     */
    private val mRootView: ViewGroup

    /**
     * 削除アイコン
     */
    private val mTrashIconRootView: FrameLayout

    /**
     * 固定された削除アイコン
     */
    private val mFixedTrashIconView: ImageView

    /**
     * 重なりに応じて動作する削除アイコン
     */
    private val mActionTrashIconView: ImageView

    /**
     * ActionTrashIconの幅
     */
    private var mActionTrashIconBaseWidth: Int = 0

    /**
     * ActionTrashIconの高さ
     */
    private var mActionTrashIconBaseHeight: Int = 0

    /**
     * ActionTrashIconの最大拡大率
     */
    private var mActionTrashIconMaxScale: Float = 0.toFloat()

    /**
     * 背景View
     */
    private val mBackgroundView: FrameLayout

    /**
     * 削除アイコンの枠内に入った時のアニメーション（拡大）
     */
    private var mEnterScaleAnimator: ObjectAnimator? = null

    /**
     * 削除アイコンの枠外に出た時のアニメーション（縮小）
     */
    private var mExitScaleAnimator: ObjectAnimator? = null

    /**
     * アニメーションを行うハンドラ
     */
    private val mAnimationHandler: AnimationHandler

    /**
     * TrashViewListener
     */
    private var mTrashViewListener: TrashViewListener? = null

    /**
     * Viewの有効・無効フラグ（無効の場合は表示されない）
     */
    private var mIsEnabled: Boolean = false

    /**
     * 削除アイコンの中心X座標を取得します。
     *
     * @return 削除アイコンの中心X座標
     */
    val trashIconCenterX: Float
        get() {
            val iconView = if (hasActionTrashIcon()) mActionTrashIconView else mFixedTrashIconView
            val iconViewPaddingLeft = iconView.paddingLeft.toFloat()
            val iconWidth = iconView.width.toFloat() - iconViewPaddingLeft - iconView.paddingRight.toFloat()
            val x = mTrashIconRootView.x + iconViewPaddingLeft
            return x + iconWidth / 2
        }

    /**
     * 削除アイコンの中心Y座標を取得します。
     *
     * @return 削除アイコンの中心Y座標
     */
    val trashIconCenterY: Float
        get() {
            val iconView = if (hasActionTrashIcon()) mActionTrashIconView else mFixedTrashIconView
            val iconViewHeight = iconView.height.toFloat()
            val iconViewPaddingBottom = iconView.paddingBottom.toFloat()
            val iconHeight = iconViewHeight - iconView.paddingTop.toFloat() - iconViewPaddingBottom
            val y = mRootView.height.toFloat() - mTrashIconRootView.y - iconViewHeight + iconViewPaddingBottom
            return y + iconHeight / 2
        }

    /**
     * TrashViewの表示状態を取得します。
     *
     * @return trueの場合は表示
     */
    /**
     * TrashViewの有効・無効を設定します。
     *
     * @param enabled trueの場合は有効（表示）、falseの場合は無効（非表示）
     */
    // 設定が同じ場合は何もしない
    // 非表示にする場合は閉じる
    var isTrashEnabled: Boolean
        get() = mIsEnabled
        set(enabled) {
            if (mIsEnabled == enabled) {
                return
            }
            mIsEnabled = enabled
            if (!mIsEnabled) {
                dismiss()
            }
        }

    /**
     * Animation State
     */
    @IntDef(ANIMATION_NONE, ANIMATION_OPEN, ANIMATION_CLOSE, ANIMATION_FORCE_CLOSE)
    @Retention(RetentionPolicy.SOURCE)
    internal annotation class AnimationState

    init {
        mWindowManager.defaultDisplay.getMetrics(mMetrics)
        mAnimationHandler = AnimationHandler(this)
        mIsEnabled = true

        windowLayoutParams = WindowManager.LayoutParams()
        windowLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        windowLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        windowLayoutParams.type = OVERLAY_TYPE
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowLayoutParams.format = PixelFormat.TRANSLUCENT
        // INFO:Windowの原点のみ左下に設定
        windowLayoutParams.gravity = Gravity.START or Gravity.BOTTOM

        // 各種Viewの設定
        // TrashViewに直接貼り付けられるView（このViewを介さないと、削除Viewと背景Viewのレイアウトがなぜか崩れる）
        mRootView = FrameLayout(context)
        mRootView.setClipChildren(false)
        // 削除アイコンのルートView
        mTrashIconRootView = FrameLayout(context)
        mTrashIconRootView.clipChildren = false
        mFixedTrashIconView = ImageView(context)
        mActionTrashIconView = ImageView(context)
        // 背景View
        mBackgroundView = FrameLayout(context)
        mBackgroundView.alpha = 0.0f
        val gradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x50000000))
        mBackgroundView.background = gradientDrawable

        // 背景Viewの貼り付け
        val backgroundParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (BACKGROUND_HEIGHT * mMetrics.density).toInt())
        backgroundParams.gravity = Gravity.BOTTOM
        mRootView.addView(mBackgroundView, backgroundParams)
        // アクションアイコンの貼り付け
        val actionTrashIconParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        actionTrashIconParams.gravity = Gravity.CENTER
        mTrashIconRootView.addView(mActionTrashIconView, actionTrashIconParams)
        // 固定アイコンの貼付け
        val fixedTrashIconParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fixedTrashIconParams.gravity = Gravity.CENTER
        mTrashIconRootView.addView(mFixedTrashIconView, fixedTrashIconParams)
        // 削除アイコンの貼り付け
        val trashIconParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        trashIconParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        mRootView.addView(mTrashIconRootView, trashIconParams)

        // TrashViewに貼り付け
        addView(mRootView)

        // 初回描画処理用
        viewTreeObserver.addOnPreDrawListener(this)
    }

    /**
     * 表示位置を決定します。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewLayout()
    }

    /**
     * 画面回転時にレイアウトの調整をします。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateViewLayout()
    }

    /**
     * 初回描画時の座標設定を行います。<br></br>
     * 初回表示時に一瞬だけ削除アイコンが表示される事象があるため。
     */
    override fun onPreDraw(): Boolean {
        viewTreeObserver.removeOnPreDrawListener(this)
        mTrashIconRootView.translationY = mTrashIconRootView.measuredHeight.toFloat()
        return true
    }

    /**
     * initialize ActionTrashIcon
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mTrashViewListener!!.onUpdateActionTrashIcon()
    }

    /**
     * 画面サイズから自位置を決定します。
     */
    private fun updateViewLayout() {
        mWindowManager.defaultDisplay.getMetrics(mMetrics)
        windowLayoutParams.x = (mMetrics.widthPixels - width) / 2
        windowLayoutParams.y = 0

        // Update view and layout
        mTrashViewListener!!.onUpdateActionTrashIcon()
        mAnimationHandler.onUpdateViewLayout()

        mWindowManager.updateViewLayout(this, windowLayoutParams)
    }

    /**
     * TrashViewを非表示にします。
     */
    fun dismiss() {
        // アニメーション停止
        mAnimationHandler.removeMessages(ANIMATION_OPEN)
        mAnimationHandler.removeMessages(ANIMATION_CLOSE)
        mAnimationHandler.sendAnimationMessage(ANIMATION_FORCE_CLOSE)
        // 拡大アニメーションの停止
        setScaleTrashIconImmediately(false)
    }

    /**
     * Window上での描画領域を取得します。
     * 当たり判定の矩形を表します。
     *
     * @param outRect 変更を加えるRect
     */
    fun getWindowDrawingRect(outRect: Rect) {
        // Gravityが逆向きなので、矩形の当たり判定も上下逆転(top/bottom)
        // top(画面上で下方向)の判定を多めに設定
        val iconView = if (hasActionTrashIcon()) mActionTrashIconView else mFixedTrashIconView
        val iconPaddingLeft = iconView.paddingLeft.toFloat()
        val iconPaddingTop = iconView.paddingTop.toFloat()
        val iconWidth = iconView.width.toFloat() - iconPaddingLeft - iconView.paddingRight.toFloat()
        val iconHeight = iconView.height.toFloat() - iconPaddingTop - iconView.paddingBottom.toFloat()
        val x = mTrashIconRootView.x + iconPaddingLeft
        val y = mRootView.height.toFloat() - mTrashIconRootView.y - iconPaddingTop - iconHeight
        val left = (x - TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density).toInt()
        val top = -mRootView.height
        val right = (x + iconWidth + TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density).toInt()
        val bottom = (y + iconHeight + TARGET_CAPTURE_VERTICAL_REGION * mMetrics.density).toInt()
        outRect.set(left, top, right, bottom)
    }

    /**
     * アクションする削除アイコンの設定を更新します。
     *
     * @param width  対象となるViewの幅
     * @param height 対象となるViewの高さ
     * @param shape  対象となるViewの形状
     */
    fun updateActionTrashIcon(width: Float, height: Float, shape: Float) {
        // アクションする削除アイコンが設定されていない場合は何もしない
        if (!hasActionTrashIcon()) {
            return
        }
        // 拡大率の設定
        mAnimationHandler.mTargetWidth = width
        mAnimationHandler.mTargetHeight = height
        val newWidthScale = width / mActionTrashIconBaseWidth * shape
        val newHeightScale = height / mActionTrashIconBaseHeight * shape
        mActionTrashIconMaxScale = max(newWidthScale, newHeightScale)
        // ENTERアニメーション作成
        mEnterScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, mActionTrashIconMaxScale), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, mActionTrashIconMaxScale))
        mEnterScaleAnimator!!.interpolator = OvershootInterpolator()
        mEnterScaleAnimator!!.duration = TRASH_ICON_SCALE_DURATION_MILLIS
        // Exitアニメーション作成
        mExitScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, 1.0f), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, 1.0f))
        mExitScaleAnimator!!.interpolator = OvershootInterpolator()
        mExitScaleAnimator!!.duration = TRASH_ICON_SCALE_DURATION_MILLIS
    }


    /**
     * アクションする削除アイコンが存在するかチェックします。
     *
     * @return アクションする削除アイコンが存在する場合はtrue
     */
    private fun hasActionTrashIcon(): Boolean {
        return mActionTrashIconBaseWidth != 0 && mActionTrashIconBaseHeight != 0
    }

    /**
     * 固定削除アイコンの画像を設定します。<br></br>
     * この画像はフローティング表示が重なった際に大きさが変化しません。
     *
     * @param resId drawable ID
     */
    fun setFixedTrashIconImage(resId: Int) {
        mFixedTrashIconView.setImageResource(resId)
    }

    /**
     * アクションする削除アイコンの画像を設定します。<br></br>
     * この画像はフローティング表示が重なった際に大きさが変化します。
     *
     * @param resId drawable ID
     */
    fun setActionTrashIconImage(resId: Int) {
        mActionTrashIconView.setImageResource(resId)
        val drawable = mActionTrashIconView.drawable
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.intrinsicWidth
            mActionTrashIconBaseHeight = drawable.intrinsicHeight
        }
    }

    /**
     * 固定削除アイコンを設定します。<br></br>
     * この画像はフローティング表示が重なった際に大きさが変化しません。
     *
     * @param drawable Drawable
     */
    fun setFixedTrashIconImage(drawable: Drawable) {
        mFixedTrashIconView.setImageDrawable(drawable)
    }

    /**
     * アクション用削除アイコンを設定します。<br></br>
     * この画像はフローティング表示が重なった際に大きさが変化します。
     *
     * @param drawable Drawable
     */
    fun setActionTrashIconImage(drawable: Drawable?) {
        mActionTrashIconView.setImageDrawable(drawable)
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.intrinsicWidth
            mActionTrashIconBaseHeight = drawable.intrinsicHeight
        }
    }

    /**
     * 削除アイコンの大きさを即時に変更します。
     *
     * @param isEnter 領域に入った場合はtrue.そうでない場合はfalse
     */
    private fun setScaleTrashIconImmediately(isEnter: Boolean) {
        cancelScaleTrashAnimation()

        mActionTrashIconView.scaleX = if (isEnter) mActionTrashIconMaxScale else 1.0f
        mActionTrashIconView.scaleY = if (isEnter) mActionTrashIconMaxScale else 1.0f
    }

    /**
     * 削除アイコンの大きさを変更します。
     *
     * @param isEnter 領域に入った場合はtrue.そうでない場合はfalse
     */
    fun setScaleTrashIcon(isEnter: Boolean) {
        // アクションアイコンが設定されていなければ何もしない
        if (!hasActionTrashIcon()) {
            return
        }

        // アニメーションをキャンセル
        cancelScaleTrashAnimation()

        // 領域に入った場合
        if (isEnter) {
            mEnterScaleAnimator!!.start()
        } else {
            mExitScaleAnimator!!.start()
        }
    }

    /**
     * 削除アイコンの拡大・縮小アニメーションのキャンセル
     */
    private fun cancelScaleTrashAnimation() {
        // 枠内アニメーション
        if (mEnterScaleAnimator != null && mEnterScaleAnimator!!.isStarted) {
            mEnterScaleAnimator!!.cancel()
        }

        // 枠外アニメーション
        if (mExitScaleAnimator != null && mExitScaleAnimator!!.isStarted) {
            mExitScaleAnimator!!.cancel()
        }
    }

    /**
     * TrashViewListenerを設定します。
     *
     * @param listener TrashViewListener
     */
    fun setTrashViewListener(listener: TrashViewListener) {
        mTrashViewListener = listener
    }

    /**
     * FloatingViewに関連する処理を行います。
     *
     * @param event MotionEvent
     * @param x     FloatingViewのX座標
     * @param y     FloatingViewのY座標
     */
    fun onTouchFloatingView(event: MotionEvent, x: Float, y: Float) {
        val action = event.action
        // 押下
        if (action == MotionEvent.ACTION_DOWN) {
            mAnimationHandler.updateTargetPosition(x, y)
            // 長押し処理待ち
            mAnimationHandler.removeMessages(ANIMATION_CLOSE)
            mAnimationHandler.sendAnimationMessageDelayed(ANIMATION_OPEN, LONG_PRESS_TIMEOUT.toLong())
        } else if (action == MotionEvent.ACTION_MOVE) {
            mAnimationHandler.updateTargetPosition(x, y)
            // まだオープンアニメーションが開始していない場合のみ実行
            if (!mAnimationHandler.isAnimationStarted(ANIMATION_OPEN)) {
                // 長押しのメッセージを削除
                mAnimationHandler.removeMessages(ANIMATION_OPEN)
                // オープン
                mAnimationHandler.sendAnimationMessage(ANIMATION_OPEN)
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 長押しのメッセージを削除
            mAnimationHandler.removeMessages(ANIMATION_OPEN)
            mAnimationHandler.sendAnimationMessage(ANIMATION_CLOSE)
        }// 押上、キャンセル
        // 移動
    }

    /**
     * アニメーションの制御を行うハンドラです。
     */
    internal class AnimationHandler
    /**
     * コンストラクタ
     */
    (trashView: TrashView) : Handler() {

        /**
         * アニメーションを開始した時間
         */
        private var mStartTime: Long = 0

        /**
         * アニメーションを始めた時点のアルファ値
         */
        private var mStartAlpha: Float = 0.toFloat()

        /**
         * アニメーションを始めた時点のTransitionY
         */
        private var mStartTransitionY: Float = 0.toFloat()

        /**
         * 実行中のアニメーションのコード
         */
        private var mStartedCode: Int = 0

        /**
         * 追従対象のX座標
         */
        private var mTargetPositionX: Float = 0.toFloat()

        /**
         * 追従対象のY座標
         */
        private var mTargetPositionY: Float = 0.toFloat()

        /**
         * 追従対象の幅
         */
        var mTargetWidth: Float = 0.toFloat()

        /**
         * 追従対象の高さ
         */
        var mTargetHeight: Float = 0.toFloat()

        /**
         * 削除アイコンの移動限界位置
         */
        private val mTrashIconLimitPosition: Rect

        /**
         * Y軸の追従の範囲
         */
        private var mMoveStickyYRange: Float = 0.toFloat()

        /**
         * OvershootInterpolator
         */
        private val mOvershootInterpolator: OvershootInterpolator


        /**
         * TrashView
         */
        private val mTrashView: WeakReference<TrashView> = WeakReference(trashView)

        init {
            mStartedCode = ANIMATION_NONE
            mTrashIconLimitPosition = Rect()
            mOvershootInterpolator = OvershootInterpolator(OVERSHOOT_TENSION)
        }

        /**
         * アニメーションの処理を行います。
         */
        override fun handleMessage(msg: Message) {
            val trashView = mTrashView.get()
            if (trashView == null) {
                removeMessages(ANIMATION_OPEN)
                removeMessages(ANIMATION_CLOSE)
                removeMessages(ANIMATION_FORCE_CLOSE)
                return
            }

            // 有効でない場合はアニメーションを行わない
            if (!trashView.isTrashEnabled) {
                return
            }

            val animationCode = msg.what
            val animationType = msg.arg1
            val backgroundView = trashView.mBackgroundView
            val trashIconRootView = trashView.mTrashIconRootView
            val listener = trashView.mTrashViewListener
            val screenWidth = trashView.mMetrics.widthPixels.toFloat()
            val trashViewX = trashView.windowLayoutParams.x.toFloat()

            // アニメーションを開始した場合の初期化
            if (animationType == TYPE_FIRST) {
                mStartTime = SystemClock.uptimeMillis()
                mStartAlpha = backgroundView.alpha
                mStartTransitionY = trashIconRootView.translationY
                mStartedCode = animationCode
                listener?.onTrashAnimationStarted(mStartedCode)
            }
            // 経過時間
            val elapsedTime = (SystemClock.uptimeMillis() - mStartTime).toFloat()

            // 表示アニメーション
            if (animationCode == ANIMATION_OPEN) {
                val currentAlpha = backgroundView.alpha
                // 最大のアルファ値に達していない場合
                if (currentAlpha < MAX_ALPHA) {
                    val alphaTimeRate = min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f)
                    val alpha = min(mStartAlpha + alphaTimeRate, MAX_ALPHA)
                    backgroundView.alpha = alpha
                }

                // DelayTimeを超えていたらアニメーション開始
                if (elapsedTime >= TRASH_OPEN_START_DELAY_MILLIS) {
                    val screenHeight = trashView.mMetrics.heightPixels.toFloat()
                    // アイコンが左右に全部はみ出たらそれぞれ0%、100%の計算
                    val positionX = trashViewX + (mTargetPositionX + mTargetWidth) / (screenWidth + mTargetWidth) * mTrashIconLimitPosition.width() + mTrashIconLimitPosition.left.toFloat()
                    // 削除アイコンのY座標アニメーションと追従（上方向がマイナス）
                    // targetPositionYRateは、ターゲットのY座標が完全に画面外になると0%、画面の半分以降は100%
                    // stickyPositionYは移動限界の下端が原点で上端まで移動する。mMoveStickyRangeが追従の範囲
                    // positionYの計算により時間経過とともに移動する
                    val targetPositionYRate = min(2 * (mTargetPositionY + mTargetHeight) / (screenHeight + mTargetHeight), 1.0f)
                    val stickyPositionY = mMoveStickyYRange * targetPositionYRate + mTrashIconLimitPosition.height() - mMoveStickyYRange
                    val translationYTimeRate = min((elapsedTime - TRASH_OPEN_START_DELAY_MILLIS) / TRASH_OPEN_DURATION_MILLIS, 1.0f)
                    val positionY = mTrashIconLimitPosition.bottom - stickyPositionY * mOvershootInterpolator.getInterpolation(translationYTimeRate)
                    trashIconRootView.translationX = positionX
                    trashIconRootView.translationY = positionY
                    // clear drag view garbage
                }

                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
            } else if (animationCode == ANIMATION_CLOSE) {
                // アルファ値の計算
                val alphaElapseTimeRate = min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f)
                val alpha = max(mStartAlpha - alphaElapseTimeRate, MIN_ALPHA)
                backgroundView.alpha = alpha

                // 削除アイコンのY座標アニメーション
                val translationYTimeRate = min(elapsedTime / TRASH_CLOSE_DURATION_MILLIS, 1.0f)
                // アニメーションが最後まで到達していない場合
                if (alphaElapseTimeRate < 1.0f || translationYTimeRate < 1.0f) {
                    val position = mStartTransitionY + mTrashIconLimitPosition.height() * translationYTimeRate
                    trashIconRootView.translationY = position
                    sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS)
                } else {
                    // 位置を強制的に調整
                    trashIconRootView.translationY = mTrashIconLimitPosition.bottom.toFloat()
                    mStartedCode = ANIMATION_NONE
                    listener?.onTrashAnimationEnd(ANIMATION_CLOSE)
                }
            } else if (animationCode == ANIMATION_FORCE_CLOSE) {
                backgroundView.alpha = 0.0f
                trashIconRootView.translationY = mTrashIconLimitPosition.bottom.toFloat()
                mStartedCode = ANIMATION_NONE
                listener?.onTrashAnimationEnd(ANIMATION_FORCE_CLOSE)
            }// 即時非表示
            // 非表示アニメーション
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation   ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param delayMillis メッセージの送信時間
         */
        fun sendAnimationMessageDelayed(animation: Int, delayMillis: Long) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis)
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         */
        fun sendAnimationMessage(animation: Int) {
            sendMessage(newMessage(animation, TYPE_FIRST))
        }

        /**
         * アニメーションが開始しているかどうかチェックします。
         *
         * @param animationCode アニメーションコード
         * @return アニメーションが開始していたらtrue.そうでなければfalse
         */
        fun isAnimationStarted(animationCode: Int): Boolean {
            return mStartedCode == animationCode
        }

        /**
         * 追従対象の位置情報を更新します。
         *
         * @param x 追従対象のX座標
         * @param y 追従対象のY座標
         */
        fun updateTargetPosition(x: Float, y: Float) {
            mTargetPositionX = x
            mTargetPositionY = y
        }

        /**
         * Viewの表示状態が変更された際に呼び出されます。
         */
        fun onUpdateViewLayout() {
            val trashView = mTrashView.get() ?: return
// 削除アイコン(TrashIconRootView)の移動限界設定(Gravityの基準位置を元に計算）
            // 左下原点（画面下端（パディング含む）：0、上方向：マイナス、下方向：プラス）で、Y軸上限は削除アイコンが背景の中心に来る位置、下限はTrashIconRootViewが全部隠れる位置
            val density = trashView.mMetrics.density
            val backgroundHeight = trashView.mBackgroundView.measuredHeight.toFloat()
            val offsetX = TRASH_MOVE_LIMIT_OFFSET_X * density
            val trashIconHeight = trashView.mTrashIconRootView.measuredHeight
            val left = (-offsetX).toInt()
            val top = ((trashIconHeight - backgroundHeight) / 2 - TRASH_MOVE_LIMIT_TOP_OFFSET * density).toInt()
            val right = offsetX.toInt()
            mTrashIconLimitPosition.set(left, top, right, trashIconHeight)

            // 背景の大きさをもとにY軸の追従範囲を設定
            mMoveStickyYRange = backgroundHeight * 0.20f
        }

        companion object {

            /**
             * アニメーションをリフレッシュするミリ秒
             */
            private const val ANIMATION_REFRESH_TIME_MILLIS = 10L

            /**
             * 背景のアニメーション時間
             */
            private const val BACKGROUND_DURATION_MILLIS = 200L

            /**
             * 削除アイコンのポップアニメーションの開始遅延時間
             */
            private const val TRASH_OPEN_START_DELAY_MILLIS = 200L

            /**
             * 削除アイコンのオープンアニメーション時間
             */
            private const val TRASH_OPEN_DURATION_MILLIS = 400L

            /**
             * 削除アイコンのクローズアニメーション時間
             */
            private const val TRASH_CLOSE_DURATION_MILLIS = 200L

            /**
             * Overshootアニメーションの係数
             */
            private const val OVERSHOOT_TENSION = 1.0f

            /**
             * 削除アイコンの移動限界X軸オフセット(dp)
             */
            private const val TRASH_MOVE_LIMIT_OFFSET_X = 22

            /**
             * 削除アイコンの移動限界Y軸オフセット(dp)
             */
            private const val TRASH_MOVE_LIMIT_TOP_OFFSET = -4

            /**
             * アニメーション開始を表す定数
             */
            private const val TYPE_FIRST = 1
            /**
             * アニメーション更新を表す定数
             */
            private const val TYPE_UPDATE = 2

            /**
             * アルファの最大値
             */
            private const val MAX_ALPHA = 1.0f

            /**
             * アルファの最小値
             */
            private const val MIN_ALPHA = 0.0f

            /**
             * Clear the animation garbage of the target view.
             */
            private fun clearClippedChildren(viewGroup: ViewGroup) {
                viewGroup.clipChildren = true
                viewGroup.invalidate()
                viewGroup.clipChildren = false
            }

            /**
             * 送信するメッセージを生成します。
             *
             * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
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

    companion object {

        /**
         * 背景の高さ(dp)
         */
        private const val BACKGROUND_HEIGHT = 164

        /**
         * ターゲットを取り込む水平領域(dp)
         */
        private const val TARGET_CAPTURE_HORIZONTAL_REGION = 30.0f

        /**
         * ターゲットを取り込む垂直領域(dp)
         */
        private const val TARGET_CAPTURE_VERTICAL_REGION = 4.0f

        /**
         * 削除アイコンの拡大・縮小のアニメーション時間
         */
        private const val TRASH_ICON_SCALE_DURATION_MILLIS = 200L

        /**
         * アニメーションなしの状態を表す定数
         */
        const val ANIMATION_NONE = 0
        /**
         * 背景・削除アイコンなどを表示するアニメーションを表す定数<br></br>
         * FloatingViewの追尾も含みます。
         */
        const val ANIMATION_OPEN = 1
        /**
         * 背景・削除アイコンなどを消すアニメーションを表す定数
         */
        const val ANIMATION_CLOSE = 2
        /**
         * 背景・削除アイコンなどを即時に消すことを表す定数
         */
        const val ANIMATION_FORCE_CLOSE = 3

        /**
         * 長押し判定とする時間
         */
        private val LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout()

        /**
         * Overlay Type
         */
        private val OVERLAY_TYPE: Int = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

    }
}
