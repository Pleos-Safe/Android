# Pleos 개발 가이드와 정책

2026년 11월 3일 확인한 공식 문서를 바탕으로 현재 앱 개발에 필요한 기준을 정리한다. 기능 지원, 사용 권한, 서비스 연결 상태는 별도로 확인한다. 아래 수치는 공식 문서의 해당 적용 범위를 유지하며, 배포 전에 원문의 최신 상태와 실제 콘솔 규격을 확인한다.

## 개발 환경과 SDK 버전

제품 앱의 minSdk는 API 28 이상이며, 공식 설치 가이드는 Gradle 8.0 이상과 Java 또는 Kotlin을 명시한다. 현재 에뮬레이터는 v2.1.0, Android API 34다. AVD는 Cold boot와 RAM 4 GB를 사용한다. Quick boot에서는 VHAL이 동작하지 않을 수 있다. Mac용 시스템 이미지와 Windows/Linux용 이미지는 별도 URL을 사용한다.

공식 Maven 저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`다. SDK는 필요한 모듈만 추가한다.

| SDK | 2026년 10월 19일 릴리스 노트 기준 | 주 용도 |
| --- | --- | --- |
| Vehicle | 2.3.2 | 제조사 확장 차량 상태·제어 |
| NaviHelper | 3.0.5 | 기본 내비게이션 앱 연동 |
| TextToSpeech | 3.3.1 | 음성 합성·재생 |
| SpeechToText | 3.3.1 | 음성 인식 |
| FusedLocation | 1.1.3 | 차량 센서를 결합한 위치 |

에뮬레이터 2.1.0에서는 LLM SDK, CRN, Bluetooth 동글 연동 제공이 종료됐다. Gleo 연동은 Gleo Interface를 사용한다.

근거: [에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [에뮬레이터 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator), [SDK 릴리스 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note), [에뮬레이터 릴리스 노트](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note).

## 프로젝트와 사용 승인

프로젝트 생성 시 이름, 개발사, 앱 카테고리, 설명을 입력한다. 프로젝트 ID는 심사 및 권한 연결에, 클라이언트 ID·시크릿은 서버 연결이 필요한 SDK 등록에 사용한다. 인증 정보는 문서·소스에 기록하지 않는다.

프로젝트 생성 가이드에서 ‘준비 중’은 Playground의 SDK 권한 신청 버튼이 표시되지 않는 상태다. 공개 안내는 FusedLocation, TTS, STT, Vehicle Control을 이 항목에, Vehicle State와 NaviHelper를 신청 없이 사용 가능한 항목에 둔다. 팀 프로젝트의 신청 상태는 해당 콘솔에서 확인한다.

FusedLocation 개발 가이드는 Pleos 에뮬레이터에서 개발·검증하는 방법을 제공한다. 앱에서 위치를 읽을 수 있는지는 실행 환경의 `isSupported()`, `pleos.car.permission.FUSED_LOCATION` 권한, 실제 위치 콜백으로 확인한다. [FusedLocation 개발 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)

Vehicle SDK 권한은 App Market과 사전 협의·승인한 범위에서만 선언·사용할 수 있다는 심사 기준이 별도로 있다. 즉, 조회 SDK의 신청 면제와 개별 권한의 출시 승인 조건을 혼동하지 않는다.

근거: [프로젝트와 SDK 신청](https://document.pleos.ai/docs/pleos-only/create-my-project), [SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/), [SDK 권한 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability).

## 차량 데이터와 하드웨어 접근

속도·기어처럼 AAOS에 있는 표준 기능은 AAOS Car API가 우선이다. Pleos SDK는 제조사 특화 기능과 표준으로 접근하기 어려운 영역을 제공한다. 표준에 편입되는 기존 SDK API는 최소 두 메이저 버전의 하위 호환 정책을 따른다. 물리 장치·센서에 대한 비공식 직접 접근은 허용되지 않는다.

클러스터와 동일한 차량 속도는 CarPropertyManager의 PERF_VEHICLE_SPEED로 조회한다. GPS 속도로 대체하면 표시가 달라질 수 있다. 차량 모델 이름으로 지원 여부를 분기하지 말고 실제 프로퍼티·권한·설정 또는 SDK capability를 확인한다. 미지원과 권한 부족, 일시 장애, 현재값 없음은 별도 상태다.

주행 중 통신 단절·저대역폭에 대비해 로컬 캐시, 타임아웃, 재시도와 오프라인 안내를 설계한다. 전화·SMS·모바일 센서·캘린더·주소록 등 모바일 API가 차량에도 존재한다고 가정하지 않는다.

근거: [SDK API 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api), [하드웨어 접근](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture), [API 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability), [플랫폼 FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq).

## Vehicle SDK 사용 계약

Vehicle 생성과 initialize 이후 getDoor·getEvBattery 등의 도메인 객체를 얻고, capability → 조회·구독·필요한 제어 순서로 구성한다. IPC 결과는 비동기이므로 initialize의 반환을 개별 기능 준비 완료로 해석하지 않는다. 사용 종료 시 등록한 동일 리스너와 영역으로 구독을 해제한 뒤 release한다.

CapabilityConfig.accessType은 NONE, READ, WRITE, READ_WRITE를 구분한다. AreaBounds와 영역별 범위도 함께 확인한다. get의 null, 미지원 영역, 권한 오류, 서비스 오류를 별도로 처리한다. 제어 요청의 성공 콜백과 차량의 실제 상태 변경은 다르므로 재조회·리스너로 실제 결과를 확인한다.

조회 권한은 `pleos.car.permission.CAR_*`, 제어는 `CONTROL_CAR_*` 계열이다. 정확한 권한은 각 함수의 @RequiresPermission을 기준으로 삼는다. Sample App의 전체 권한을 제품 앱에 복사하지 않는다. VehicleException.errorCode를 처리하되 모든 Exception이 VehicleException이라고 가정하지 않는다. 오류 종류에는 미지원 속성·영역, 권한, 연결 실패, 내부 오류, 주행 제한, 빈 결과 등이 있다.

근거: [Vehicle 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [오류 코드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code), [공식 샘플](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app).

## 주행 안전과 UX 제한

앱은 CarUxRestrictionsManager의 초기 상태와 변경 이벤트를 반영한다. DO를 선언한 앱도 activeRestrictions에 따라 기능을 직접 제한한다. DO 선언은 주행 중 모든 기능 허용을 의미하지 않는다. 속도가 0이라는 이유만으로 키보드·설정·동영상·배지 같은 기능을 해제하지 않는다.

| 플래그 | 값 | 제한 대상 |
| --- | --- | --- |
| NO_DIALPAD | 1 | 다이얼패드 |
| NO_FILTERING | 2 | 검색·필터링 |
| LIMIT_STRING_LENGTH | 4 | 표시 문자열 길이 |
| NO_KEYBOARD | 8 | 키보드 |
| NO_VIDEO | 16 | 동영상 |
| LIMIT_CONTENT | 32 | 콘텐츠 양·깊이 |
| NO_SETUP | 64 | 로그인·설정 등 |
| NO_TEXT_MESSAGE | 128 | 텍스트 메시지 |
| NO_VOICE_TRANSCRIPTION | 256 | 음성 전사 표시 |

지역별 허용 범위는 다르며 플랫폼이 통지하는 제한을 따른다. 북미·유럽·기타 지역의 키보드와 검색 허용 범위는 같지 않다. 동영상·게임·브라우저는 주행 중 사용을 중지하고 UI를 숨겨야 하며, 주행 중 재개를 막는다. Music/Audio의 청취와 기본 조작은 별도 카테고리 정책을 따른다.

애니메이션·자동 스크롤·반복 화면 전환과 과도한 장식을 제한하고, 짧은 정보와 최소 조작을 우선한다. 운전 중 게임·피트니스·웹 탐색을 유도하지 않는다. 배지·보상 기능도 위험 운전, 과속, 화면 주시를 유도하지 않도록 설계한다. 마지막 문장은 현재 앱에 대한 설계 적용 판단이다.

근거: [주행 규제 구현](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [지역 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy), [안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline), [운전자 주의 분산 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements).

## 화면과 디자인

Driving view는 고정 시스템 영역이며 앱이 가리거나 종료할 수 없다. 앱 영역에는 최대 두 앱이 표시된다. 일반 앱은 portrait·landscape와 크기 변경에 대응하고, 화면 이동·백그라운드 전환이 종료를 의미하지 않음을 고려한다. 상세 Window Policy에서 Connect-S와 Connect-W의 기본 배치·실행 동작을 구분한다. W에서는 새 앱이 primary에 landscape로 실행되는 등 일반 설명과 다른 세부 규칙이 있다.

Connect-S는 17·14.6·12.9인치, W는 Wide, WF는 Waterfall 형태다. 시스템 바·GNB·키보드·패널은 앱보다 상위에 표시될 수 있다. 입력 필드가 있는 window의 IME resize와 입력 필드가 없는 window·dialog의 불필요한 IME 영향을 구분해서 처리한다.

주요 UI 기준은 1~2초 내 정보 파악, 0.25초 이내 입력 피드백, 대비율 4.5:1 이상이다. 영어 단일 메시지는 120자 또는 24단어 이하, 한 줄 80자 이하, 문장·단락은 3줄 이하로 제시한다. 주요 텍스트 높이는 5 mm 이상, 보조는 3 mm 이상을 기준으로 제시한다. 이를 임의의 고정 sp 값으로 치환하지 않는다. 주야간 테마·짧은 텍스트·한 손 조작·큰 터치 영역을 적용한다.

디자인 문서는 색상·타이포·아이콘·앱 아이콘·그림자·상호작용과 버튼·필드·선택·팝업·알림·탭·페이지·바·스피너·위젯 등을 다룬다. Basic button의 pressed·disabled·loading, primary·secondary, large·medium·small 도표를 확인했다. 구체적인 토큰값·치수는 제공 Figma 원본에서 확인한다.

근거: [화면 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model), [Display Layout](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout), [Window Policy](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/window), [UI Design Guide](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro), [IME](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/ime-window-resize-handling-guide).

## CCP와 뒤로가기

사용자가 설치하는 App Market 앱의 CCP 지원은 선택 사항이고, 미지원이어도 모든 필수 조작을 터치로 완료할 수 있어야 한다. 미지원 선언은 application 내부의 `com.hmg.rotary.ROTARY_SUPPORTED=false` 메타데이터다. 기본 탑재 앱은 원칙적으로 초기 출시부터 CCP를 지원하며 예외는 계약에 명시한다.

최상위 화면의 CCP Back은 `moveTaskToBack(true)`로 앱을 숨겨야 한다. 하위 화면·팝업은 pop·닫기를 수행한다. 최상위에서 finish·finishAffinity로 종료하지 않는다. Back이 표준 Android 이벤트로 전달되는지 먼저 확인하고 API를 선택한다. 숨김 후에도 프로세스가 종료될 수 있으므로 필요한 상태를 저장한다.

근거: [CCP 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/ccp-rotary-controller-development).

## 성능과 상태 복원

| 항목 | 공식 기준 |
| --- | --- |
| 주행 중 내비게이션 CPU | 15% 이하 |
| 주행 중 기타 앱 CPU | 5% 미만 |
| 정차 중 모든 앱 CPU | 25% 이하 |
| RAM | 400 MB 이하 |
| 앱 실행 / 콘텐츠 로드 | 각각 10초 이내 |
| 버튼 동작·화면 전환 | 2초 이내, 지연 시 처리 중 표시 |
| 터치 효과 피드백 | 0.25초 이내 |

불필요한 백그라운드 타이머·폴링·애니메이션·센서 구독을 줄이고 초기화는 필요한 것부터 수행한다. 복귀·화면 크기 변경 시 상태와 작업을 복원하며 기능 패리티를 유지한다. 성능 수치는 실제 대상 환경에서 측정한다.

근거: [리소스 효율](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt), [성능·안정성 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability).

## 보안과 개인정보

기능에 필요한 최소 권한과 최소 데이터만 사용한다. 민감 데이터는 내부 저장소·DB·캐시·SharedPreferences에서도 앱 수준 암호화가 필요하며 키는 Keystore로 관리한다. 토큰·비밀 키의 하드코딩, 민감정보의 URL 삽입·평문 로그를 피한다. 불필요한 백업은 비활성화하고 필요한 백업에는 민감 데이터를 제외한다.

HTTPS 등 안전한 전송을 사용하고 TLS 1.2 이상, 가능하면 1.3을 적용한다. 인증서 검증을 우회하지 않는다. 내부 컴포넌트 노출을 최소화하고 외부 입력·URI·호출자·권한을 검증한다. WebView는 콘텐츠·파일 접근·JavaScript bridge를 필요한 범위로 제한한다. 출시에는 정식 서명과 디버깅 비활성화가 필요하다.

개인정보 항목·목적·보유기간·제3자 제공·철회 절차를 명확히 안내한다. 동의 버튼 외의 동작을 동의로 간주하지 않는다. 철회 시 이용 경로를 중단하고 해당 데이터 삭제를 처리한다. 공유 차량의 로그아웃·계정 전환, 운전자 변경도 고려한다. 위치·음성·차량 식별 데이터가 추가되면 설계 단계에서 처리 정책을 정한다.

근거: [앱 보안](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide), [개인정보 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-user-privacy-security), [보안 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy).

## 음성 위치 내비게이션 SDK

NaviHelper는 독립 지도 엔진이 아니라 기본 Navigation 앱에 요청을 전달한다. 초기 연결 성공 후 리스너를 등록하고 결과 이벤트를 확인한다. 요청 반환은 경로 탐색 완료가 아니다. 권한은 NAVI_ROUTE, NAVI_ROUTE_SEARCH, NAVI_CUSTOM_MAP 등 API별로 구분한다. 경유지는 최대 두 개이며 새 버전의 좌표 생성자 순서는 latitude, longitude다.

FusedLocation은 isSupported 확인 후 초기화한다. 마지막 위치 조회는 새 위치 계산이 아니고 null을 반환할 수 있다. `FUSED_LOCATION` 권한과 차량 개인정보 설정을 확인한다. 차단 안내 팝업은 권한 부여 기능이 아니다. 구독 해제와 SDK release를 모두 수행한다.

TTS·STT의 기본 모드는 HYBRID이며 서버 연결 실패 시 온디바이스로 전환한다. SERVER는 자동 전환하지 않고, ON_DEVICE는 서버 앱 등록이 필요 없다. 서버 사용은 초기화·프로젝트 자격 증명 등록·연결 완료 후 시작한다. 권한은 각각 TTS_SERVICE와 STT_SERVICE다. 오류는 문자열 대신 ErrorType으로 전달된다.

STT는 인식 텍스트를 반환하며 앱 기능을 실행하지 않는다. 완료 여부·EPD byte offset·RMS를 구분한다. sendAudio는 ON_DEVICE에서만 지원하고 마지막에 completeAudioSend가 필요하다. 문서의 16 kHz·16-bit·mono, 3,200 byte/100 ms는 전송 예시이며 모든 입력 형식에 고정하지 않는다. TTS는 speak와 isFinal로 발화 청크를 구성한다.

근거: [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [FusedLocation](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro), [TTS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [STT](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro).

## Gleo Interface

Gleo는 App Schema에 정의된 기능을 Android Intent로 호출하고 Callback을 통해 결과를 받는다. `assets/schema.json`에 packageName, 설명, 버전과 actionCalls를 정의한다. 기능 수는 최대 3개이며 이름은 대문자와 밑줄, description은 영어로 작성한다. 파라미터의 타입·필수값을 앱에서도 검증한다.

component=activity는 startActivity, component=service는 sendBroadcast 방식이다. service라는 명칭만 보고 Android Service 직접 실행으로 구현하지 않는다. 예시는 ACTION_PARAM JSON 문자열을 사용한다. Callback은 functionName, success·asking·fail, 영어 message, 선택 data와 voice·beep·silent 모드를 사용하며 Gleo는 7초 후 Action을 타임아웃 처리한다.

설명 본문의 파라미터별 putExtra와 실제 예제의 ACTION_PARAM 방식에 차이가 있어 연동 시 실환경 계약을 확인한다. 외부 Intent 검증과 주행 안전 정책은 음성 호출에도 적용한다.

근거: [Core concepts](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts), [App Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability), [Callback](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback), [Intent 예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples).

## 오디오와 내비게이션 카테고리

오디오 재생 전에 AudioAttributes·AudioFocusRequest를 사용하고 획득·상실·일시 상실·duck을 처리한다. 포커스 충돌 정책은 Reject > Exclusive > Concurrent 순서다. 실제 상호작용은 공식 매트릭스를 따른다. Suspend 진입 전 재생 중단, 포커스 반납, 상태 저장·자원 해제를 수행하고 Resume 때 상태를 재평가한다. 음성 안내에는 데이터 앞부분의 150~200 ms 묵음을 권장한다.

Music/Audio 앱은 표준 콘텐츠 탐색 서비스와 MediaSession, 스티어링 휠 기본 제어를 제공하고 연결·프로필 변경만으로 자동 재생하지 않는다. 음악·팟캐스트·오디오북·라디오 구조를 구분한다. Media3 연동을 권장하지만 향후 제공 예정인 Gleo 자동 음성 연동을 이미 보장된 기능으로 간주하지 않는다.

Navigation 앱은 APP_MAPS와 마켓 카테고리를 일치시킨다. 한 앱만 Active Route를 소유하고 비활성·백그라운드에서 안내하지 않는다. 주행 중 광고·영상·게임·전체 화면 팝업과 복잡한 조작을 금지하고 위치·마이크 권한을 최소화한다. 현재 앱에 음성·지도 기능을 추가하면 이 카테고리 규칙을 별도로 적용한다.

근거: [오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development), [Suspend](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus), [선행 묵음](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide), [Music/Audio](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide), [Navigation](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide).

## 테스트 환경

Virtual Console은 v2.1.0 이상에서 `adb forward tcp:8088 tcp:8088` 후 로컬 8088로 접속한다. Motion, ADAS, Convenience, Safety, Energy, Climate 등에서 기어·브레이크·도어·배터리·공조 상태를 바꾸어 앱 반응을 확인한다. 실제 상태와 앱의 오류·재연결·제한 적용을 함께 검증한다.

폼 팩터 테스트는 독립 창의 Emulator, Host Display Bridge, FormFactorConsole을 사용한다. Go 1.22 이상과 포트 8095·8096이 필요하다. Android Studio 내장 Running Devices 또는 headless에서는 가상 디스플레이가 표시되지 않는다. 방향·해상도·dp·DPI, 잘림·겹침·터치·복원을 확인한다. 가상 디스플레이 제거는 그 디스플레이의 앱을 강제 종료한다.

현재 앱의 우선 시나리오는 정차→출발→일시정차→P, 배지 화면을 연 채 출발, UX 정보 미확인·실패·복구, 속도 권한 거부, 프로퍼티 미지원, 서비스 단절, 화면 크기 변경과 Activity·프로세스 재생성이다. 이는 현재 구현에 맞춘 테스트 설계 항목이다.

근거: [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide), [폼 팩터](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide).

## 앱 심사와 배포

제출물은 실제 서비스와 동일한 완성 앱이어야 한다. 더미 콘텐츠·임시 파일을 제거하고 로그인 필요 시 전체 기능에 접근 가능한 일반 사용자 심사 계정을 제공한다. 카테고리·국가·연령 등급·개인정보·위치·권한·광고 정보를 정확히 기재한다. 위험 운전 조장, 불법·유해 콘텐츠, 권리 침해, 악성 행위는 금지된다. UGC·AI 콘텐츠는 필터와 신고 기능이 필요하다.

| 제출 항목 | 문서 기준 |
| --- | --- |
| 패키지 | APK, 최대 600 MB. 등록정보 심사는 압축 크기 600 MB 미만으로 표현 |
| 아이콘 | PNG/JPEG, 최대 1 MB, 512×512 px |
| 스크린샷 | 최소 2장·최대 8장, 장당 최대 8 MB, PNG/JPEG |
| 스크린샷 크기 | 상세 등록정보 기준 2560×1380, 1700×1212, 840×1212 px |
| 앱 이름 | 영문 기준 30자 이내 |
| 태그 | 최대 20개 |
| 심사노트 첨부 | 최대 60 MB |

개인정보처리방침 URL과 지원 연락 채널을 준비한다. 위치정보를 사용하면 해당 약관을 추가한다. 출시 지역별 메타데이터·정책 언어와 IARC 등급 증빙을 준비하고 필요 시 저작권·기관·도메인 관련 증빙을 제출한다.

배포·심사 요청마다 버전을 변경한다. App Market 자동 업데이트는 지원하지 않으므로 최신 버전 확인·안내·마켓 이동을 앱에서 구현한다. 상세 화면 URI는 `market://detail?package={패키지명}`다. 설치 이후에도 품질 모니터링·재심사·마켓 제외가 가능하다.

