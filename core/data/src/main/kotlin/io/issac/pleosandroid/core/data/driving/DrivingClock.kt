package io.issac.pleosandroid.core.data.driving

import android.os.SystemClock
import java.time.Instant
import javax.inject.Inject

internal interface DrivingClock {
    fun elapsedNanos(): Long
    fun now(): Instant
}

internal class DeviceDrivingClock @Inject constructor() : DrivingClock {
    override fun elapsedNanos() = SystemClock.elapsedRealtimeNanos()
    override fun now(): Instant = Instant.now()
}
