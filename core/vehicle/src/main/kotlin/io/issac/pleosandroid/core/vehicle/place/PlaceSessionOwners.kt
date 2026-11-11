package io.issac.pleosandroid.core.vehicle.place

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Each screen releases its own lease. The last release closes the shared native session. */
internal class PlaceSessionOwners<T : AutoCloseable>(private val create: () -> T) {
    private val lock = Any()
    private val mutableRuntime = MutableStateFlow<T?>(null)
    private var owners = 0
    val runtime = mutableRuntime.asStateFlow()

    fun acquire(): AutoCloseable {
        synchronized(lock) {
            if (owners == 0) mutableRuntime.value = create()
            owners += 1
        }
        val released = AtomicBoolean(false)
        return AutoCloseable {
            if (released.compareAndSet(false, true)) release()
        }
    }

    private fun release() {
        synchronized(lock) {
            owners -= 1
            if (owners == 0) {
                val previous = mutableRuntime.value
                mutableRuntime.value = null
                previous?.close()
            }
        }
    }
}
