package com.example.lockscreenapp

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class LockService : AccessibilityService() {

    private var overlay: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Keep the overlay up while the lock screen's own
                    // screen-off animation finishes underneath it
                    handler.postDelayed({ removeOverlay() }, 1000)
                }
                Intent.ACTION_SCREEN_ON -> {
                    // User woke the phone: never block it
                    removeOverlay()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for simply locking the screen
    }

    override fun onInterrupt() {
        // Handle service interruption if necessary
    }

    companion object {
        private var instanceRef: WeakReference<LockService>? = null
        val instance: LockService? get() = instanceRef?.get()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        removeOverlay()
        instanceRef = null
    }

    fun lockDevice() {
        if (overlay != null) return // already locking

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View(this).apply { setBackgroundColor(Color.BLACK) }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        var locked = false
        val doLock = {
            if (!locked) {
                locked = true
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        }

        try {
            wm.addView(view, lp)
            overlay = view
        } catch (_: Exception) {
            // If the overlay fails, fall back to a plain lock
            doLock()
            return
        }

        // Lock only once the black frame has really been drawn
        view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var done = false
            override fun onDraw() {
                if (done) return
                done = true
                handler.post { view.viewTreeObserver.removeOnDrawListener(this) }
                // Short wait for the frame to reach the display
                handler.postDelayed({ doLock() }, 60)
            }
        })

        // Backup in case the draw callback never fires
        handler.postDelayed({ doLock() }, 400)

        // Safety net for removing the overlay
        handler.postDelayed({ removeOverlay() }, 3000)
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {}
    }
}