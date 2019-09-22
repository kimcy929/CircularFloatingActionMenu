package com.example.floatingcircularmenu


import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager
import kotlinx.android.synthetic.main.fragment_floating_menu.*

/**
 * A simple [Fragment] subclass.
 */
class FloatingCircularMenuFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_floating_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${requireContext().packageName}".toUri())
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            }
        }

        btnEnableFloatingTool.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                startFloatingViewService(activity)
            } else {
                requireContext().stopService(Intent(requireContext(), FloatingCircularMenuService::class.java))
            }
        }
    }

    private fun startFloatingViewService(activity: Activity?) {
        // *** You must follow these rules when obtain the cutout(FloatingViewManager.findCutoutSafeArea) ***
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 1. 'windowLayoutInDisplayCutoutMode' do not be set to 'never'
            if (activity!!.window.attributes.layoutInDisplayCutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER) {
                throw RuntimeException("'windowLayoutInDisplayCutoutMode' do not be set to 'never'")
            }
            // 2. Do not set Activity to landscape
            if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                throw RuntimeException("Do not set Activity to landscape")
            }
        }
        // launch service
        val service = FloatingCircularMenuService::class.java
        val key: String = FloatingCircularMenuService.EXTRA_CUTOUT_SAFE_AREA
        val intent = Intent(activity!!, service).putExtra(key, FloatingViewManager.findCutoutSafeArea(activity))
        requireContext().startService(intent)
    }

    companion object {
        const val REQUEST_OVERLAY_PERMISSION = 6
    }
}
