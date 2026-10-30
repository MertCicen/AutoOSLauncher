package com.autoos.launcher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.WeakHashMap

/** Refreshes a launcher screen left on the back stack when the UI language changes. */
object LocaleRefresh : Application.ActivityLifecycleCallbacks {
    private val createdWith=WeakHashMap<Activity,String>()

    private fun selected(activity: Activity): String =
        AppLanguage.selected(activity)

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        createdWith[activity]=selected(activity)
    }
    override fun onActivityResumed(activity: Activity) {
        val code=selected(activity)
        if(createdWith[activity]!=code) {
            createdWith[activity]=code
            activity.recreate()
        }
    }
    override fun onActivityDestroyed(activity: Activity) { createdWith.remove(activity) }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
