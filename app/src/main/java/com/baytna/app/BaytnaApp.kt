package com.baytna.app

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BaytnaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.sync.start()
        container.appScope.launch {
            // Only touch WorkManager when reminders were turned on.
            if (container.settings.settings.first().remindersEnabled) container.reminders.schedule(true)
        }
    }
}
