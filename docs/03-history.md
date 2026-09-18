# 설계문서 3/5 — 앱 실행 이력

> 목차와 구현 기록(§15)은 [../agent.md](../agent.md). 이 파일은 원문 §8 그대로이며, v3.0 에서 바뀐 곳만 표시했다.

## 8. 앱 실행 이력 (R8, R9, R10)

### 8.1 요구사항

- 기록 항목: **실행 앱 명 / 실행 시간 / 차단 여부**
- 범위: **모든 앱**, 보관: **180일**, 조회: **관리자(PIN 통과)만**
- **날짜별 필터링** 조회 (R9)
- **제어가 꺼져 있던 구간 표시** (R10)

### 8.2 기록 대상 판별

모든 접근성 이벤트를 그대로 기록하면 시스템UI·입력기·런처 전환까지 섞여 이력이 노이즈로 가득 찬다. **"런처에서 실행할 수 있는 앱"만** 기록한다.

```
isLoggable(pkg):
   pkg != 자기 자신
   && PackageManager.getLaunchIntentForPackage(pkg) != null   // 실행 가능한 앱
   && pkg != 현재 기본 런처                                    // 홈 화면 복귀는 기록 안 함
```

- 판별 결과는 캐시하고, `PACKAGE_ADDED/REMOVED` 시에만 갱신
- 접근성 이벤트 필터(`packageNames`)는 **비워 전체 수신**하고, 위 조건으로 걸러낸다

### 8.3 중복 억제 (10초)

앱을 전환할 때 중간 윈도우 이벤트가 여러 번 발생해 같은 앱이 중복 기록된다. **같은 패키지가 10초 내 재등장하면 무시**한다.

- 상태: `lastPkg`, `lastAt` 두 변수만 메모리에 유지
- 다른 앱으로 갔다가 10초 안에 돌아오면 두 번째 진입은 기록되지 않는다 → 짧은 왕복은 1건으로 합쳐지는 셈. 이력을 "사용 구간"이 아니라 "실행 시점" 기록으로 보므로 수용한다
- **허용·차단을 구분하지 않고 동일하게 10초** 적용 (확정). 같은 차단 앱을 10초 안에 반복 시도한 정황은 기록되지 않는다. 대신 오버레이가 떠 있는 동안 발생하는 중복 이벤트가 이력을 오염시키지 않는다는 이점이 있다

### 8.4 스키마 (Room)

이력에 앱 실행 외 시스템 이벤트가 섞이므로 `type` 으로 구분한다.

```kotlin
enum class LogType {
    APP_LAUNCH,      // 앱 실행 (허용/차단 모두)
    GUARD_STARTED,   // 접근성 서비스 시작
    GUARD_STOPPED,   // 접근성 서비스 종료
    DEVICE_BOOT      // 기기 부팅
}

@Entity(
    tableName = "event_log",
    indices = [Index("dayKey"), Index("occurredAt"), Index("packageName")]
)
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,             // LogType
    val occurredAt: Long,         // epoch millis
    val dayKey: Int,              // yyyyMMdd (로컬 시각 기준, 기록 시점 계산)
    val packageName: String?,     // APP_LAUNCH 일 때만
    val appLabel: String?,        // 기록 시점 이름 그대로 저장
    val blocked: Boolean = false, // APP_LAUNCH 일 때만 의미 있음
    val blockReason: String?,     // "ALWAYS_BLOCKED" | "OUT_OF_HOURS" | null
    val inferred: Boolean = false // GUARD_STOPPED 를 추론으로 만들었는지 (§8.7)
)
```

**설계 근거**

