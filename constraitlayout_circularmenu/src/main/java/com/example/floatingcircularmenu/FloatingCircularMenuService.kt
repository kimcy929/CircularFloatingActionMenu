package com.example.floatingcircularmenu

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.forEachIndexed
import androidx.core.view.isGone
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewListener
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager
import kotlin.math.cos
import kotlin.math.sin


class FloatingCircularMenuService : Service(), FloatingViewListener {

    private val windowManger by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var circularMenuParams: WindowManager.LayoutParams? = null

    private lateinit var actionButton: AppCompatImageView

    private lateinit var circularMenu: ConstraintLayout
    private lateinit var btnClose: AppCompatImageView
    private lateinit var button2: AppCompatImageView
    private lateinit var button3: AppCompatImageView
    private lateinit var button4: AppCompatImageView
    private lateinit var button5: AppCompatImageView

    private val constraintSetRTL = ConstraintSet()
    private val constraintSetLTR = ConstraintSet()

    private val radius by lazy {
        resources.getDimensionPixelSize(R.dimen.radius)
    }

    private val actionButtonSize by lazy {
        resources.getDimensionPixelSize(R.dimen.floating_icon_size)
    }

    private lateinit var gestureDetector: GestureDetector

    private var isHideCircularMenu = true

    private val metrics = DisplayMetrics()

    private var floatingViewManager: FloatingViewManager? = null

    private var xPosition: Int = 0
    private var yPosition: Int = 0

    private val overMargin by lazy {
        (2 * metrics.density).toInt()
    }

    private val isMoveToEdge = true // For FloatingViewManager.MOVE_DIRECTION_THROWN

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    @SuppressLint("InflateParams")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        initFloatingView(intent)

        initCircularMenu()