근거: [심사 제출](https://document.pleos.ai/docs/pleos-only/distribute/app-submission-guide), [등록정보](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-app-registration-info), [콘텐츠](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-content-safety), [버전 관리](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-version-management-policy), [업데이트](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-update-guide).

## 홈페이지 약관과 데이터 처리

서비스 이용약관은 2025년 3월 28일 시행본이다. 승인받은 앱·API 범위와 개발 가이드를 준수해야 하며, 권한·계정의 양도·공유, API의 재제공, 허용량 우회를 위한 다중 프로젝트, 무단 고객 데이터 처리 등을 제한한다. 유료 앱·이용료 부과는 별도 계약 대상으로 명시한다. 개발도구 역공학·재배포와 성능 테스트 결과 공개에도 제한이 있으므로 배포 방식과 계약 범위를 확인한다.

통합 개인정보 처리방침은 2026년 6월 12일 시행본이다. Playground는 계정 식별 정보·이름·이메일을 처리하며, 원칙적으로 계약 종료 후 3개월 이내 파기하되 법정 보존·분쟁 등 예외를 둔다. 국외 위탁, 정보주체 권리와 민원 절차를 안내한다. 이 방침은 플랫폼 운영사의 방침이며 우리 앱 자체의 데이터 처리방침을 대체하지 않는다.

쿠키 정책은 필수 쿠키와 선택 분석 쿠키를 구분한다. 선택 쿠키에는 Datadog·Google Analytics가 포함되며 동의 변경·철회 경로를 제공한다. 문서의 쿠키 이름은 정책 설명이며 실제 브라우저 토큰·쿠키값은 조사·저장하지 않았다.

근거: [이용약관](https://developers.pleos.ai/playground/external/use-policy?partnerId=672505ad-2e37-4500-8162-000bcfc05b5c&locale=ko-KR), [통합 개인정보 처리방침](https://developers.pleos.ai/playground/external/privacy-policy?partnerId=672505ad-2e37-4500-8162-000bcfc05b5c&locale=ko-KR), [쿠키 정책](https://developers.pleos.ai/playground/cookie-policy).

## 클라우드 API와 추가 문서의 적용 범위

Fleet API는 계약·승인 후 제공되는 REST와 Webhook으로 차량·단말·매칭 등을 관리한다. Client Credentials 방식으로 토큰을 발급하며 문서상 만료는 3,600초다. Webhook에는 주기·이벤트 데이터가 있고 필드는 선택적으로 누락될 수 있다. 중복 수신·재시도와 응답 코드를 고려한다. 엔드포인트는 계약 후 제공되므로 임의로 추정하지 않는다.

Vehicle Data API는 문서상 유럽 커넥티드 카 대상이다. 사업자는 소유자 로그인·공유 동의·승인 후 사용하며, 기초 데이터는 공유 동의 후 24시간이 지나야 조회된다. access token은 1시간, refresh token은 90일이며 갱신 때 회전한다. 철회 알림 수신 시 해당 차량 데이터를 즉시 삭제하고 노출을 중단한다. 개인용 키 발급·관리 흐름은 사업자 프로젝트와 별개다. 문서의 지역·기간 조건을 차량 내 실시간 센서 API에 적용하지 않는다.

사이트맵에서 확인한 Platform SDK는 승인된 privileged 앱 대상이다. 정확한 버전의 compileOnly stub에 의존하며 runtime을 APK에 넣지 않는다. signature|privileged 권한과 제품별 allowlist 등 통합 조건이 필요하다. 1.0.0-20261023 자료의 빌드 대상은 Pleos Connect 3.5·API 34이며 제품별 지원 범위는 명시되지 않는다. 이 릴리스의 NDAS는 스텁만 제공하며 실제 전송에 의존하면 안 된다.

Connect Mobile 문서는 별도 모바일 서비스·사내용 콘솔의 프로필·차량 공유·원격 제어·공조·충전·V2L·예약을 설명한다. 공개 문서 열람만으로 해당 운영 시스템 접근 권한이 생기지 않는다. InfoLINK 보충 주행 정책도 별도 대상이다. 준비 중 Payment SDK 문서는 제공 계획이며 현재 앱에 사용 가능한 결제 기능으로 가정하지 않는다.

근거: [Fleet 시작](https://document.pleos.ai/api-reference/fleet-api/getting-started), [Vehicle Data 개요](https://document.pleos.ai/api-reference/vehicle-data-api/intro), [동의 철회](https://document.pleos.ai/api-reference/vehicle-data-api/getting-started/for-business-users/notify-data-sharing-consent-withdraw), [Platform SDK 시작](https://document.pleos.ai/api-reference/pleos-platform-sdk/get-started), [Platform SDK 릴리스](https://document.pleos.ai/api-reference/pleos-platform-sdk/release-notes).

## 문서 불일치와 적용 시 확인할 사항

- 빠른 시작의 Vehicle SDK 예시는 2.3.1이지만 상세 설치·릴리스 노트는 2.3.2다. 배포 대상과 맞는 상세 문서·artifact를 기준으로 확인한다.
- 샘플 앱 minSdk=23은 제품 배포 요건 API 28의 예외 근거가 아니다. 샘플 가이드도 이 차이를 명시한다.
- 심사 제출 표의 작은 스크린샷은 840×121로 기재되어 있으나 등록정보 상세 가이드는 840×1212다. 제출 콘솔에서 최종 확인한다.
- Vehicle SDK 일반 Set 가이드와 샘플의 완료 콜백 설명에 차이가 있으므로 실제 사용하는 함수의 API Reference와 SDK 선언을 확인한다.
- Gleo 예제와 설명의 Intent 파라미터 전달 방식이 일치하지 않아 실제 계약 확인이 필요하다.
- 디자인 가이드의 OTA 일반 설명과 마켓의 업데이트 정책을 혼동하지 않는다. App Market 배포 앱에는 자동 업데이트 미지원 안내가 구체적으로 존재한다.
- SDK 문서 제공, 사용 신청 상태, 권한 승인, 실차 가용성은 서로 다른 조건이다.
