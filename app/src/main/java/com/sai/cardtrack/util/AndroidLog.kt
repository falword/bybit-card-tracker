package com.sai.cardtrack.util

import android.util.Log
import com.sai.cardtrack.BuildConfig

class AndroidLog : AppLog {
    override fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("CardTrack", message)
        }
    }
}
