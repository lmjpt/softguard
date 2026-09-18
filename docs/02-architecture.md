# 설계문서 2/5 — 아키텍처 · PIN · 정책 모델

> 목차와 구현 기록(§15)은 [../agent.md](../agent.md). 이 파일은 원문 §5~§7 그대로이며, v3.0 에서 바뀐 곳만 표시했다.

## 5. 아키텍처

### 5.1 구성

```
┌──────────── 우리집 소프트가드 (공기계) ────────────┐
│                                                    │
│  [UI]                                              │
│   SetupWizard    : 최초 1회 (PIN + 권한 + 앱 + 시간)│
│   PinLockScreen  : 6자리 숫자 키패드                 │
│   Dashboard      : 동작 상태 / 오늘 요약             │
│   BlockListEditor: 차단 앱 선정                      │
│   ScheduleEditor : 공통 허용 시간대 편집              │
│   HistoryScreen  : 날짜별 이력 조회                   │
│   BlockOverlay   : 차단 안내 (오버레이 뷰)            │
│                                                    │
│  [도메인]                                           │
│   PolicyEngine   : evaluate(pkg, now)               │
│   PolicyStore    : DataStore (정책 JSON)            │
│   EventLogger    : 버퍼링 + 배치 기록                │
│   EventLogDao    : Room DAO                         │
│   GapDetector    : 제어 중단 구간 추론 (§8.7)         │
│   PinStore       : EncryptedSharedPreferences       │
│   AppCatalog     : 설치 앱 목록 / 기록 대상 판별      │
│                                                    │
│  [집행]                                             │
│   interface Enforcer                               │
│     └ AccessibilityEnforcer                        │
│                                                    │
│  [진입점]                                           │
│   SoftGuardAccessibilityService  (상시 바인드)       │
│   BootReceiver          : BOOT_COMPLETED            │
│   PackageChangeReceiver : PACKAGE_REMOVED (목록 정리)│
│   WindowEndAlarmReceiver: 허용 시간대 종료            │
│   LogPruneWorker        : 일 1회 180일 초과 삭제      │
│                                                    │
└────────────────────────────────────────────────────┘
```

### 5.2 기술 스택

| 영역 | 선택 | 근거 |
| --- | --- | --- |
| 언어 | Kotlin |  |
| UI | Jetpack Compose |  |
| 최소 SDK | **API 33 (Android 13)** | 버전 분기 제거 |
| 타겟 SDK | 최신 (API 35+) |  |
| 정책 저장 | DataStore (Preferences + `kotlinx.serialization`) | 구조 단순 |
| 이력 저장 | **Room** | 9만 건 규모 조회·정렬·기간 삭제 |
| 이력 조회 | **날짜 단위 일괄 조회** (Paging 불필요) | 하루치 200~500건 → 한 번에 읽어도 충분 |
| PIN 저장 | EncryptedSharedPreferences (Jetpack Security) |  |
| 스케줄 | AlarmManager `setExactAndAllowWhileIdle` | 시간대 종료 |
| 주기 작업 | WorkManager (일 1회) | 오래된 이력 정리 |
| 서버 | 없음 |  |

> **Paging 3를 뺀 이유**: 날짜별 필터링이 기본 조회 방식이므로 한 번에 읽는 양이 하루치로 제한된다. 전체 9만 건을 스크롤할 일이 없어 페이징 라이브러리가 불필요하다.

### 5.3 전력·메모리 (R3)

이력을 모든 앱에 대해 남기므로 접근성 이벤트는 전체 수신한다. 정직한 비용 계산:

| 항목 | 값 |
| --- | --- |
| 콜백 발생 빈도 | 사용자가 앱을 전환할 때만 = **하루 200~500회** (사람이 조작하는 속도) |
| 콜백 1회 처리 비용 | 해시셋 조회 + 시각 구간 비교 + 메모리 버퍼 append → 마이크로초 단위 |
| 디스크 쓰기 | 배치로 묶어 **하루 수십 회** |
| 180일 누적 용량 | 약 4만~9만 건 × ~100B ≈ **9~15MB** (인덱스 포함) |
| 결론 | 폴링·포그라운드 서비스·웨이크락이 여전히 없으므로 **배터리 통계에 유의미하게 잡히지 않을 것으로 예상.** M6에서 실측 |

**절감 전략**