        return START_NOT_STICKY
    }

    private fun initFloatingView(intent: Intent?) {
        windowManger.defaultDisplay.getMetrics(metrics)

        val inflater = LayoutInflater.from(this)
        actionButton = inflater.inflate(R.layout.floating_action_button, null, false) as AppCompatImageView
        actionButton.setOnClickListener {
            closeOpenCircleMenu()
        }

        floatingViewManager = FloatingViewManager(this, this).apply {
            setFixedTrashIconImage(R.drawable.ic_trash_fixed)
            setActionTrashIconImage(R.drawable.ic_trash_action)
            setSafeInsetRect(intent!!.getParcelableExtra(EXTRA_CUTOUT_SAFE_AREA) as Rect)

            val options = FloatingViewManager.Options().apply {
                moveDirection = FloatingViewManager.MOVE_DIRECTION_THROWN
                overMargin = this@FloatingCircularMenuService.overMargin
                floatingViewX = metrics.widthPixels - actionButtonSize - radius + overMargin
                floatingViewY = (metrics.heightPixels - actionButtonSize) / 2
                isTrashViewEnabled = false
                setDisplayMode(FloatingViewManager.DISPLAY_MODE_SHOW_ALWAYS)
            }

            addViewToWindow(actionButton, options)
        }
    }

    override fun onDestroy() {
        destroy()
        super.onDestroy()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initCircularMenu() {
        val inflater = LayoutInflater.from(this)

        circularMenu = inflater.inflate(R.layout.floating_action_circle_menu, null, false) as ConstraintLayout

        constraintSetLTR.apply {
            clone(circularMenu)
            connect(R.id.btnClose, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(R.id.btnClose, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(R.id.btnClose, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }

        constraintSetRTL.apply {
            clone(circularMenu)
            connect(R.id.btnClose, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(R.id.btnClose, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(R.id.btnClose, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }

        btnClose = circularMenu.findViewById(R.id.btnClose)
        button2 = circularMenu.findViewById(R.id.button2)
        button3 = circularMenu.findViewById(R.id.button3)
        button4 = circularMenu.findViewById(R.id.button4)
        button5 = circularMenu.findViewById(R.id.button5)

        btnClose.setOnClickListener {
            closeOpenCircleMenu()
        }

        button2.setOnClickListener {
            btnClose.callOnClick()
        }

        button3.setOnClickListener {
            btnClose.callOnClick()
        }

        button4.setOnClickListener {
            btnClose.callOnClick()
        }

        button5.setOnClickListener {
            btnClose.callOnClick()
        }

        val gesture = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                closeOpenCircleMenu()
                return super.onSingleTapConfirmed(e)
            }
        }

        gestureDetector = GestureDetector(this, gesture)

        circularMenu.setOnTouchListener(FloatingButtonTouchListener())

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
        }

        circularMenuParams = WindowManager.LayoutParams(
            radius + actionButtonSize,
            2 * radius + actionButtonSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                //or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT)

        circularMenuParams!!.gravity = Gravity.TOP or Gravity.START
    }

    private fun closeOpenCircleMenu() {
        if (isHideCircularMenu) {
            actionButton.animate().apply {
                cancel()
            }.alpha(0f)
            .setDuration(0L)
            .setStartDelay(0L)
            .start()

            isHideCircularMenu = false

            circularMenuParams!!.x = if (!isMoveToEdge) {
                if (isRTL()) xPosition - circularMenuParams!!.width + actionButtonSize else xPosition
            } else {
                if (isRTL()) getScreenWidth() - circularMenuParams!!.width + overMargin else -overMargin
            }

            circularMenuParams!!.y = yPosition + (actionButton.height - circularMenuParams!!.height) / 2

            if (isRTL()) {
                constraintSetRTL.clear(R.id.btnClose, ConstraintSet.START)
                constraintSetRTL.applyTo(circularMenu)
            } else {
                constraintSetLTR.clear(R.id.btnClose, ConstraintSet.END)
                constraintSetLTR.applyTo(circularMenu)
            }

            if (!ViewCompat.isAttachedToWindow(circularMenu)) {
                windowManger.addView(circularMenu, circularMenuParams)
            } else {
                circularMenu.isGone = false
                windowManger.updateViewLayout(circularMenu, circularMenuParams)
            }
        } else {
            isHideCircularMenu = true
        }

        val angle = if (isHideCircularMenu) 0f else 45f
        btnClose.animate().apply {
            cancel()
        }
        .rotation(angle)
        .setDuration(100L)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withStartAction {
            val count = circularMenu.childCount - 1
            circularMenu.forEachIndexed { index, view ->
                if (view.id != R.id.btnClose) {
                    if (!isHideCircularMenu) view.isGone = isHideCircularMenu
                    view.side(isHideCircularMenu, radius, isRTL(), index == count - 1)
                }
            }
        }
        .withEndAction {
            if (isHideCircularMenu) {
                actionButton.animate().apply {
                    cancel()
                }.alpha(1f)
                    .setDuration(100L)
                    .setStartDelay(0L)
                    .withEndAction {
                        actionButton.animate().alpha(0.25f).setDuration(3000L).start()
                    }
                    .start()
            }
        }
        .start()
    }

    private fun View.side(isGone: Boolean, radius: Int, isRTL: Boolean = false, isLastItem: Boolean = false) {
        val layoutParams = this.layoutParams as ConstraintLayout.LayoutParams
        val angle = layoutParams.circleAngle.toDouble()
        var radian = Math.toRadians(angle)
        if (isRTL) radian = -radian
        var from = 0
        var to = radius
        if (isGone) {
            from = radius
            to = 0
        }
        ValueAnimator.ofInt(from, to).apply {
            duration = 200L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val animatedValue = it.animatedValue as Int
                val x = animatedValue * sin(radian)
                val y = animatedValue * cos(radian)
                this@side.translationX = x.toFloat()
                this@side.translationY = y.toFloat()
            }
            doOnEnd {
                if (isGone) {
                    this@side.isGone = isGone
                    if (isLastItem) {
                        /*if (isHideCircularMenu) {
                            actionButton.animate().apply {
                                cancel()
                            }.alpha(1f)
                            .setStartDelay(0L)
                            .setStartDelay(0L)
                            .withEndAction {
                                actionButton.animate().alpha(0.45f).setDuration(3000L).start()
                            }
                            .start()
                        }*/
                        circularMenu.isGone = true
                    }
                }
            }
        }.start()
    }

    private fun isRTL() = xPosition > metrics.widthPixels / 2

    private fun getScreenWidth() =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            metrics.heightPixels
        else
            metrics.widthPixels

    private inner class FloatingButtonTouchListener : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    if (!circularMenu.isGone) {
                        btnClose.callOnClick()
                    }
                }
            }
            return true
        }
    }

    override fun onFinishFloatingView() {
        stopSelf()
    }

    /**
     * {@inheritDoc}
     */
    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {
        if (isFinishing) {
            Log.d(TAG, getString(R.string.deleted_soon))
        } else {
            xPosition = x
            yPosition = y
            Log.d(TAG, getString(R.string.touch_finished_position, x, y))
        }
    }

    /**
     * Viewを破棄します。
     */
    private fun destroy() {
        floatingViewManager?.removeAllViewToWindow()
        try {
            windowManger.removeView(circularMenu)
        } catch (e: IllegalArgumentException) {
        }
    }

    private fun createNotification(context: Context): Notification {
        val notificationChannel = context.getString(R.string.default_floatingview_channel_name)

        return NotificationCompat.Builder(context, notificationChannel).apply {
            setWhen(System.currentTimeMillis())
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle(context.getString(R.string.chathead_content_title))
            setContentText(context.getString(R.string.content_text))
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_MIN
            setCategory(NotificationCompat.CATEGORY_SERVICE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(NotificationChannel(notificationChannel, "App Service", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }.build()
    }

    companion object {

        val TAG: String = FloatingCircularMenuService::class.java.simpleName


        /**
         * Intent key (Cutout safe area)
         */
        const val EXTRA_CUTOUT_SAFE_AREA = "cutout_safe_area"
    }
}
