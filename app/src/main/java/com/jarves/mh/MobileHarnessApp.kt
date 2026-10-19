package com.jarves.mh

import android.app.Application
import android.util.Log
import com.jarves.mh.runtime.RuntimeTaskController

/** Process initialization, not Activity initialization: reconciliation runs only once per cold start. */
class MobileHarnessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { RuntimeTaskController.initialize(this) }
            .onFailure { Log.e("MobileHarnessApp", "Session journal unavailable; jobs remain fail-closed", it) }
    }
}
