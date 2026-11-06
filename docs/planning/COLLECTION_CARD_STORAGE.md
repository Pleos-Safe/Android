# 컬렉션 저장 구조와 사용 가이드

카드 획득·진화·공개·대표 선택은 Room DB가 원본이고, 화면의 필터·페이지·애니메이션은 UI 상태다. 실제 사용자 DB는 소유 카드 0장으로 시작한다. 디버그의 ‘데모 카드 체험’은 별도 DB를 사용한다.

## 모듈 의존성

화살표는 Gradle 의존 방향이다.

```text
:core:database ← :core:data → :core:domain ← :feature:badges
                                ↑                 ↓
                           :core:vehicle       :core:ui

:app → :core:data, :core:vehicle, :feature:*
```

| 모듈 | 책임 |
| --- | --- |
| `core:database` | Room Entity/DAO, 스키마, SQLCipher DB 생성, Keystore 키 보호. domain/feature에 의존하지 않는다. |
| `core:data` | domain Repository 구현, 트랜잭션, Entity 매핑, 카탈로그 로딩, Hilt 바인딩. |
| `core:domain` | 순수 Kotlin 모델/Repository 계약, `AchievementEvaluator`, 차량 조작 제한을 조합하는 UseCase. Android·Room·리소스 ID가 없다. |
| `core:ui` | `UiState`, `UiIntent`, `UiEffect`, `BaseViewModel`. |
| `feature:badges` | 화면별 MVI, domain → UI 모델 변환, 카드 렌더링·애니메이션. DAO/data 모듈을 참조하지 않는다. |
| `app` | 구현 모듈 조립, 전역 획득 알림 호스트. |

단순 조회는 Repository의 Flow를 직접 사용한다. 차량 상태 확인이 필요한 공개 시작/완료와 대표 선택에는 `BeginCardRevealUseCase`, `CompleteCardRevealUseCase`, `SetRepresentativeCardUseCase`를 사용한다. 방문 기록과 카드 지급을 화면에서 따로 호출하지 않는다.

## 패키지와 MVI

`feature/badges/.../feature/badges/` 아래를 기능 단위로 나눴다.

| 패키지 | 화면/책임 |
| --- | --- |
| `collection` | 컬렉션 선반, 필터, 목록 상태와 Intent. `component`에 필터 다이얼로그. |
| `inbox` | 미개봉·진화 대기 목록. 목록 ViewModel 계약을 재사용한다. |
| `detail` | 카드 상세·기억 목록. |
| `reveal` | 카드 개봉/진화, 공개 토큰, 저장 결과. `motion`에 연출 코드. |
| `representative` | 대표 카드 선택과 저장 후 이동. |
| `guide` | 획득 안내·장소·기억 보기. |
| `home` | 홈 대표 카드, 읽기 전용 Flow. |
| `notice` | DB의 미확인 획득 알림 구독/확인. |
| `model` | 화면 모델·리소스 매퍼. 샘플 모델은 debug 소스셋에 있다. |
| `component` | 공용 컬렉션 UI, 로딩/오류 UI. |
| `navigation` | Route, NavEntryFactory, Effect 처리, 선반 복귀 대상. |

화면은 `Intent → ViewModel → Repository/UseCase → StateFlow<UiState> → Screen` 순서다. `viewModelScope`에서 조회/저장을 실행하고 Compose는 `collectAsStateWithLifecycle()`로 상태를 구독한다. Room 작업은 IO dispatcher와 트랜잭션 안에서 처리한다.

화면 이동은 `Channel`의 `Flow<CollectionEffect>`로 전달하고, RESUMED·현재 Route·차량 조작 허용 상태에서 소비한다. 획득 알림 같은 중요한 결과는 이 스트림에만 보관하지 않고 DB에 저장한다. Activity 범위의 `CollectionNavigationViewModel`에는 차량 상태와 선반 복귀 대상만 있다. 모든 카드 데이터를 공용 ViewModel에 넣지 않는다.

SavedStateHandle은 필터와 선반 복귀 대상을 복원한다. 조작 중 회전 각도·빛 위치·탭 드래그·필터 다이얼로그 초안은 Compose 상태로 남긴다. 기존 카드 재질과 레벨별 애니메이션 코드를 재사용한다.

