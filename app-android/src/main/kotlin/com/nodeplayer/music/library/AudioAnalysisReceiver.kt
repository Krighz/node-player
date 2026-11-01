package com.nodeplayer.music.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AudioAnalysisReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_PAUSE -> AudioAnalysisManager.pause(context)
            ACTION_RESUME -> AudioAnalysisManager.resume(context)
            ACTION_STOP -> AudioAnalysisManager.cancel(context)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.nodeplayer.music.action.PAUSE_ANALYSIS"
        const val ACTION_RESUME = "com.nodeplayer.music.action.RESUME_ANALYSIS"
        const val ACTION_STOP = "com.nodeplayer.music.action.STOP_ANALYSIS"
    }
}
