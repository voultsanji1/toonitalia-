package com.toonitalia.app

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

class ToonItaliaApp : Application() {
    companion object {
        var isTvMode: Boolean = false
            private set

        fun isTV(context: Context): Boolean {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }
    }

    override fun onCreate() {
        super.onCreate()
        isTvMode = isTV(this)
        CrashHandler(this)
    }
}