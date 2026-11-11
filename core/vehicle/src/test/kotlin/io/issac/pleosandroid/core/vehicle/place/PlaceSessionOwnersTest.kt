package io.issac.pleosandroid.core.vehicle.place

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSessionOwnersTest {
    @Test
    fun lastOwnerClosesSessionAndRepeatedReleaseCannotCloseTheNextSession() {
        val owners = PlaceSessionOwners { Session() }
        val home = owners.acquire()
        val original = checkNotNull(owners.runtime.value)
        val explore = owners.acquire()
        assertSame(original, owners.runtime.value)

        home.close()
        assertEquals(0, original.closed.get())
        explore.close()
        assertNull(owners.runtime.value)
        assertEquals(1, original.closed.get())

        val next = owners.acquire()
        val replacement = checkNotNull(owners.runtime.value)
        assertNotSame(original, replacement)
        home.close()
        explore.close()
        assertSame(replacement, owners.runtime.value)
        assertEquals(0, replacement.closed.get())
        next.close()
        assertEquals(1, replacement.closed.get())
    }

    @Test
    fun concurrentOwnersShareOneSessionAndReleaseItOnce() {
        val created = AtomicInteger()
        val owners = PlaceSessionOwners {
            created.incrementAndGet()
            Session()
        }
        val home = owners.acquire()
        val session = checkNotNull(owners.runtime.value)
        val ready = CountDownLatch(8)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val workers = (1..8).map {
                executor.submit {
                    val lease = owners.acquire()
                    ready.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    lease.close()
                    lease.close()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            home.close()
            assertEquals(0, session.closed.get())
            release.countDown()
            workers.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, created.get())
            assertEquals(1, session.closed.get())
            assertNull(owners.runtime.value)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private class Session : AutoCloseable {
        val closed = AtomicInteger()
        override fun close() {
            closed.incrementAndGet()
        }
    }
}
