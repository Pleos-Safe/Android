# 기능 모듈의 책임과 화면 상태 관리

## 기능 소유권

| 모듈 | 소유하는 책임 |
| --- | --- |
| `app` | 기능 등록과 앱 수준 조립, 홈 카드 콘텐츠 연결 |
| `feature:main` | 탭 호스트, 탭별 이동 이력, 최신 차량 제한 검사, 여정 결과로 홈 전환 |
| `feature:home` | 주행 전·정차·주행·종료 표시, 추천과 방문 보상 UI |
| `feature:explore` | 관광지 검색·지도·장소 상세, 목적지·경유지 설정 UI |
| `feature:records` | 여정 목록·안전 리포트·포인트·기록 저장 설정 |
| `feature:settings` | 일반 설정 진입 화면 |
| `feature:membership` | PLUS 상점과 구독 관리 |
| `feature:collection` | 팩 보관·개봉·뒤집기, 컬렉션·대표 카드, 연출 목록과 개봉 미리보기 |
| `feature:forge` | 강화 대상 선택, 강화·흔적 복구, 결과 확인과 강화 미리보기 |
| `core:card-ui` | 두 기능이 사용하는 카드 원화·프레임·재질, 카드 목록과 전시 레이아웃 |
| `core:domain` | 주행·장소·보상 모델, 정책, 저장소 계약 |
| `core:vehicle` | 차량·위치·외부 내비 SDK 연결과 공유 장소 세션 |
| `core:data` | 기록·포인트·방문팩 저장과 데이터 변경 |
| `core:navigation` | 화면 경로와 UI 이동 계약 |

홈과 탐색은 상대 기능의 ViewModel을 참조하지 않는다. `app`이 각 기능의 `NavEntryFactory`를 조립한다. 기존 `feature:nav`는 탭 호스트가 사용하는 공통 화면 렌더러다. 기존 `MainRoute`의 직렬화된 이름을 유지해 저장된 화면 복원과 호환한다.

컬렉션과 강화는 각자의 화면 경로를 등록하고 `CardAlbumRepository.observe()`로 보관함을 읽는다. 포인트 차감과 강화 결과 기록은 한 저장 트랜잭션에서 처리한다. 강화나 개봉 후 컬렉션으로 돌아갈 때는 탐색 결과로 사본 ID를 전달하고, 컬렉션이 해당 카드를 표시한 뒤 소비한다.

## MVVM과 단방향 데이터 흐름

사용자 동작은 `Intent`로 ViewModel에 전달한다. ViewModel은 저장소의 Flow와 화면 선택 상태를 결합해 읽기 전용 `StateFlow<State>`를 제공한다. Compose 화면은 전달받은 상태를 그리고 동작 콜백을 호출한다.

각 기능의 Contract가 화면 상태와 입력을 정의한다. 홈과 탐색 등 화면 밖으로 일회성 동작을 전달하는 기능은 Effect도 정의한다. 컬렉션과 강화는 각각 `CollectionContract`와 `ForgeContract`로 보관함 로딩·재시도·사용자 작업을 관리한다. 탭 호스트의 `MainState`는 차량과 여정 상태에서 파생되며, 탭 이동 이력은 UI의 `MainTabNavigation`이 소유한다.

포인트 적립, 카드팩 지급, 정산 결과와 재시도 오류는 저장소 또는 상태에 남긴다. 일회성 효과는 화면 이동과 권한 요청에 사용한다. Channel은 영속 전달을 보장하는 수단으로 사용하지 않는다. 효과 소비 시 현재 화면 경로와 최신 차량 상태를 다시 확인한다.

탐색의 검색어·선택 장소·지역·목록 모드·경유지 확인은 `SavedStateHandle`에 보관한다. 지도 카메라와 같은 UI 표시 상태는 Compose와 MapLibre의 저장·복원 기능으로 관리한다.

## 동시성과 생명주기

- 홈과 탐색은 `PlaceSessionRepository`의 같은 내비 요청 상태를 구독한다. 내비 변경 요청과 확인 중인 요청의 소유자는 하나다.
- SDK 명령과 콜백 처리는 메인 디스패처에서 직렬화한다. 요청 직전 최신 차량 제한과 경로를 확인한다.
- 추천 클릭은 화면에 표시한 장소 ID와 목적지 유무를 전달한다. 추천이 바뀌면 이전 화면의 클릭으로 다른 장소를 요청하지 않는다.
- 방문팩 클릭은 표시한 방문 후보와 데이터 세대를 전달한다. 실제 위치·주차·여정·보상 조건은 공유 세션과 저장소에서 다시 검사한다.
- 여정 종료는 작업 대기 상태로 중복 요청을 차단한다. 실패하면 대기 상태를 풀어 재시도를 허용한다. 취소 예외는 전파한다.
- 홈·탐색 ViewModel은 공유 세션의 소유권을 획득하고 `onCleared`에서 반환한다. 마지막 소유자가 사라지면 위치 콜백·내비 리스너·타임아웃과 런타임 작업을 종료한다.
- 위치와 주행 화면은 각 소유자 토큰으로 시작·종료한다. 이전 화면의 종료가 새 화면의 수집을 중지하지 않는다.
- Compose는 `collectAsStateWithLifecycle`로 상태를 수집하고, UI 효과는 `repeatOnLifecycle(STARTED)`에서 처리한다. 화면 밖에서 돌아온 권한 응답은 활성 경로를 다시 확인한다.
- SDK 연결에는 Application Context를 사용한다. MapLibre 리스너와 MapView는 Compose 생명주기에 맞춰 해제한다.

## 코드 배치

ViewModel은 상태 선언·초기화, 입력 처리와 공개 동작, 비공개 처리 함수 순으로 읽을 수 있게 배치한다. 화면 연결은 Route 또는 Destination에 두고 UI 컴포넌트에 ViewModel을 전달하지 않는다. 홈 화면은 주행·주차·종료 단계로 파일을 구분하고 포인트 표시 계산은 UI 연결에서 분리한다.

함수는 하나의 판단 또는 UI 구역을 설명하는 단위로 나눈다. 단순 위임을 위한 계층이나 사용하지 않는 이벤트 타입을 추가하지 않는다. 기능 내부에서만 쓰는 타입과 구현은 `internal` 또는 `private`로 제한한다.

## 참고한 공식 자료

NIA의 기능별 모듈 소유권과 앱 조립 원칙을 현재 프로젝트의 공통 경로 계약과 Hilt 화면 등록 방식에 적용했다. UDF는 ViewModel과 저장소 사이의 Flow, 불변 화면 상태와 입력 콜백으로 구성한다.

- [Now in Android 아키텍처](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)
- [Now in Android 모듈화](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)
- [Android UI 이벤트 지침](https://developer.android.com/topic/architecture/ui-layer/events)

AAOS 예제에서는 차량 API의 연결 소유권과 생명주기에 따른 연결 해제를 확인했다. 이 프로젝트에서는 같은 원칙을 공유 SDK 세션의 명시적 소유권과 콜백 해제에 적용했다.

- [공식 AAOS CarGearViewer 예제](https://github.com/android/car-samples/blob/main/car-lib/CarGearViewerKotlin/automotive/src/main/java/com/example/cargearviewer/MainActivity.kt)
