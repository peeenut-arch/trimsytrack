package com.trimsytrack

import android.app.Application

class TrimsyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
