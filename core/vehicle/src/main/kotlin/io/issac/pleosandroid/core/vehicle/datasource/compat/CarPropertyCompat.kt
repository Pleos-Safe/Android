package io.issac.pleosandroid.core.vehicle.datasource.compat

import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback
import android.car.hardware.property.Subscription
import android.os.Build
import androidx.annotation.RequiresApi

internal fun CarPropertyManager.subscribePropertyEventsCompat(
    callback: CarPropertyEventCallback,
    propertyId: Int,
    updateRateHz: Float,
): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    Api35.subscribe(this, callback, propertyId, updateRateHz)
} else {
    @Suppress("DEPRECATION") // Required by the Pleos API 34 framework.
    registerCallback(callback, propertyId, updateRateHz)
}

internal fun CarPropertyManager.unsubscribePropertyEventsCompat(callback: CarPropertyEventCallback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        Api35.unsubscribe(this, callback)
    } else {
        @Suppress("DEPRECATION") // Required by the Pleos API 34 framework.
        unregisterCallback(callback)
    }
}

internal fun CarPropertyValue<*>.hasAvailableStatus(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        Api35.hasAvailableStatus(this)
    } else {
        @Suppress("DEPRECATION") // getPropertyStatus is absent from the API 34 framework.
        status == CarPropertyValue.STATUS_AVAILABLE
    }

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private object Api35 {
    fun subscribe(
        manager: CarPropertyManager,
        callback: CarPropertyEventCallback,
        propertyId: Int,
        updateRateHz: Float,
    ): Boolean {
        val subscription = Subscription.Builder(propertyId)
            .setUpdateRateHz(updateRateHz)
            .setVariableUpdateRateEnabled(false)
            .build()
        return manager.subscribePropertyEvents(listOf(subscription), null, callback)
    }

    fun unsubscribe(manager: CarPropertyManager, callback: CarPropertyEventCallback) {
        manager.unsubscribePropertyEvents(callback)
    }

    fun hasAvailableStatus(value: CarPropertyValue<*>): Boolean =
        value.propertyStatus == CarPropertyValue.STATUS_AVAILABLE
}
