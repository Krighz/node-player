package com.nodeplayer.music.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log

/**
 * Manifest-registered BroadcastReceiver for system audio effect sessions.
 * Intercepts ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION & ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
 * and forwards them to SystemWideEqService.
 */
class AudioSessionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AudioSessionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "Unknown"

        Log.d(TAG, "AudioSessionReceiver received: $action | session=$sessionId | package=$packageName")

        if (sessionId <= 0) return

        if (SystemWideEqService.isRunning) {
            val service = SystemWideEqService.instance
            if (service != null) {
                when (action) {
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> service.attachSession(sessionId, packageName)
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> service.releaseSession(sessionId)
                }
            } else {
                val serviceIntent = Intent(context, SystemWideEqService::class.java).apply {
                    this.action = action
                    putExtras(intent)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
