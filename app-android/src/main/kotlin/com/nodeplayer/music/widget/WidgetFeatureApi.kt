package com.nodeplayer.music.widget

import android.content.Context
import com.nodeplayer.model.Track

/**
 * Public feature contract for Home Screen Widgets.
 * Allows PlaybackService, ViewModels, and other features to trigger widget updates
 * without tight coupling to internal rendering logic.
 */
interface WidgetFeatureApi {
    fun updateAllWidgets(context: Context)
    fun saveLastKnownTrack(context: Context, track: Track)
    fun getLastKnownTrack(context: Context): Track?
}