## 다른 feature에서 기록하기

`core:domain`에 의존하고 `ProgressRepository`를 Hilt로 주입한다. 조건 정의는 카탈로그에, 실제 행동은 `recordProgress()`에 전달한다. 화면이 카드 ID별 레벨이나 누적 횟수를 직접 올리지 않는다.

```kotlin
// 수집/분석 작업을 시작할 때 받아 작업과 함께 보존한다.
val generation = progressRepository.getDataGeneration()

// visitId와 operationId는 재시도마다 새로 생성하지 않는다.
val event = ProgressEvent.VisitConfirmed(
    operationId = "visit:$visitId:confirmed:v1",
    dataGeneration = generation,
    visit = ConfirmedVisit(
        id = visitId,
        placeId = placeId,
        journeyId = null, // 연결할 완료 여정이 있으면 그 ID
        arrivedAt = arrivedAt,
        departedAt = departedAt,
        confirmedAt = confirmedAt,
        zoneId = "Asia/Seoul",
        distanceFromPlaceMeters = distanceFromPlaceMeters,
        accuracyMeters = accuracyMeters,
    ),
    storageConsent = hasStorageConsent,
)

when (val result = progressRepository.recordProgress(event)) {
    is RecordProgressResult.Applied -> {
        val newlyAcquired = result.receipt.newCards
        val upgraded = result.receipt.upgrades
        val progress = result.receipt.progress
        // 화면 고유의 저장 완료/진행도를 갱신한다.
        // 공통 카드 획득 알림은 app의 호스트가 표시한다.
    }
    is RecordProgressResult.AlreadyProcessed -> Unit // 동일 입력의 기존 결과
    is RecordProgressResult.Rejected -> handleRejection(result.reason)
    RecordProgressResult.StorageFailure -> offerRetryWithSameEvent(event)
}
```

위 코드는 호출 예시이며 시간·위치·동의 값은 호출 기능에서 실제 값으로 채운다. `storageConsent=true`를 무조건 넣지 않는다. 운전습관은 기록 탭의 저장 설정과 `DrivingRecorder`가 동의를 확인하며, 자동 장소 수집은 같은 저장 동의와 위치 권한을 확인하는 `VisitRecorder`가 처리한다.

- `previewProgress(event)`: 쓰기 없이 예상 변화/진행도 또는 거절 이유를 반환한다. 요청 ID를 소비하지 않는다. 미리보기와 실제 저장 사이에 다른 작업이 반영되면 결과가 달라질 수 있다.
- `getProgressResult(operationId)`: 커밋된 처리 결과를 재조회한다. 현재 카드 상태는 Flow로 읽는다. 기록 삭제 후 개인정보 정리로 이전 receipt 상세가 비어 있을 수 있다.
- `JourneyCompleted`: 종료된 여정 요약을 저장한다. 분석 미완료면 `AssessmentStatus.Pending`; 조건이 맞지 않아도 여정은 기록된다.
- `JourneyAssessmentUpdated`: 같은 여정에 더 높은 `assessmentVersion`으로 분석 결과를 반영한다. 여정 시작/종료/거리/시간대는 바꿀 수 없다. 동일 여정을 두 번 적립하지 않는다.
- 방문에 `journeyId`를 넣으면 그 여정이 먼저 저장돼 있어야 한다. `driving_draft`는 측정 복구용이며 완료 여정 FK로 사용할 수 없다. 여정 없이 확정된 방문은 nullable 연결을 사용한다.
- Pending/Insufficient 분석으로 안전운전 카드를 지급하지 않는다. 점수·급출발 등의 필수 측정값이 없으면 해당 조건을 충족하지 않은 것으로 본다.

`Applied`여도 새 카드가 없을 수 있다. 진행도만 늘거나 이미 최고 레벨일 수 있다. `Rejected`는 입력 미반영, `StorageFailure`는 저장 실패다. 코루틴 취소는 실패 값으로 삼키지 않고 전파한다.

