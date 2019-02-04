package com.dansdev.libapplifecycleobserver

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import com.dansdev.libapplifecycleobserver.listener.AppLifecycleListener
import com.dansdev.libapplifecycleobserver.receiver.OnLockScreenReceiver
import java.util.*

/**
 * Observer of app states.
 *
 * Handle state: app START, RESUME, PAUSE, STOP.
 *
 * Add listener @see [AppLifecycleListener]
 * Add adapter for handle only selected method @see [com.dansdev.libapplifecycleobserver.listener.AppLifecycleAdapter]
 *
 * First need to call @see [AppLifecycleObserver.init]
 */
class AppLifecycleObserver : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: AppLifecycleObserver? = null
            get() {
                if (field == null) {
                    field = AppLifecycleObserver()
                }
                return field
            }
            private set
    }

    private var app: Application? = null

    private val lifecycleListeners = WeakHashMap<String, AppLifecycleListener>()

    private var activities = mutableListOf<String>()
    private var isPaused = false
    private var isLastActivityFinished = false
    private var lockScreenReceiver: OnLockScreenReceiver? = null
    private var currentActivity: Activity? = null

    fun init(app: Application) {
        this.app = app
        app.registerActivityLifecycleCallbacks(this)
        app.registerComponentCallbacks(this)
    }

    fun addListener(tag: String, lifecycleListener: AppLifecycleListener) {
        if (app == null) throw IllegalStateException("First need to call init(), and after that add listeners")
        lifecycleListeners[tag] = lifecycleListener
        lockScreenReceiver?.let {
            it.lifecycleListeners[tag] = lifecycleListener
        }
    }

    fun removeListener(tag: String) {
        if (lifecycleListeners.containsKey(tag)) {
            lifecycleListeners.remove(tag)
        } else {
            System.out.println("is tag for listener not register")
        }
    }

    fun removeAllListeners() {
        lifecycleListeners.clear()
    }

    fun lastOpenedActivity(): Activity? = currentActivity

    override fun onActivityPaused(activity: Activity?) {
        activity?.let { isLastActivityFinished = it.isFinishing }
    }

    override fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !isLastActivityFinished) {
            isPaused = true
            lifecycleListeners.values.forEach { it.onAppPaused(null, false) }
        }
    }

    override fun onActivityResumed(activity: Activity?) {
        activity?.let { activity ->
            currentActivity = activity
            if (isPaused) {
                lifecycleListeners.values.forEach { it.onAppResumed(activity, false) }
                isPaused = false
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity?) {
        activity?.let { activities.remove(activity.javaClass.simpleName) }
        handleChangeActivities()
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        handleAppIsCreated()
        activity?.let {
            currentActivity = it
            activities.add(activity.javaClass.simpleName)
        }
        handleChangeActivities()
    }

    private fun handleAppIsCreated() {
        if (activities.isEmpty()) {
            lifecycleListeners.values.forEach { it.onAppStart() }
            registerLockReceiver()
        }
    }

    private fun registerLockReceiver() {
        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        intentFilter.addAction(Intent.ACTION_USER_PRESENT)

        unregisterLockReceiver()
        lockScreenReceiver = OnLockScreenReceiver(lifecycleListeners) { isPaused }
        app?.registerReceiver(lockScreenReceiver, intentFilter)
    }

    private fun handleChangeActivities() {
        if (activities.isEmpty()) {
            currentActivity = null
            lifecycleListeners.values.forEach { it.onAppClose() }
            unregisterLockReceiver()
        }
    }

    private fun unregisterLockReceiver() {
        lockScreenReceiver?.let {
            try {
                app?.unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                lockScreenReceiver = null
            }
        }
    }

    override fun onActivityStopped(activity: Activity?) {
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onLowMemory() {
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        lifecycleListeners.values.forEach { it.onAppConfigurationChanged(newConfig) }
    }

    override fun onActivityStarted(activity: Activity?) {
    }


}