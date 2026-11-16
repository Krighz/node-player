package com.nodeplayer.music.test

import com.nodeplayer.model.SettingsRegistry
import org.junit.Test

class SettingsRestartPersistenceTest {

    @Test
    fun testOnlineModePersistenceAcrossRestart() {
        SettingsRestartPersistenceTestHelper.assertSettingSurvivesRestart(
            setting = SettingsRegistry.IS_ONLINE_MODE,
            testValue = false
        )
    }

    @Test
    fun testNodeConnectPersistenceAcrossRestart() {
        SettingsRestartPersistenceTestHelper.assertSettingSurvivesRestart(
            setting = SettingsRegistry.NODE_CONNECT_ENABLED,
            testValue = false
        )
    }

    @Test
    fun testLastFmScrobblePersistenceAcrossRestart() {
        SettingsRestartPersistenceTestHelper.assertSettingSurvivesRestart(
            setting = SettingsRegistry.LASTFM_SCROBBLE_ENABLED,
            testValue = true
        )
    }

    @Test
    fun testMinDurationSecondsPersistenceAcrossRestart() {
        SettingsRestartPersistenceTestHelper.assertSettingSurvivesRestart(
            setting = SettingsRegistry.MIN_DURATION_SECONDS,
            testValue = 45
        )
    }
}
