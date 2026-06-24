package com.smsauto

import android.app.Application

class SmsAutoApp : Application() {
    var mainActivity: MainActivity? = null

    override fun onCreate() {
        super.onCreate()
    }
}
