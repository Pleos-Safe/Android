package io.issac.pleosandroid.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VehicleDoorState(
    val loading: Boolean = false,
    val message: String = "차량 서비스 연결 전",
    val position: Int? = null,
)

/** A read-only SDK probe. Vehicle controls belong in the feature-specific implementation. */
class VehicleDoorReader(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val area = DoorArea(DoorValue.ROW_1_LEFT)
    private val mutableState = MutableStateFlow(VehicleDoorState())
    val state = mutableState.asStateFlow()

    private var vehicle: Vehicle? = null
    private var generation = 0
    private var request = 0
    private var timeout: Runnable? = null

    fun start() {
        if (vehicle != null) return
        generation++
        try {
            vehicle = Vehicle(context)
            vehicle?.initialize()
            refresh()
        } catch (error: Exception) {
            mutableState.value = VehicleDoorState(message = "SDK 연결 실패: ${error.message}")
            stopConnection()
        }
    }

    fun refresh() {
        val current = vehicle ?: return
        val session = generation
        val reading = ++request
        mutableState.value = VehicleDoorState(loading = true, message = "차량 정보 확인 중")
        timeout?.let(handler::removeCallbacks)
        timeout = Runnable {
            if (generation == session && request == reading && vehicle != null) {
                request++
                mutableState.value = VehicleDoorState(message = "응답 시간 초과 · 에뮬레이터 부팅 상태를 확인해 주세요")
            }
        }.also { handler.postDelayed(it, 10_000) }

        fun complete(value: VehicleDoorState) {
            handler.post {
                if (generation == session && request == reading && vehicle != null) {
                    request++
                    timeout?.let(handler::removeCallbacks)
                    timeout = null
                    mutableState.value = value
                }
            }
        }

        fun failed(error: Exception) {
            complete(VehicleDoorState(message = "차량 정보 확인 실패: ${error.message}"))
        }

        try {
            val door = current.getDoor()
            door.checkDoorPositionCapabilities(
                onComplete = { _, areas ->
                    // A capability callback may arrive after the Activity has stopped.
                    handler.post {
                        if (generation == session && request == reading && vehicle != null) {
                            if (areas.containsKey(area)) {
                                try {
                                    door.getCurrentDoorPosition(
                                        area = area,
                                        onComplete = { position ->
                                            complete(VehicleDoorState(
                                                message = if (position == null) "값을 제공하지 않는 차량 상태" else "Vehicle SDK 응답 정상",
                                                position = position,
                                            ))
                                        },
                                        onFailed = ::failed,
                                    )
                                } catch (error: Exception) {
                                    failed(error)
                                }
                            } else {
                                complete(VehicleDoorState(message = "이 차량에서 앞좌석 왼쪽 문 상태를 지원하지 않습니다"))
                            }
                        }
                    }
                },
                onFailed = ::failed,
            )
        } catch (error: Exception) {
            failed(error)
        }
    }

    fun stop() {
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        stopConnection()
        mutableState.value = VehicleDoorState()
    }

    private fun stopConnection() {
        val current = vehicle
        vehicle = null
        try {
            current?.release()
        } catch (error: Exception) {
            Log.w("VehicleDoorReader", "Vehicle service unavailable during cleanup", error)
        }
    }
}