| 전략 | 내용 |
| --- | --- |
| 이벤트 타입 한정 | `TYPE_WINDOW_STATE_CHANGED` 만 구독. `TYPE_VIEW_*`, `flagRetrieveInteractiveWindows` 미사용 |
| `notificationTimeout` | 100~200ms 로 이벤트 병합 |
| 기록 대상 축소 | 런처에서 실행 가능한 앱만 기록 (§8.2). 시스템UI·입력기 등은 애초에 기록하지 않음 |
| **중복 억제 10초** | 같은 패키지가 10초 내 재등장하면 무시 |
| **배치 쓰기** | 메모리 버퍼에 모아 10건 또는 15초마다 flush. 화면 꺼짐·서비스 종료 시 강제 flush |
| 알람 최대 1개 | 차단 앱이 허용 시간 중 전면에 있을 때만 종료 알람 1개 |
| 폴링 금지 | 주기적 전면 앱 확인 루프 없음 |
| 포그라운드 서비스 미사용 | 접근성 서비스는 시스템이 살려두므로 상주 서비스·상시 알림 불필요 |
| 오버레이 지연 생성 | 첫 차단 때 생성하고 이후 재사용 (`View.GONE`) |
| 정책 메모리 캐시 | 판정 형태로 미리 계산, 변경 시에만 재계산 |
| 이력 정리 | WorkManager 일 1회, 180일 초과 행 일괄 삭제 |

> **버퍼 손실 감수**: 접근성 서비스가 강제 종료되면 버퍼의 최대 15초 분량이 유실될 수 있다. 감사 로그가 아니라 관리 참고용이므로 수용한다. 단 **손실 구간은 §8.7의 공백 추론으로 드러난다.**

### 5.4 삼성 One UI 대응 (중요)

| 항목 | 경로 | 이유 |
| --- | --- | --- |
| **제한된 설정 허용** | 설정 → 앱 → 우리집 소프트가드 → ⋮ → 제한된 설정 허용 | **사이드로드 APK의 접근성 토글이 회색으로 잠겨 있다.** minSdk 33 이므로 **항상 필요** |
| 배터리 사용량 제한 없음 | 설정 → 앱 → 우리집 소프트가드 → 배터리 → 제한 없음 | 백그라운드 종료 방지 |
| 미사용 앱 절전 모드 제외 | 설정 → 배터리 → 백그라운드 사용 제한 → 절전 모드로 전환하지 않을 앱 | 오래 안 열면 잠들어 서비스가 죽는 것 방지 |
| 자동 최적화 재시작 확인 | 설정 → 디바이스 케어 → ⋮ → 자동 최적화 | 매일 자동 재부팅 시 서비스 복구 확인 |
| 접근성 단축키 | 설정 → 접근성 → 고급 설정 → 볼륨 버튼 단축키 | 의도치 않은 토글 방지 |

> **M1 기술 검증에서 실기기로 이 경로들을 먼저 확인할 것.** One UI 버전에 따라 메뉴 이름이 달라진다.

### 5.5 부팅 자동 실행 (R2)

- 접근성 서비스는 **한 번 켜두면 부팅 후 시스템이 자동으로 다시 바인드**한다 → R2 충족
- `BootReceiver` 작업: `DEVICE_BOOT` 이력 기록 → 정책 로드 → 필요 시 알람 재등록 → 이력 정리 작업 예약 확인
- **부팅 시 UI를 띄우지 않는다** — 매번 PIN 화면이 뜨면 사용성이 나쁘고, 백그라운드 액티비티 실행이 제한된다
- 접근성이 꺼져 있으면 제어와 기록이 모두 중단되므로 **자체 상태 점검**이 필요 (§10.1)

### 5.6 판정 + 기록 흐름 (v3.0 구현 순서)

> **순서가 중요하다.** 집행(판정·오버레이)은 매 이벤트마다, 기록만 중복 억제를 거친다.
> v2.0 개념 코드는 중복 억제와 `isLoggable` 검사가 집행보다 앞에 있어서
> ① 차단 앱 → 다른 앱 → 10초 안에 차단 앱으로 돌아오면 오버레이가 뜨지 않고,
> ② 홈으로 나가면 런처 이벤트에서 조기 반환되어 오버레이가 홈 화면 위에 남았다. (§15)

