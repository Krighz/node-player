package com.nodeplayer.music.update

import com.nodeplayer.music.support.SupportConfig
import org.junit.Assert.*
import org.junit.Test

class SupportConfigTest {

    @Test
    fun testUpiConfigValues() {
        assertEquals("7063061981@fam", SupportConfig.UPI_ID)
        assertEquals("Krighz", SupportConfig.PAYEE_NAME)
        assertEquals("upi://pay?pa=7063061981@fam&pn=Krighz", SupportConfig.UPI_PAY_URI)
        assertEquals("https://krighz.github.io/node-player/#support", SupportConfig.SUPPORT_URL)
    }

    @Test
    fun testUpiUriStructure() {
        val uri = SupportConfig.UPI_PAY_URI
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=7063061981@fam"))
        assertTrue(uri.contains("pn=Krighz"))
        // Verify no ad parameters or extraneous tracking
        assertFalse(uri.contains("track"))
        assertFalse(uri.contains("analytics"))
    }
}
