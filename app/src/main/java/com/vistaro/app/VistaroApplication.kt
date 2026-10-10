package com.vistaro.app

import android.app.Application

class VistaroApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.catalogSyncManager.scheduleDailySync()
    }
}