```kotlin
override fun onServiceConnected() {
    gapDetector.onServiceStart()      // §8.7 — 공백 추론 + GUARD_STARTED 기록
    // 정책 로드, 오버레이 준비 등
}

override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (event.eventType != TYPE_WINDOW_STATE_CHANGED) return
    val pkg = event.packageName?.toString() ?: return
    touchAliveThrottled(now)                              // §8.7 — 1분에 한 번 lastAliveAt 갱신

    if (pkg == packageName) {                             // 자기 자신
        if (event.className 이 우리 액티비티) { overlay.hide(); alarms.cancel() }
        return                                            // 우리가 띄운 오버레이 창의 이벤트는 무시
    }
    if (appCatalog.isTransient(pkg)) return               // 알림 창·입력기·권한 대화상자: 상태 유지

    // 1) 집행 — 매 이벤트. 중복 억제를 거치지 않는다
    lastForeground = pkg
    val verdict = policyEngine.evaluate(pkg, now)
    when (verdict) {
        is Allowed -> { overlay.hide(); verdict.until?.let { alarms.schedule(it) } ?: alarms.cancel() }
        is Blocked -> { overlay.show(...);  verdict.nextAvailableAt?.let { alarms.schedule(it) } ?: alarms.cancel() }
    }
    //    홈 화면(런처)도 여기까지 온다 → Allowed → 오버레이가 내려간다

    // 2) 기록 — 실행 가능 앱만, 10초 중복 억제
    if (!appCatalog.isLoggable(pkg)) return               // 런처 복귀·시스템 화면은 기록하지 않음
    if (!dedup.shouldRecord(pkg, now)) return
    eventLogger.recordLaunch(pkg, appCatalog.labelOf(pkg), now, verdict is Blocked, (verdict as? Blocked)?.reason)
}

override fun onUnbind(intent: Intent?): Boolean {
    eventLogger.recordGuardStopped(now, inferred = false) // 정상 종료 (동기 flush)
    eventLogger.flushNow()
    return super.onUnbind(intent)
}
```

---

## 6. PIN 인증 (R4)

PIN 하나가 앱 진입·정책 변경·이력 조회를 모두 관장한다.

| 항목 | 설계 |
| --- | --- |
| 형식 | 숫자 6자리 |
| 저장 | **평문 금지.** PBKDF2-HMAC-SHA256(반복 10만회) + 기기별 랜덤 salt → 해시만 SharedPreferences. *(v3.0: 이미 salt 를 섞은 해시라 암호화 저장소가 필요 없고, `EncryptedSharedPreferences` 는 폐기된 라이브러리다)* |
| 검증 | 상수 시간 비교 |
| 실패 시 | 진입 차단. 5회 실패 → 30초 / 10회 → 5분 (간단한 백오프) |
| 화면 보호 | `FLAG_SECURE` (스크린샷·최근앱 미리보기 차단) — **이력 화면에도 적용** |
| 입력 UX | 자체 숫자 키패드(시스템 키보드 미사용), 6자리 입력 시 자동 검증 |
| 변경 | 기존 PIN 확인 후 변경 |
| **분실 시** | 서버가 없으므로 복구 불가 → **앱 삭제 후 재설치**(정책·이력 초기화) |

---

## 7. 정책 모델

### 7.1 블랙리스트 (확정)

```
기본: 전부 허용
  + 차단 목록의 앱만 제한
  + 차단 목록의 앱은 공통 허용 시간대에만 열림
```

**보호 목록이 필요 없다.** 런처·전화·시스템UI·입력기·설정은 관리자가 차단 목록에 넣지 않는 한 항상 동작한다.

#### 안전장치

| 항목 | 처리 |
| --- | --- |
| **자기 자신 · 기본 홈 앱 · 시스템UI** | 차단 목록 UI 에서 **아예 제외**한다 (구현 완료) |
| 그 외 시스템 앱 | 목록에 `(시스템)` 으로 표시하고, 켤 때 **확인 대화상자**를 띄운다 (구현 완료) |
| 삭제된 앱 | `PACKAGE_REMOVED` 수신 시 차단 목록에서 자동 정리 (**이력은 보존**) — 미구현 |

> **홈 앱을 경고만 띄우고 허용하지 않는 이유**: 기본 홈 앱이 차단되면 차단 화면에서 홈으로
> 나가도 다시 홈이 차단되어 되돌이에 빠진다. 설정에 들어가 접근성을 끌 수조차 없어 기기를
> 되살릴 수 없다. **되돌릴 수 없는 선택은 경고로 막을 것이 아니라 못 하게 해야 한다.**
> 오버레이 폴백(홈 호출)에도 최소 간격 2초를 두어 같은 종류의 되돌이를 이중으로 막는다.

#### 차단 목록 선정 가이드 (§2.3 대응)

| 추천 항목 | 이유 |
| --- | --- |
| **Play 스토어** (`com.android.vending`) | 새 앱을 설치해 우회하는 경로를 막음 (새 앱은 자동 허용되므로) |
| **갤럭시 스토어** | 위와 동일 |
| **브라우저** (삼성 인터넷, Chrome) | 유튜브 앱을 막아도 브라우저로 접속 가능 |

### 7.2 공통 허용 시간대 (확정)