같은 `operationId`에 다른 입력은 `ConflictingOperation`, 같은 원본 ID의 다른 내용은 `ConflictingSource`다. 다른 요청 ID로 같은 원본이 와도 적립하지 않으며 그 별칭 ID도 바인딩한다. 호출자가 같은 물리적 방문에 새 `visitId`를 계속 부여하면 자동으로 알아낼 수 없으므로, 방문 세션 ID를 수집 기능에서 안정적으로 유지해야 한다.

## 조회 API

| Repository | 제공 API와 사용처 |
| --- | --- |
| `CollectionRepository` | `observeCollection`, `observeCard`, `observeCardProgress`, `observePendingReveals`, `observeRepresentativeCard`, `observeRewardNotices` |
| `PlaceRepository` | `observePlaces(PlaceQuery(text, bounds))`, `observePlace(placeId)`. 활성 장소를 소유 여부와 관계없이 조회한다. |
| `HistoryRepository` | `observeJourneys`, `observeJourney`, `observeVisits(HistoryQuery(placeId, journeyId))` |
| `CatalogRepository` | `applyCatalog`, `reevaluateRules` |
| `CollectionDemoRepository` | debug에서만 `setEnabled`, `resetDemo`, `enabled`, `available` |

목록/상세/공개 대기/대표/장소/홈이 DB 변경을 구독한다. 여러 테이블의 값은 같은 트랜잭션에서 스냅샷으로 읽으므로 저장 중간 상태가 UI에 나오지 않는다. 현재 소규모 카탈로그는 revision 기반 전체 스냅샷을 사용한다. 기록이 많아지는 단계에서는 DAO별 쿼리·Paging으로 최적화할 수 있다.

## 스키마와 저장 위치

`core/database/schemas/io.issac.pleosandroid.core.database.CollectionDatabase/3.json`이 현재 Room 스키마다. `migration1To2`는 기존 여정·카드를 유지하고 nullable `journey.drivingJson`과 `driving_draft`를 추가한다. `migration2To3`는 지도 지역 표시용 nullable `place.regionId`를 추가한다. 번들 카탈로그 v2가 기존 부산 장소에 `KR-26`을 채운다. 상세 측정값이 없는 이전 기록을 임의로 채우지 않는다. 파괴적 재생성 fallback은 사용하지 않는다.

| 테이블 | 역할/핵심 제약 |
| --- | --- |
| `catalog_state` | 콘텐츠 버전과 해시. DB 스키마 버전과 별개. |
| `place` | 검색 정보·위경도·방문 반경/체류시간·이미지 키·지도 지역 ID·활성 여부. |
| `card_definition` | 안정된 카드 ID, 장소 연결, 원화/광택/각인 자산 키, 활성 규칙 버전. |
| `card_level_rule` | 카드+레벨+버전 복합 PK. 1~5레벨의 누적 횟수·시작 시점·점수/거리/급출발 조건. |
| `journey` | 종료 여정 요약·분석 상태/결과/버전·운전습관 관찰 요약. 안전운전 달성의 원본. |
| `driving_draft` | 진행 중/저장 대기 여정 한 건의 암호화된 집계값·generation. 원본 속도 시계열은 저장하지 않는다. |
| `visit` | 확인된 방문, nullable 여정 FK, 도착/이탈/확정 시각·시간대·판정 값/버전. |
| `progress_operation` | 요청 ID, 원본 ID/버전, 입력 해시, receipt, 무효화 상태. |
| `card_memory` | 카드별 방문/여정 기억. 출처는 정확히 하나. 카드+방문, 카드+여정 각각 unique. |
| `owned_card` | 카드별 소유 한 건, ownership ID, earned/revealed level. 1~5레벨과 공개 범위를 DB trigger로 제한. |
| `card_level_state` | 소유+레벨별 grant ID, 당시 규칙 버전, 획득/공개 시각. |
| `reward_notice` | 보상 변화 알림. 요청+카드 unique, 확인 시각 별도 저장. |
| `collection_display` | 대표 카드 한 장. 소유 제거 시 FK가 null로 정리됨. |
| `user_data_state` | 초기화 세대 ID와 화면 구독용 revision. |

