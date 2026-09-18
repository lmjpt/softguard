# 우리집 소프트가드

가족이 쓰는 공기계(갤럭시 S20 이상, Android 13+)에 설치해, 정해 둔 앱을 정해 둔 시간에만 열리게 하고
앱 실행 이력을 날짜별로 남기는 안드로이드 앱입니다. 설계와 결정 기록은 [agent.md](agent.md) 에 있습니다.

```
차단 목록에 넣은 앱  →  기본 차단, 공통 허용 시간대에만 열림
차단 목록에 없는 앱  →  항상 허용 (시스템 앱 포함)
모든 앱 실행 이력    →  이 기기 안에 180일, 날짜별 조회, 관리자(PIN)만
제어가 꺼졌던 구간    →  이력에 "기록 공백"으로 표시
```

## 받기 · 설치

APK 는 GitHub Actions 가 만들고 항상 같은 주소에 있습니다.

**https://github.com/lmjpt/softguard/releases/latest/download/softguard.apk**

공기계 브라우저로 위 주소를 열어 내려받고 실행합니다. "출처를 알 수 없는 앱 설치"를 한 번 허용해야 합니다.
설치 후 앱을 열면 마법사가 순서대로 안내합니다:

1. 관리자 PIN 6자리
2. 접근성 서비스 켜기 — **먼저 앱 정보 → ⋮ → "제한된 설정 허용"** (스토어 밖에서 설치한 앱은 이걸 안 하면 접근성 토글이 회색으로 잠겨 있습니다)
3. 배터리 최적화 제외 + 삼성 절전 설정
4. 정확한 알람 · 알림 (선택)
5. 차단할 앱 고르기 (스토어·브라우저 추천)
6. 열어 줄 시간대 정하기

[상태] 탭 맨 위가 **"제어 동작 중"** 이면 정상입니다.

## 구조

```
core/   순수 Kotlin/JVM — 정책 모델, 판정 엔진, 시간대 정규화, dayKey, 중복 억제, 저장 포맷, 이력 타임라인
        → 단위 테스트로 검증 (gradle :core:test)
app/    안드로이드 — 접근성 서비스, 차단 오버레이, Room 이력, Compose UI, 알람, 정리 워커
```

| 곳 | 하는 일 |
| --- | --- |
| `app/.../service/SoftGuardAccessibilityService.kt` | 전면 앱 이벤트 → 판정·오버레이(매 이벤트) → 기록(중복 억제) |
| `app/.../service/BlockOverlay.kt` | 차단 안내 화면. `TYPE_ACCESSIBILITY_OVERLAY` 라 별도 권한 불필요 |
| `app/.../data/PolicyRepository.kt` | `filesDir/policy.json`. 읽기 실패 → 아무것도 차단하지 않음 |
| `app/.../data/EventLogger.kt` | 버퍼 → 배치 insert, `lastAliveAt` 갱신, 비정상 종료 추정 |
| `app/.../ui/` | 마법사 · PIN · 상태 · 차단 앱 · 시간대 · 이력 · 설정 |

## 설계문서와 다른 점

구현하면서 설계문서(agent.md) 의 네 가지 문제를 고쳤고, 권한 하나를 없앴습니다. 자세한 근거는 agent.md §15.

- 중복 억제(10초)는 **기록에만** 걸고, 판정과 오버레이는 매 이벤트마다 수행한다
- 홈 화면·시스템 UI·입력기가 올라올 때 오버레이 상태를 올바르게 유지/해제한다
- `lastAliveAt` 은 flush 외에 화면 켬/꺼짐과 이벤트 수신(1분 간격)에서도 갱신한다
- 허용 시간 **시작** 정각에도 알람을 두어, 덮여 있던 앱이 바로 열린다
- 오버레이를 접근성 전용 창으로 띄워 '다른 앱 위에 표시' 권한이 필요 없다
- PIN 해시는 일반 SharedPreferences 에 둔다 (EncryptedSharedPreferences 는 폐기됨)
- R8 축소는 끈다 (기기에서 진단할 수 없고, APK 는 GitHub 에서 받으므로 크기 제한이 없음)

## 빌드

로컬에는 빌드 도구가 없어도 됩니다. `main` 에 푸시하면 `.github/workflows/android.yml` 이
`:core:test` → `:app:assembleRelease` → Release 갱신을 합니다. 실패 로그는 Actions 탭 또는
`gh run view --log-failed` 로 봅니다.

서명 키 원본은 PC 의 `android-signing.local/` (git 제외) 에 있고, GitHub secret
`ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` 로 전달됩니다.
**키를 잃으면 기존 설치본 위에 업데이트할 수 없습니다.** 백업해 두세요.

버전을 올릴 때는 `app/build.gradle.kts` 의 `versionCode` 와 `versionName` 을 함께 올립니다.