```jsonc
{
  "version": 3,
  "defaultPolicy": "ALLOW_ALL",
  "blockedPackages": [
    "com.google.android.youtube",
    "com.android.vending",
    "com.sec.android.app.sbrowser"
  ],
  "allowWindows": [
    { "days": ["MON","TUE","WED","THU","FRI"], "from": "19:00", "to": "20:00" },
    { "days": ["SAT","SUN"],                   "from": "10:00", "to": "12:00" },
    { "days": ["SAT","SUN"],                   "from": "19:00", "to": "21:00" }
  ]
}
```

- `allowWindows` 가 **비어 있으면** 차단 목록의 앱은 **항상 차단**된다
- 설정 화면은 두 개뿐: **"차단할 앱 고르기"** + **"열어줄 시간 정하기"**

#### 저장 포맷 규칙 (`PolicyCodec` — 구현 완료, §14.6)

| 항목 | 규칙 |
| --- | --- |
| `version` | `3` 만 허용. 다른 값은 거부한다 (추측해서 읽지 않는다) |
| `defaultPolicy` | `"ALLOW_ALL"` 이어야 한다. 화이트리스트 시절(v1·v2)의 `BLOCK_ALL` 파일을 블랙리스트로 오해해 **차단이 전부 풀리는 것**을 막는 표식이다 |
| 요일 | `MON`~`SUN` 세 글자 코드. 읽을 때는 대소문자·앞뒤 공백을 허용하고, 쓸 때는 요일 순서로 정렬한다 |
| 모르는 키 | **거부한다.** 오타 난 설정이 조용히 무시되는 것보다 알려주는 쪽이 낫다 |
| 출력 결정성 | 같은 정책이면 항상 같은 문자열. 집합(차단 목록·요일)을 정렬해 출력하므로 바뀌지 않았는데 저장이 일어나는 일이 없다 |
| **읽기 실패 시** | 예외를 던지지 않고 실패 값을 돌려준다. 폴백은 **아무것도 차단하지 않는 정책**이다 — 전부 차단하는 쪽으로 넘어지면 설정 앱조차 못 열어 기기를 되살릴 수 없다. **실패 방향은 기기를 계속 쓸 수 있는 쪽이어야 한다** |

### 7.3 판정 규칙

```
evaluate(pkg, now):
   1. pkg == 자기 자신              → Allowed(until = null)
   2. pkg ∉ blockedPackages         → Allowed(until = null)        // 대부분 여기서 종료
   3. allowWindows 가 비어 있음      → Blocked(ALWAYS_BLOCKED)
   4. now ∈ allowWindows            → Allowed(until = 구간 종료시각)
   5. 그 외                         → Blocked(OUT_OF_HOURS, next = 다음 구간 시작시각)
```

| 이유 | 오버레이 문구 |
| --- | --- |
| `ALWAYS_BLOCKED` | "이 앱은 사용할 수 없어요" |
| `OUT_OF_HOURS` | "지금은 사용 시간이 아니에요 · 오늘 19:00부터 사용 가능" |

**경계 처리 (단위 테스트 대상)**
- 자정 넘김 구간 (22:00~01:00) → 두 구간으로 분해
- 구간 겹침 → 합집합으로 정규화
- `to = "24:00"` 을 자정으로 허용
- 시작 == 종료 → 무효 구간 (저장 시 검증 거부)
- `allowWindows = []` → 항상 차단 (의도된 동작이므로 저장 시 안내만)
- 기기 시각 변경 → 막지 않음 (§2.3). 판정은 항상 현재 시각 기준

### 7.4 허용 시간대 종료 처리

```
차단 앱이 전면에 옴 (허용 시간 중) → Allowed(until = 20:00)
    ↓ 알람 1개 등록
사용자가 다른 앱으로 전환 → 알람 취소
    ↓
20:00 도달 → WindowEndAlarmReceiver
    ↓
마지막 전면 패키지를 재판정 → 여전히 차단 대상이면 오버레이 표시 + 이력 기록
```

- **허용 시작 알람 (v3.0)**: 차단 앱이 오버레이에 덮여 있는 동안은 다음 허용 **시작** 시각에도 알람을 둔다. 정각에 재판정해 오버레이를 내리므로, 아이가 "19:00부터" 안내를 보며 기다리다 정각에 바로 쓸 수 있다. 알람은 여전히 한 번에 1개 (종료 또는 시작 중 현재 전면 앱에 해당하는 것)
- 현재 전면 앱은 접근성 서비스가 기억하는 "마지막 전면 패키지" 를 사용 (별도 권한 불필요)
- `SCHEDULE_EXACT_ALARM` 권한을 초기 설정에서 요청. 못 받으면 부정확 알람으로 폴백(최대 수 분 오차)하고 UI에 명시

---

