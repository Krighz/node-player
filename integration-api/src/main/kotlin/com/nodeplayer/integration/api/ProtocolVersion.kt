package com.nodeplayer.integration.api

object ProtocolVersion {
    const val CURRENT_VERSION: Int = 1
    const val MIN_SUPPORTED_VERSION: Int = 1

    fun isCompatible(peerVersion: Int): Boolean {
        return peerVersion in MIN_SUPPORTED_VERSION..CURRENT_VERSION
    }
}