| 결정 | 이유 |
| --- | --- |
| `dayKey` 를 별도 컬럼으로 | 날짜별 조회(R9)가 기본이므로 `WHERE dayKey = 20260917` 한 줄로 끝난다. SQLite 날짜 함수·타임존 변환 불필요. 인덱스도 잘 걸린다 |
| `dayKey` 를 **기록 시점에 계산** | 나중에 타임존이 바뀌어도 과거 기록의 소속 날짜가 흔들리지 않는다 |
| `appLabel` 비정규화 저장 | 앱이 삭제되면 `PackageManager` 로 이름을 되찾을 수 없다. 6개월 전 기록이 "알 수 없는 앱"이 되지 않게 |
| `inferred` 플래그 | 정상 종료와 추론된 종료를 UI에서 구분 표시 |

**주요 쿼리**

```sql
-- 특정 날짜 전체 (R9)
SELECT * FROM event_log WHERE dayKey = :dayKey ORDER BY occurredAt DESC;

-- 특정 날짜, 차단된 것만
SELECT * FROM event_log
 WHERE dayKey = :dayKey AND type = 'APP_LAUNCH' AND blocked = 1
 ORDER BY occurredAt DESC;

-- 기록이 있는 날짜 목록 (날짜 선택기의 점 표시용)
SELECT DISTINCT dayKey FROM event_log ORDER BY dayKey DESC;

-- 날짜별 요약
SELECT dayKey, COUNT(*) AS total, SUM(blocked) AS blockedCount
  FROM event_log WHERE type = 'APP_LAUNCH' GROUP BY dayKey;
```

### 8.5 기록 파이프라인

```
접근성 이벤트
    ↓  isLoggable 필터
    ↓  10초 중복 억제
EventLogger 메모리 버퍼 (ArrayDeque, 상한 200건)
    ↓  10건 또는 15초마다 / 화면 꺼짐 / 서비스 종료 시
Room 일괄 insert
    ↓  flush 성공 시
DataStore.lastAliveAt = 마지막 flush 시각      ← §8.7 공백 추론의 기준점
```

- IO는 `Dispatchers.IO` 코루틴에서 처리, 접근성 콜백을 블로킹하지 않는다
- `GUARD_STOPPED` 는 버퍼링하지 않고 **즉시 동기 기록** (종료 중이라 다음 기회가 없다)

### 8.6 조회 화면 — 날짜별 (R9)

| 요소 | 내용 |
| --- | --- |
| **날짜 선택** | 상단에 날짜 표시 + 좌우 화살표(전일/다음일). 탭하면 캘린더 피커. **기록이 있는 날짜에 점 표시** |
| 기본값 | 오늘 |
| 범위 제한 | 180일 이전은 선택 불가 (데이터 없음 안내) |
| 상단 요약 | "실행 42건 · 차단 7건" (선택한 날짜 기준) |
| 목록 | 선택한 날짜의 이벤트, **최신순**. 한 번에 전체 로드 (하루치 수백 건) |
| 행 표시 | 앱 아이콘 + 앱 이름 + `HH:mm` + 차단 배지 |
| 차단 배지 | 색으로 구분 + 이유 표시 ("시간 외" / "항상 차단") |
| 필터 칩 | 전체 / 차단된 것만 / 앱 선택 |
| 빈 상태 | "이 날은 기록이 없어요" |
| 보호 | `FLAG_SECURE` 적용 |

### 8.7 제어 중단 구간 기록 (R10)

접근성 서비스가 꺼져 있던 시간을 이력에서 구분할 수 있게 한다. "막지는 못해도 알 수는 있게" 만드는 장치다.

#### 기록 시점

| 이벤트 | 기록 |
| --- | --- |
| `onServiceConnected()` | `GUARD_STARTED` |
| `onUnbind()` / `onDestroy()` | `GUARD_STOPPED (inferred = false)` — 즉시 동기 기록 |
| 부팅 | `DEVICE_BOOT` (BootReceiver) |

#### 비정상 종료 추론 — `lastAliveAt` 방식

강제 종료·크래시·전원 차단 시에는 `onUnbind()` 가 호출되지 않아 `GUARD_STOPPED` 가 남지 않는다. 이를 메우기 위해:

```
매 배치 flush · 화면 켬/꺼짐 · 접근성 이벤트 수신(1분에 한 번)  → lastAliveAt = now   (SharedPreferences 한 줄)

서비스 시작 시 (onServiceConnected):
   마지막 이벤트가 GUARD_STOPPED 가 아니라면
       → lastAliveAt 시각에 GUARD_STOPPED(inferred = true) 를 삽입
   그 다음 GUARD_STARTED 기록
```

- `lastAliveAt` 은 배치 flush 외에 **화면 켬/꺼짐 브로드캐스트와 접근성 이벤트 수신(1분에 한 번)** 에서도 갱신한다 (v3.0). 여전히 별도 웨이크업이나 하트비트는 없다
- 추론된 종료 시각은 "마지막으로 무언가 일어난 시각" 이다. 사용 중이면 오차가 1분 안이지만, **아무 이벤트도 없는 유휴 구간(밤새 방치)에 서비스가 죽으면 추정 종료가 마지막 활동 시각까지 당겨진다.** v2.0 의 "최대 15초" 는 flush 가 이벤트가 있을 때만 일어난다는 점을 빠뜨린 것이었다. 화면 꺼짐이 기준점이 되므로 실제로는 "화면을 끈 뒤" 로 표시되며, 공백 표시 목적에는 충분

#### 화면 표시

날짜별 목록에서 `GUARD_STOPPED` → 다음 `GUARD_STARTED` 를 짝지어 **공백 블록**으로 렌더링한다.

```
19:42   YouTube              [시간 외 차단]
─────────────────────────────────────────
  ⚠  기록 공백 · 4시간 33분
     14:02 ~ 18:35  제어가 꺼져 있었습니다
─────────────────────────────────────────
13:58   카카오톡
```

| 상황 | 표시 |
| --- | --- |
| 정상 종료 후 재시작 | "제어가 꺼져 있었습니다" |
| 추론된 종료 (`inferred`) | "제어가 중단되었습니다 (종료 시각 추정)" |
| `DEVICE_BOOT` 이 사이에 있음 | "기기가 꺼져 있었습니다" — 사용자가 끈 것과 전원을 내린 것을 구분 |
| 공백이 날짜를 넘김 | 각 날짜에 걸친 부분만 표시 |

- 대시보드에도 "최근 7일 제어 중단 N회" 를 표시해 관리자가 바로 알아챌 수 있게 한다

> `DEVICE_BOOT` 를 넣은 이유: 이게 없으면 밤에 폰을 끈 것과 접근성을 끈 것이 똑같은 공백으로 보인다. BootReceiver가 이미 있으니 insert 한 줄이면 되고, 공백 표시의 신뢰도가 크게 올라간다.

### 8.8 보관 및 정리

| 항목 | 값 |
| --- | --- |
| 보관 기간 | **180일** |
| 정리 작업 | `LogPruneWorker` — WorkManager 주기 작업, 하루 1회 |
| 정리 쿼리 | `DELETE FROM event_log WHERE dayKey < :cutoffDayKey` |
| 예상 용량 | 하루 200~500건 × 180일 ≈ 3.6만~9만 건 ≈ **9~15MB** |
| 수동 삭제 | 설정에서 "이력 전체 삭제" 제공 |

### 8.9 이력 관련 원칙

- **로컬 전용.** 네트워크 권한 자체를 쓰지 않으므로 이력이 기기 밖으로 나갈 경로가 없다
- 기록하는 것은 **앱 이름·시각·차단 여부뿐**이다. 화면 내용, 입력 내용, URL, 메시지는 기록하지 않으며 접근성 설정에서도 해당 이벤트를 구독하지 않는다
- 내보내기(CSV)는 기기 밖으로 나갈 수 있으므로 현재 미포함 (§13)
- 공기계를 쓰는 가족 구성원에게 이력이 남는다는 점을 알려두는 편이 낫다 (설정 → 정보 화면에 기록 항목 명시)

---

