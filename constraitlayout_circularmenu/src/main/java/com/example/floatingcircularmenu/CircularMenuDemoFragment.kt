package com.example.floatingcircularmenu


import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_circle_menu.*

/**
 * A simple [Fragment] subclass.
 */
class CircularMenuDemoFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_circle_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var isGone = true
        val radius = resources.getDimensionPixelSize(R.dimen.radius)

        val root = view.findViewById<ConstraintLayout>(R.id.root)

        btnClose.setOnClickListener { v ->
            isGone = !isGone

            val angle = if (isGone) 0f else 45f
            v.animate().rotation(angle).setDuration(100L).interpolator = AccelerateDecelerateInterpolator()

            root.forEach {
                if (it.id != R.id.btnClose && it.id != R.id.switch1) {
                    if (!isGone) it.isGone = isGone
                    it.side(isGone, radius, switch1.isChecked)
                }
            }



           /* if (!isGone) {
                button2.isGone = isGone
                button3.isGone = isGone
                button4.isGone = isGone
                button5.isGone = isGone
            }

            side(button2, isGone, radius, switch1.isChecked)
            side(button3, isGone, radius, switch1.isChecked)
            side(button4, isGone, radius, switch1.isChecked)
            side(button5, isGone, radius, switch1.isChecked)*/
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
    }

    private fun View.side(isGone: Boolean, radius: Int, isRTL: Boolean = false) {
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
            duration = 300L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val animatedValue = it.animatedValue as Int
                val x = animatedValue * Math.sin(radian)
                val y = animatedValue * Math.cos(radian)
                this@side.translationX = x.toFloat()
                this@side.translationY = y.toFloat()
            }
            doOnEnd {
                if (isGone) this@side.isGone = isGone
            }
        }.start()
    }
}
