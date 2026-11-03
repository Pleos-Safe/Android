package io.issac.pleosandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.car.CarUxMonitor
import io.issac.pleosandroid.car.CarUxPolicy
import io.issac.pleosandroid.car.VehicleDoorReader
import io.issac.pleosandroid.car.VehicleDoorState
import io.issac.pleosandroid.navigation.PleosAppNavigation
import io.issac.pleosandroid.ui.theme.PleosAndroidTheme

class MainActivity : ComponentActivity() {
    private val uxMonitor by lazy { CarUxMonitor(applicationContext) }
    private val doorReader by lazy { VehicleDoorReader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val policy by uxMonitor.policy.collectAsStateWithLifecycle()
            val door by doorReader.state.collectAsStateWithLifecycle()
            PleosAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PleosAppNavigation(contentPadding = innerPadding) { contentPadding ->
                        SetupScreen(policy, door, doorReader::refresh, Modifier.padding(contentPadding))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        uxMonitor.start()
        doorReader.start()
    }

    override fun onStop() {
        doorReader.stop()
        uxMonitor.stop()
        super.onStop()
    }
}

@Composable
private fun SetupScreen(
    policy: CarUxPolicy,
    door: VehicleDoorState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The starter is parked-only. Keep a compact fallback during transitions or service loss.
    if (policy.restrictionsRequired) {
        Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Pleos 연결 확인", style = MaterialTheme.typography.headlineMedium)
            Text(if (policy.known) "주행 UI 제한 적용 중" else "차량 환경 확인 중")
            Text(policy.message, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Pleos 기본 연결", style = MaterialTheme.typography.headlineMedium)
        Text("SDK와 차량 실행 환경을 확인합니다.", style = MaterialTheme.typography.bodyLarge)
        BoxWithConstraints {
            if (maxWidth >= 840.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    VehicleCard(door, onRefresh, Modifier.weight(1f))
                    PolicyCard(policy, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    VehicleCard(door, onRefresh)
                    PolicyCard(policy)
                }
            }
        }
        Text("현재 기초 앱은 주차 중 사용하도록 설정되어 있습니다.")
    }
}

@Composable
private fun VehicleCard(door: VehicleDoorState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    StatusCard("Vehicle SDK", modifier) {
        Text(door.message)
        door.position?.let { Text("앞좌석 왼쪽 문 위치 원시값: $it") }
        Button(onClick = onRefresh, enabled = !door.loading) {
            Text(if (door.loading) "확인 중" else "차량 정보 다시 확인")
        }
    }
}

@Composable
private fun PolicyCard(policy: CarUxPolicy, modifier: Modifier = Modifier) {
    StatusCard("주행 UI 정책", modifier) {
        Text(policy.message)
        Text("키보드: ${if (policy.keyboardAllowed) "허용" else "제한"}")
        Text("영상: ${if (policy.videoAllowed) "허용" else "제한"}")
        Text("설정: ${if (policy.setupAllowed) "허용" else "제한"}")
    }
}

@Composable
private fun StatusCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 800)
@Preview(showBackground = true, widthDp = 1200, heightDp = 700)
@Composable
private fun SetupPreview() {
    PleosAndroidTheme {
        SetupScreen(
            policy = CarUxPolicy(
                connected = true,
                known = true,
                requiresDistractionOptimization = false,
                activeRestrictions = 0,
                message = "플랫폼의 주행 UI 제한이 해제되어 있습니다.",
            ),
            door = VehicleDoorState(message = "미리보기용 데이터", position = 0),
            onRefresh = {},
        )
    }
}
