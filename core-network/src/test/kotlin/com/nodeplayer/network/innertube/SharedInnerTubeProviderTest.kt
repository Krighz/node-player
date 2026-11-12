package com.nodeplayer.network.innertube

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SharedInnerTubeProviderTest {

    @After
    fun tearDown() {
        SharedInnerTubeProvider.reset()
    }

    @Test
    fun testGetReturnsSameThreadSafeSingletonAcrossConcurrentCalls() = runBlocking {
        val creations = AtomicInteger(0)
        SharedInnerTubeProvider.setProvider {
            creations.incrementAndGet()
            InnerTubeClient(engine = MockEngine { respondOk("{}") })
        }

        val results = (1..16).map {
            async(Dispatchers.Default) {
                SharedInnerTubeProvider.get()
            }
        }.awaitAll()

        val first = results.first()
        results.forEach { assertSame(first, it) }
        assertEquals(1, creations.get())
    }

    @Test
    fun testSetProviderOverrideAndCookieSharingWithStreamResolver() {
        val customClient = InnerTubeClient(engine = MockEngine { respondOk("{}") })
        SharedInnerTubeProvider.setProvider { customClient }

        val resolvedClient = SharedInnerTubeProvider.get()
        assertSame(customClient, resolvedClient)

        val resolver = StreamResolver(innerTubeClient = SharedInnerTubeProvider.get())
        assertSame(customClient, resolver.innerTubeClient)

        customClient.sessionCookie = "SAPISID=test_cookie_value"
        assertEquals("SAPISID=test_cookie_value", resolver.innerTubeClient.sessionCookie)

        resolver.close()
    }

    @Test
    fun testDefaultInnerTubeClientConstructorRemainsIndependent() {
        val clientA = InnerTubeClient(engine = MockEngine { respondOk("{}") })
        val clientB = InnerTubeClient(engine = MockEngine { respondOk("{}") })
        assertNotSame(clientA, clientB)
        clientA.close()
        clientB.close()
    }
}
