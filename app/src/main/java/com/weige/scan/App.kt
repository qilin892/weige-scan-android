package com.weige.scan

import android.app.Application
import com.weige.scan.data.Prefs
import com.weige.scan.work.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Prefs.init(this)
        Notifier.ensureChannel(this)
    }
}