실제 파일은 앱 전용 `databases/collection.db`, debug 체험은 `collection-demo.db`다. SQLCipher 4.14.1/Room 2.8.4를 사용한다. 랜덤 DB 키는 Android Keystore의 AES-GCM 키로 감싼 뒤 `noBackupFilesDir`에 보관한다. DB 파일과 키를 자동 백업/기기 전송에서 제외했다. 키가 사라진 기존 DB를 조용히 지우고 새로 만들지 않는다. DB 오류는 화면의 로드/저장 실패로 처리한다.

여정·방문·카드에 별도 DB 파일을 만들지 않는다. 원본 저장, 기억, 소유/레벨, 알림, receipt는 같은 Room 트랜잭션에서 반영된다. 중간 실패는 전체 롤백한다. SQLCipher API는 [공식 Android 라이브러리 문서](https://github.com/sqlcipher/sqlcipher-android/blob/master/README.md)에서 확인할 수 있다.

## 획득·공개·진화·알림

| 저장 상태 | 의미 |
| --- | --- |
| 소유 행 없음 | 미획득 |
| earned=1, revealed=null | 미개봉 |
| earned=3, revealed=2 | 2→3 진화 확인 대기 |
| earned=3, revealed=3 | 현재 획득 레벨까지 공개 완료 |

일반 조회는 미개봉 카드의 `front`를 null로 반환한다. 공개 화면 진입 시 `beginReveal()`의 `RevealSession`이 앞면과 공개 토큰을 제공한다. 토큰에는 generation/card/ownership/grant/targetLevel이 있다. 공개 연출이 완료된 뒤 `completeReveal(token)`이 성공해야 화면의 공개 상태를 확정한다. 저장 실패 시 다시 열 수 있고, 이전 토큰으로 같은 완료를 재시도해도 안전하다.

순차 공개 정책이다. earned=5, revealed=2면 다음 공개는 3이며 4·5는 대기 상태로 남는다. 연출 도중 추가 레벨을 얻어도 진행 중 카드의 크기·인쇄·재질 상태를 새 결과로 교체하지 않는다. 해당 grant가 살아 있으면 원래 목표 공개를 완료할 수 있다. 삭제/하향/재획득으로 토큰이 무효가 되면 완료를 거절한다.

애니메이션 프레임은 저장하지 않는다. 완료 전 뒤로가기·조작 제한·프로세스 종료 시 공개 대기를 유지한다. 화면 재진입 시 현재 미공개 단계부터 다시 시작한다. 공개 완료 저장 후 화면 이탈은 이미 완료된 공개를 되돌리지 않는다.

알림 확인과 카드 공개 확인은 별개다. `CollectionRewardNoticeHost`를 app에 한 번만 배치했다. RESUMED이며 차량 조작이 허용될 때 미확인 알림을 표시하고, 사용자가 확인/닫기한 뒤 ack를 저장한다. 저장 실패 시 재확인을 제공한다. 표시와 ack 사이 프로세스가 종료되면 알림이 다시 보일 수 있지만 카드 지급은 중복되지 않는다.

대표 카드에는 이미 공개한 카드를 선택할 수 있다. 진화 대기 중에는 마지막 공개 레벨로 표시한다. 선택 저장 성공 후에만 다음 화면으로 이동한다.

## 장소·이미지·조건 추가

1. `core/data/src/main/assets/collection/catalog.json`에 새 장소/카드/규칙을 추가하고 pack `version`을 올린다.
2. 이미지 파일을 앱에 포함되는 drawable에 추가한다. manifest는 `R.drawable` 숫자가 아닌 파일의 리소스 키를 쓴다. 동적 키가 release 축소로 사라지지 않게 `collection_resources_keep.xml` 또는 `milestone_resources_keep.xml` 패턴에도 포함시킨다.
3. 같은 장소에 다른 카드면 place ID를 유지하고 card ID만 새로 만든다. 이미지 교체는 card ID를 유지하고 콘텐츠 버전을 올린다.
4. 기존 규칙 내용을 바꾸려면 새 rule version을 추가하고 card의 `ruleVersion`을 변경한다. 이전 규칙은 당시 획득 근거를 검증하는 데 쓰므로 지우거나 덮어쓰지 않는다.
5. 적용은 Repository 초기화의 번들 로딩 또는 `CatalogRepository.applyCatalog(pack)`이다. ID·참조·좌표·이미지·레벨 조건을 검증한 후 병합하며 소유/공개 상태를 덮어쓰지 않는다. 목록에서 누락시키는 것으로 삭제되지 않으므로 중단할 콘텐츠는 `active=false`로 보낸다.

새 조건 종류를 추가하려면 domain `AchievementEvaluator` 지원이 필요하다. 기존 ‘방문 횟수’와 ‘점수/거리/급출발 조건을 만족한 여정 횟수’의 숫자 조합은 카탈로그만으로 변경 가능하다. 장소 카드에 여정 점수 같은 미지원 조건을 지정하면 조용히 무시하지 않고 카탈로그를 거절한다. 여러 조건은 AND, 레벨은 누적 조건이다.

카탈로그 반영 자체로 소급 지급하지 않는다. 과거 기록에도 지급하려면 대상 card IDs와 안정된 operation ID를 담은 `ReevaluateRulesRequest`를 호출한다. 비소급 조건은 `eligibleFrom`을 출시 시점으로 설정해야 다음 방문 때 과거 기록을 합산하지 않는다. 단순 콘텐츠 변경으로 기존 지급을 회수하지 않는다.

현재 번들은 6곳/8종, 레벨별 1~5회, 방문 반경 250m·체류 60초, 안전 귀가 85점/1km, 부드러운 출발 70점/1km/급출발 0회다. 구현·시연용 초기 값이며 최종 운영 기준이 아니다. 안전 귀가 카드는 현재 ‘점수 조건을 충족한 종료 여정’으로 평가하며 실제 집 도착 판정이 필요하면 조건과 원본을 확장해야 한다.

## 운전습관 측정과 여정 저장

`core:domain/driving`의 `DrivingAnalyzer`가 순수 계산을 맡고, `core:data`의 `DefaultDrivingRecorder`가 차량 구독·암호화 체크포인트·기존 `ProgressRepository` 호출을 연결한다. feature는 `DrivingRecorder`와 `HistoryRepository`만 사용한다. Android foreground service는 app 모듈에 있다.

- 기록 저장은 기본 OFF다. 주차 중 켠 뒤 다음 출발부터 수집한다. 끄면 미완료 여정을 폐기하고 기존 기록·카드는 유지한다. 폐기 도중 종료되거나 삭제가 실패해도, 다시 켜기 전에 이전 체크포인트를 제거한다. 데모 DB에 실제 차량 기록을 저장하지 않는다.
- `PERF_VEHICLE_SPEED`의 단조 증가 timestamp를 사용한다. UI 재전달, 동일 timestamp, 역순 이벤트는 적분/집계하지 않는다. 속도는 5Hz를 요청하고 API 35 이상에서도 가변 업데이트를 끈다. 실제 지원 주기는 차량에 따라 다르다. [CarPropertyValue timestamp](https://developer.android.com/reference/android/car/hardware/CarPropertyValue#getTimestamp())
- 유효한 연속 샘플만 사다리꼴 적분해 **관찰 거리**를 계산한다. 2초 초과 간격은 보간하지 않는다. 비정상 속도·12m/s² 초과 변화는 제외한다. 전진 시 이동 구간만 가속/감속 평가에 쓴다.
- 가속도에 시정수 0.4초의 지수 평활을 적용한다. +2.5m/s² 이상 가속 또는 −3.0m/s² 이하 감속이 0.6초 이상 이어질 때 1회로 센다. 기준의 절반 아래에서 0.8초 유지돼야 다시 셀 수 있다. 가속 후보 시작 속도 2.8m/s 미만은 급출발로 함께 센다.
- 버전 1 참고 점수는 `round(100 - 6 × 급가속 횟수 × 10 / max(5, 전진 이동 분))`, 0~100 범위다. **급감속은 도로 맥락을 알 수 없으므로 감점하지 않고 관찰 횟수만 표시한다.** 과속·급회전·사고 위험을 평가하지 않는다. 임계값과 산식은 실차 데이터 검증 전의 제품 정책이며 공식 안전성 평가가 아니다.
- 전진 이동 120초, 관찰 거리 1km, 유효 샘플 300개, 관찰 시간 비율 90% 이상이 필요하다. 10초 초과 신호 공백, 과도한 이상값, 출발 이후 수집 시작, 프로세스/서비스 중단 기록은 `Insufficient`로 남긴다. 이때 점수는 null이며 카드를 지급하지 않는다.
- `JourneyRepository`가 홈·추천·측정의 여정 원본이다. 주행 감지 시 시작하고 정지 후 P 기어에서 즉시 종료한다. D단 0km/h와 화면 전환은 종료가 아니다. 홈으로 돌아가도 마지막 종료 세션은 저장/방문 처리에 남기며, 새 출발에는 새 ID를 발급한다. UI와 DB가 같은 ID·시작/종료 시각을 사용한다.
- foreground service는 앱이 보이는 동안 저장 설정이 켜져 있을 때 시작한다. 내비 등 다른 앱으로 전환해도 기록을 유지한다. OS 강제 종료/재부팅 후 연속 측정을 가정하지 않는다. 5초마다 저장한 요약으로 중단 기록을 복구하고 점수·지급을 보류한다. `specialUse` 유형을 사용하고, 정확한 위치 권한이 있을 때 `location` 유형을 함께 활성화한다. [Foreground service 유형](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- 완료 결과를 먼저 체크포인트에 고정하고 안정된 `driving:<journeyId>` operation ID로 저장한다. 저장 성공 후 체크포인트를 지우므로 그 사이 종료돼도 카드가 중복 적립되지 않는다. 실패하면 동일 입력으로 재시도한다. 데이터 삭제/초기화의 이전 generation을 새 값으로 바꾸지 않는다.

기록 탭은 정상 리포트, 데이터 상태, 기록 부족, 빈 목록, 로드/저장 오류, 저장 설정을 제공한다. 모든 상세 조작은 공통 차량 제한 정책을 따르며, 주행 중 점수를 표시하지 않는다.

홈 추천·지도 검색·지도 핀·주행 중 근거리 추천·경로 중복 판정은 모두 `PlaceRepository`의 활성 장소 Flow를 읽는다. 장소 추가/수정/비활성화가 반영되며 지도 사진은 `imageKey`로 찾는다. `DefaultBadgeRegionSource`는 실제 소유 카드가 있는 장소의 `regionId`를 반환한다. 지도 경계 자산의 ID와 일치해야 하며 부산은 `KR-26`이다. 컬렉션 안내의 부산 일러스트는 별도의 장식용 화면이다.

홈의 대표 카드는 `observeRepresentativeCard()`에 연결한다. 여정 종료 화면은 현재 세션과 같은 ID의 저장 결과가 있을 때만 운전습관 상세 링크를 표시한다. 저장 중/꺼짐/실패 상태는 별도로 안내한다. 주행 중 관찰 배너는 측정기의 급가속/급감속 이벤트만 표시하며, 정해진 시간에 가상의 관찰을 만들어 내지 않는다.

## 자동 방문 인정

`DrivingRecordingService`가 저장 동의 동안 `DrivingRecorder`와 `VisitRecorder`를 함께 실행한다. 지도·내비 화면을 벗어나도 관찰이 계속되며, 데모에서는 실제 측정과 방문 지급을 중단한다.

- 내비 도착 콜백은 주행 화면의 도착 안내에만 쓴다. 방문 지급 근거로 삼지 않는다.
- Pleos FusedLocation은 지도 위치 표시에 사용한다. 방문 근거는 수평 오차(m)가 포함된 Android GPS/Network 위치를 `VisitLocationSource`로 받는다. FusedLocation의 `confidence` 값을 임의로 미터로 변환하지 않는다. 해당 기기에서 정확도 있는 위치가 제공되지 않거나 위치 권한이 없으면 자동 방문을 인정하지 않는다.
- 완료 여정이 DB에 저장되고 P·정지가 확인된 상태에서 연속 체류를 관찰한다. 각 위치의 거리+오차가 장소 반경 안에 있어야 하며, 15초 초과 오래된 위치·누락·반경 이탈은 체류를 초기화한다. 원본 위치 궤적은 저장하지 않는다.
- `VisitTracker`는 `visit:<journeyId>:<placeId>`로 같은 여정/장소를 한 번만 확정한다. 이후 `ProgressRepository`가 방문·카드·기억·알림을 함께 반영한다. 안전 점수가 부족한 여정도 장소 방문 조건은 독립적으로 충족할 수 있다.
- 저장 실패는 같은 입력으로 다시 시도한다. 동의 해제·데모 전환·프로세스 종료는 미확정 체류를 버린다. 방문 저장 전 프로세스가 끝나면 새 연속 체류가 필요하며, 이미 저장한 방문은 중복 적립하지 않는다. 초기화/삭제 이후 예전 작업에 새 generation을 부여하지 않는다.


## 삭제·초기화·데모

`deleteHistory(DeleteHistoryRequest(...))`는 남은 근거로 레벨을 재계산한다. 이 작업으로 새 카드를 지급하거나 레벨을 올리지 않는다. 여정 삭제는 연결 방문도 삭제한다. 관련 기억·상위 grant·무효 알림을 정리하고, 공개 레벨을 낮아진 earned 이하로 맞춘다. 근거가 0이면 소유와 대표 참조를 제거한다. 기존 grant는 당시 규칙 버전으로 검증한다.

삭제된 원본의 operation에는 최소 식별자/무효화 상태만 남기고 fingerprint와 receipt를 지운다. 삭제로 낡아진 다른 receipt 상세도 지운다. 삭제된 원본의 늦은 재시도는 `DeletedSource`로 거절한다. 데이터 세대가 같아도 새 원본 ID로 전달하면 다른 행동으로 간주하므로 수집 단계의 안정적인 ID 관리가 필요하다.

`clearUserData()`는 선택한 저장소의 원본/소유/공개/알림/대표/요청을 초기화하고 generation을 교체한다. 카탈로그는 유지한다. 초기화 전 시작한 작업은 **그때의 generation을 그대로 보내야** `StaleGeneration`으로 차단된다. 재시도할 때 현재 generation을 새로 받아 덮어쓰면 안 된다.

debug의 데모는 별도 DB에 8장의 미개봉/공개/1→2·2→3·3→4·4→5 대기를 구성한다. 미개봉 목록에서 데모를 재설정할 수 있다. 실제 저장소는 영향을 받지 않는다. Release에서는 데모 활성화가 불가능하다. Android 사용자별 앱 저장소를 사용하며 앱 내부 계정 전환 기능은 아직 없다.

## 검증

- domain: 방문 체류/오차, 적용 시점, 원본 중복, 분석 상태/필수 값, 최고 레벨 판정.
- data instrumentation: 빈 DB, 획득/알림/receipt, 동시 중복, 요청 충돌, 미리보기, 순차 공개 중 추가 성장, DB 재열기, 삭제/초기화의 오래된 토큰, 트랜잭션 롤백, 카탈로그 갱신, 다중 지급, 분석 완료, DB 제약, 암호화 재열기, 데모 분리.
- feature/app instrumentation: 카드 프레임·연출 연속성·선반·상태별 내비게이션, 공개 저장 성공/실패·추가 성장·삭제·차량 제한에 대한 ViewModel 검증.
- driving: 시간 간격별 적분·노이즈·중복 샘플·급가속/감속·데이터 부족 판정, 중단 복구·중복 보상 방지·저장 해제·D 정차/P 종료, 화면 크기 변경·리포트 복원·기록 서비스와 실제 SDK timestamp 갱신.

```shell
./gradlew :core:domain:test :app:assembleDebug :app:assembleRelease
./gradlew :core:data:connectedDebugAndroidTest
./gradlew :core:database:connectedDebugAndroidTest
./gradlew :feature:records:connectedDebugAndroidTest
./gradlew :feature:badges:connectedDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache \
  -Pandroid.testInstrumentationRunnerArguments.class=io.issac.pleosandroid.navigation.MainActivityFlowTest
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache \
  -Pandroid.testInstrumentationRunnerArguments.class=io.issac.pleosandroid.navigation.DrivingHabitFlowTest
./gradlew spotlessCheck
```

연결 테스트는 Pleos 에뮬레이터에서 모듈별로 순서대로 실행한다. app의 여정 예시는 debug 데모 저장소에만 추가한다. 기록 서비스 테스트는 주차 상태에서 설정을 전환하고 원래 설정으로 복구한다.
