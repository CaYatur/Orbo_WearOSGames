package com.cayadev.orbo

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import com.cayadev.orbo.engine.Engine
import com.cayadev.orbo.engine.Host
import com.cayadev.orbo.engine.OrboView
import com.cayadev.orbo.home.HomeScene

class MainActivity : Activity(), Host {
    private lateinit var engine: Engine
    private lateinit var view: OrboView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureImmersiveWindow()
        engine = Engine(this, this)
        view = OrboView(this, engine)
        setContentView(view)
        installSystemBackConsumer()
        engine.setRoot(HomeScene())
    }

    override fun onResume() {
        super.onResume()
        configureImmersiveWindow()
        view.resume()
        engine.onResume()
    }

    override fun onPause() {
        super.onPause()
        view.pause()
        engine.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        consumeSystemBack()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            consumeSystemBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            consumeSystemBack()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureImmersiveWindow()
    }

    private fun installSystemBackConsumer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                consumeSystemBack()
            }
        }
    }

    private fun consumeSystemBack() {
        configureImmersiveWindow()
        if (::view.isInitialized) view.requestFocus()
    }

    private fun configureImmersiveWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setDecorFitsSystemWindows(false)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Host
    override fun keepScreenOn(on: Boolean) {
        runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
