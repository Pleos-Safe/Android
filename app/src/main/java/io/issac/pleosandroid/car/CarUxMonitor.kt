package io.issac.pleosandroid.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Subscribe in onStart and release in onStop. Instantiate only after checking FEATURE_AUTOMOTIVE. */
class CarUxMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutablePolicy = MutableStateFlow(CarUxPolicy())
    val policy: StateFlow<CarUxPolicy> = mutablePolicy.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var started = false
    private var session = 0L
    private var subscription = 0L

    @MainThread
    fun start() {
        checkMainThread()
        if (started) return
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            mutablePolicy.value = CarUxPolicy(message = "AAOS 차량 환경에서 확인할 수 있습니다.")
            return
        }
        // The non-blocking CarServiceLifecycleListener overload was added in Android 10.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mutablePolicy.value = CarUxPolicy(message = "차량 연결에는 Android 10 이상이 필요합니다.")
            return
        }

        started = true
        val currentSession = ++session
        mutablePolicy.value = CarUxPolicy(message = "차량 UX 제한 서비스에 연결 중입니다.")
        try {
            val createdCar = Car.createCar(
                appContext,
                mainHandler,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
            ) { serviceCar, ready ->
                if (started && session == currentSession) {
                    car = serviceCar
                    if (ready) {
                        attach(serviceCar, currentSession)
                    } else {
                        releaseManager()
                        mutablePolicy.value = CarUxPolicy(message = "차량 서비스 연결이 끊겼습니다.")
                    }
                }
            }
            // createCar can invoke its lifecycle callback before returning.
            if (started && session == currentSession) {
                car = createdCar
                if (createdCar == null) {
                    started = false
                    ++session
                    mutablePolicy.value = CarUxPolicy(message = "차량 서비스에 연결할 수 없습니다.")
                }
            } else {
                disconnect(createdCar)
            }
        } catch (exception: SecurityException) {
            failStart(exception)
        } catch (exception: IllegalStateException) {
            failStart(exception)
        }
    }

    @MainThread
    fun stop() {
        checkMainThread()
        started = false
        ++session
        releaseManager()
        val previousCar = car
        car = null
        disconnect(previousCar)
        mutablePolicy.value = CarUxPolicy(message = "화면이 비활성화되어 차량 구독을 중지했습니다.")
    }

    private fun attach(serviceCar: Car, currentSession: Long) {
        releaseManager()
        val currentSubscription = subscription
        try {
            val uxManager = serviceCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                as? CarUxRestrictionsManager
            if (uxManager == null) {
                mutablePolicy.value = CarUxPolicy(
                    connected = true,
                    message = "차량에서 UX 제한 서비스를 제공하지 않습니다.",
                )
                return
            }
            manager = uxManager
            uxManager.registerListener { restrictions ->
                if (started && session == currentSession &&
                    subscription == currentSubscription && manager === uxManager
                ) {
                    publish(restrictions)
                }
            }
            // Register first so a transition during the initial read cannot be missed.
            publish(uxManager.currentCarUxRestrictions)
        } catch (exception: SecurityException) {
            failRead(exception)
        } catch (exception: IllegalStateException) {
            failRead(exception)
        }
    }

    private fun publish(restrictions: CarUxRestrictions?) {
        if (restrictions == null) {
            mutablePolicy.value = CarUxPolicy(
                connected = true,
                message = "차량 UX 제한 상태를 읽지 못했습니다.",
            )
            return
        }
        mutablePolicy.value = CarUxPolicy(
            connected = true,
            known = true,
            requiresDistractionOptimization = restrictions.isRequiresDistractionOptimization,
            activeRestrictions = restrictions.activeRestrictions,
            maxRestrictedStringLength = restrictions.maxRestrictedStringLength,
            maxCumulativeContentItems = restrictions.maxCumulativeContentItems,
            maxContentDepth = restrictions.maxContentDepth,
            message = if (restrictions.isRequiresDistractionOptimization) {
                "플랫폼에서 주행 UX 제한을 적용하고 있습니다."
            } else {
                "플랫폼의 주행 UX 제한이 해제되어 있습니다."
            },
        )
    }

    private fun failStart(exception: RuntimeException) {
        Log.w(TAG, "Unable to connect to car UX service", exception)
        started = false
        ++session
        releaseManager()
        val previousCar = car
        car = null
        disconnect(previousCar)
        mutablePolicy.value = CarUxPolicy(message = "차량 서비스 연결에 실패했습니다.")
    }

    private fun failRead(exception: RuntimeException) {
        Log.w(TAG, "Unable to read car UX restrictions", exception)
        releaseManager()
        mutablePolicy.value = CarUxPolicy(
            connected = car?.isConnected == true,
            message = "차량 UX 제한을 읽을 수 없어 기능을 제한합니다.",
        )
    }

    private fun releaseManager() {
        ++subscription
        val previousManager = manager
        manager = null
        try {
            previousManager?.unregisterListener()
        } catch (exception: SecurityException) {
            Log.w(TAG, "Car UX listener was already unavailable", exception)
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Car UX service disconnected before listener cleanup", exception)
        }
    }

    private fun disconnect(previousCar: Car?) {
        try {
            previousCar?.disconnect()
        } catch (exception: SecurityException) {
            Log.w(TAG, "Car service was unavailable during disconnect", exception)
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Car service was already disconnected", exception)
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Call CarUxMonitor.start/stop on the main thread."
        }
    }

    private companion object {
        const val TAG = "CarUxMonitor"
    }
}
