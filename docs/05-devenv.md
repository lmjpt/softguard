# 설계문서 5/5 — 개발 환경 (기록)

> 목차와 구현 기록(§15)은 [../agent.md](../agent.md).
>
> **v3.0 에서 상황이 바뀌었다.** 이 장은 2026-09-17 에 쓰던 PC(Android Studio 설치, `C:\workspace\softguard`) 기준이다.
> 2026-09-18 의 작업 PC 에는 빌드 도구가 하나도 없어(Java 8 만 있음) **빌드를 GitHub Actions 로 옮겼다.**
> 그래서 §14.5 의 프록시·TEMP·`gw.cmd` 우회와 §14.9 의 APK 반출 문제는 **이 저장소에서는 해당 없다** —
> 소스만 GitHub 에 올라가고(텍스트 API 호출은 통과함), APK 는 GitHub 이 만들고, 폰은 GitHub 에서 받는다.
> §14.8 기기 테스트 절차와 §14.2~§14.4 의 "실기기 없이 검증되지 않는 것" 은 그대로 유효하다.

## 14. 개발 환경 준비

### 14.1 개발 PC 현황 (2026-09-17 실측)

**설치 완료 — 빌드에 필요한 것은 모두 갖춰짐**

| 항목 | 상태 |
| --- | --- |
| Android Studio | `C:\Program Files\Android\Android Studio` (build 261.26222.65) |
| SDK Platform | **`android-37.0`** (폴더명이 버전 번호 → 정식 릴리스) → `compileSdk` / `targetSdk` = 37 |
| Build-Tools | `36.0.0` |
| Platform-Tools | 설치됨 (`adb.exe` 포함) |
| 번들 JDK | **OpenJDK 25** (JetBrains Runtime) — 요구치 17 이상 충족 |
| SDK 총 용량 / C: 여유 | 1.5 GB / 106 GB |
| RAM | 16 GB |
| 기존 Zulu 8 | 그대로 유지. `JAVA_HOME` 미설정 상태 유지 (Studio 는 번들 JDK 사용) |

**미설치 (의도적)**

| 항목 | 사유 |
| --- | --- |
| 에뮬레이터 시스템 이미지 | **받아도 실행할 수 없다** → §14.2 |
| cmdline-tools | CLI 로 `sdkmanager` 를 쓸 일이 생기면 그때 설치 |
| git | 선택. 설계 변경이 잦았던 만큼 코드 이력은 남기는 편이 좋다 |

**참고 — SDK 설치 시 흔한 오해**

| 오해 | 실제 |
| --- | --- |
| "minSdk 33 이니 SDK 플랫폼 33 도 설치해야 한다" | **불필요.** SDK 플랫폼은 `compileSdk` 에만 필요하다. minSdk 는 컴파일 시점 검사일 뿐 플랫폼 설치와 무관 |
| "에뮬레이터도 최신 API 로 받으면 된다" | **아니다.** 배포 대상 갤럭시 S20 은 Android 13/14(API 33/34)를 돌린다. 최신 API 이미지에서 잘 돌아도 실기기와 다를 수 있다 (단 이 PC 에서는 §14.2 로 무의미) |

### 14.2 제약 — 이 PC 는 가상머신이라 에뮬레이터를 쓸 수 없다

```
Manufacturer : Xen
Model        : HVM domU
BIOS         : Xen / 4.17
VirtualizationFirmwareEnabled : False
Hyper-V / HypervisorPlatform  : 전부 InstallState = 2 (미설치)
```

가상 데스크톱(VDI) 환경이며 **중첩 가상화가 노출되지 않은 상태**다. Android 에뮬레이터는 하드웨어 가속(WHPX · AEHD · Hyper-V)을 요구하는데 이 중 어느 것도 사용할 수 없다.

- `HypervisorPlatform` 기능을 켜볼 수는 있으나 **관리자 권한이 필요**하고, Xen 게스트에서 중첩 가상화가 막혀 있으면 설치되어도 가속되지 않는다. 사내 관리 PC 라면 정책상 불가할 수도 있다
- 소프트웨어 렌더링 폴백은 실사용이 불가능한 속도다
- **따라서 시스템 이미지는 설치하지 않는다**

#### 이 PC 에서 가능/불가능

| 작업 | 가능 여부 |
| --- | --- |
| 코드 작성 · 컴파일 | 가능 |
| **`:core` 단위 테스트 (M2)** | **가능** — 원래 계획대로 진행 |
| APK 빌드 · 서명 | 가능 |
| 에뮬레이터 실행 | **불가** |
| adb USB 디버깅 | VDI 라 USB 패스스루가 안 될 가능성이 높음 (미검증) |
| adb 무선 디버깅 | 폰과 같은 네트워크에 도달해야 함 — 사내망에서는 어려울 수 있음 (미검증) |

#### 결론 및 대응

> **실기기(공기계)가 선택이 아니라 착수 조건이 된다.** M1 이후 모든 검증은 실기기에서 이뤄진다.

**디버깅 방식이 바뀐다.** adb 가 붙지 않으면 logcat 도 디버거도 쓸 수 없다. 반복 주기는 이렇게 된다:

```
이 PC 에서 APK 빌드 → 폰으로 파일 전송 → 설치 → 화면 보며 확인
```

경로 자체는 원래 배포 방식(§11 APK 수동 배포)과 같아 문제없지만, **화면에 보이는 것만으로 진단해야 한다.** 여기서 두 가지가 설계 요구사항으로 올라간다:

| 항목 | 이유 |
| --- | --- |
| **§10.1 상태 점검 대시보드** | 중요도 상승. 권한·서비스 상태를 앱 안에서 전부 확인할 수 있어야 한다 |
| **앱 내 디버그 로그 화면 (개발용)** | logcat 대신. 최근 N건의 내부 이벤트(서비스 시작/종료, 판정 결과, 예외)를 앱에서 볼 수 있게 한다. 디버그 빌드에서만 노출하고 릴리스에서는 숨긴다 |

> 이력 기능(§8)이 이미 Room 에 이벤트를 쌓고 있으므로, 디버그 로그 화면은 **같은 테이블에 개발용 이벤트 타입을 추가하는 방식으로 저비용 구현**이 가능하다.

### 14.3 2모듈 구조 — 기기 없이 최대한 진행하기 위한 설계

에뮬레이터도 기기도 없는 상황에서 검증 가능한 범위를 최대로 늘리려면, **안드로이드 API에 의존하지 않는 로직을 별도 모듈로 떼어내는 것**이 핵심이다.

```
프로젝트 구조
  :core   ← 순수 Kotlin/JVM (android 의존성 0)
            PolicyEngine, 시간대 정규화, dayKey 계산, 중복 억제 판정, 정책 모델
            → JVM 단위 테스트로 전량 검증. 이 PC 에서 바로 돌아간다
  :app    ← 안드로이드
            접근성 서비스, 오버레이, Room, Compose UI, 권한, 알람, 워커
            → 실기기에서만 검증 가능
```

| 작업 | 기기 필요 | 이 PC 에서 검증 |
| --- | --- | --- |
| **M2 정책 엔진 + 시간 경계 테스트** | 불필요 | **가능** |
| M4 이력 순수 로직 (중복 억제, `dayKey` 계산, 버퍼 정책) | 불필요 | **가능** (`:core` 에 배치) |
| M4 Room 저장·조회 | 필요 | 불가 |
| M1 접근성 차단 검증 | **필요** | 불가 |
| M3 · M5 UI | **필요** | 불가 (에뮬레이터 없음) |
| M6 배터리 측정 | **필요** | 불가 |

**부수 이득**: `:core` 는 안드로이드 의존성이 없으므로 나중에 Device Owner 방식으로 승격해도(§4.4) 그대로 재사용된다. `:app` 의 `Enforcer` 구현만 교체된다.

### 14.4 실기기가 없으면 검증되지 않는 것 (재확인)

에뮬레이터를 쓸 수 있었다 해도 이 앱의 핵심은 어차피 실기기가 필요했다. 지금은 에뮬레이터마저 없으므로 **`:app` 전체가 실기기 대기 상태**가 된다.

| 항목 | 에뮬레이터로도 불가 | 사유 |
| --- | --- | --- |
| One UI "제한된 설정" 절차 (§5.4) | **불가** | 삼성 스킨 고유 |
| 삼성 절전 정책 하에서 서비스 생존 | **불가** | 제조사 전원 관리 |
| 실제 배터리 소모 측정 (M6) | **불가** | 실측만 유효 |
| 실제 앱 전환 시 이벤트 지연·중복 양상 | **불가** | 기기 성능·스킨에 따라 다름 |

> **공기계 확보가 M1 착수의 선행 조건이다.** 그 전까지는 M2(`:core`)에 집중한다.

### 14.5 사내망·VDI 우회 설정 (실측 기록)

이 PC 에서 Gradle 을 돌리려면 **세 가지 문제를 모두** 넘어야 한다. 하나라도 빠지면 빌드가 시작조차 되지 않는다.
증상이 원인과 동떨어져 보이므로 그대로 기록해 둔다.

#### 문제 1 — Gradle 런처가 Java 8 로 실행된다

| | 내용 |
| --- | --- |
| 증상 | `Could not receive a message from the daemon`, 스택에 `Method.java:498` 같은 Java 8 프레임 |
| 원인 | `JAVA_HOME` 미설정 + PATH 의 `java` 가 Zulu 8. `org.gradle.java.home` 은 **데몬만** 바꾸고 런처는 못 바꾼다 |
| 해결 | 실행 전 `JAVA_HOME` 을 Android Studio 번들 JDK 로 지정 |

#### 문제 2 — 사용자 프로필 Temp 에서 AF_UNIX 소켓이 동작하지 않는다

| | 내용 |
| --- | --- |
| 증상 | `java.io.IOException: Unable to establish loopback connection` |
| 실제 원인 | `Selector.open()` 실패. 최신 JDK 는 Windows 에서 내부 파이프를 AF_UNIX 소켓으로 만드는데, `UnixDomainSockets.connect0` 이 `Invalid argument` 로 실패한다 |
| 확인한 것 | PowerShell 루프백 TCP 는 정상, 방화벽은 전부 꺼짐 → 네트워크 문제가 아니다. `java.io.tmpdir` 을 바꿔도 무효(AF_UNIX 는 OS TEMP 를 따로 읽는다). **`TEMP`/`TMP` 를 `C:\workspace\tmp` 로 바꾸면 해결.** 사용자 프로필의 긴 경로로 바꿔도 실패하므로 경로 길이나 8.3 단축명 문제가 아니라 **프로필 볼륨 자체의 특성**(VDI 프로필 디스크로 추정) |
| 해결 | 빌드 실행 시 `TEMP`/`TMP` 를 로컬 경로로 지정. `org.gradle.jvmargs` 의 `-D` 로는 **안 된다** — Gradle 이 데몬 기동 후에 적용해서 AF_UNIX 초기화 타이밍을 놓친다 |

#### 문제 3 — PAC 프록시 + 프록시의 TLS 재서명

| | 내용 |
| --- | --- |
| 증상 | 의존성 해석 실패 (`plugin was not found`), 빌드가 4분씩 걸리다 실패 |
| 원인 A | 프록시가 PAC(`http://70.10.5.20/sds.pac`)으로 배포된다. .NET/PowerShell 은 자동 감지하지만 **Java 는 PAC 을 읽지 않아** 연결이 타임아웃된다. PAC 의 기본 규칙은 `PROXY 70.10.15.10:8080` |
| 원인 B | 프록시가 TLS 를 재서명한다. 실제 체인을 덤프해 보면 `CN=repo.maven.apache.org` 의 발급자가 `CN=SDS, O=SAMSUNG SDS` 이고, **체인 길이가 1** 이라 발급자 인증서를 같이 주지도 않는다. JDK 기본 `cacerts` 에 없는 CA 이므로 `PKIX path building failed` |
| 해결 | 프록시를 명시적으로 지정 + `cacerts` 복사본에 사내 루트 CA 를 추가한 트러스트스토어 사용 |
| 함정 | `-Djavax.net.ssl.trustStoreType=Windows-ROOT` 는 이 JDK 에서 `NoSuchAlgorithmException` 으로 실패한다. 또 **keytool 이 한국어 로케일에서 `IllegalFormatConversionException` 을 내며 import 를 조용히 실패시킨다** → `-J-Duser.language=en` 을 붙일 것 |

#### 문제 4 — 프록시가 GitHub push 를 차단한다 (우회 불가)

| | 내용 |
| --- | --- |
| 증상 | `error: RPC failed; HTTP 403 curl 22` |
| 실제 응답 | `HTTP/1.1 403 Deny_upload` — **GitHub 의 응답이 아니다.** GitHub 는 표준 상태 문구를 쓴다. 사내 프록시가 만든 커스텀 응답이다 |
| 요청 순서 | `GET /info/refs?service=git-receive-pack` → **200** (GitHub 가 push 권한을 인정) / `POST /git-receive-pack` → **403** (프록시가 차단) |
| 판정 | 인증·권한 문제가 아니다. 자격 증명은 정상 저장되어 있고 읽기(`ls-remote`, `clone`)는 동작한다. **회사 보안 장비의 업로드 차단 정책**이다 |
| 대응 | **우회하지 않는다.** 선택지는 ① 로컬 git 만 사용 ② 개인 기기·개인 네트워크에서 push ③ 보안팀에 정식 예외 요청 |

> **결정(2026-09-17): ① 로컬 git 만 사용한다.** 원격 저장소는 쓰지 않으므로 `origin` 설정도 제거했다.
> 로컬 커밋으로 이력을 남기는 데는 아무 지장이 없다. 나중에 원격이 필요해지면
> `git remote add origin <url>` 한 줄이면 되고, 그때는 push 가 가능한 환경에서 해야 한다.
>
> **2026-09-18 재시도**: `origin` 을 다시 연결하고 push 했으나 결과는 동일했다
> (`ls-remote` 는 통과, `git-receive-pack` POST 는 `403`). 원격 설정은 **남겨 둔다** —
> 이 PC 에서는 실패하지만, 저장소 폴더를 push 가 가능한 환경으로 옮기면 그대로 쓸 수 있다.
>
> 원격이 없으므로 **저장소 폴더 자체가 유일한 사본이다.** 백업은 `C:\workspace\softguard`
> 폴더를 통째로 복사해두는 것으로 대신한다 (`.git` 에 이력이 모두 들어 있다).

#### 추가 확인 (2026-09-18) — 차단 기준은 git 프로토콜이 아니라 **파일 내용**이다

APK 를 GitHub 웹 UI 로 올려 폰에서 받으려 했으나 실패했다. 프로브로 원인을 좁혔다.

| 프로브 | 결과 |
| --- | --- |
| 5바이트 multipart 파일 POST → github.com | GitHub 이 `422` 로 직접 응답 — **프록시 통과** |
| 실제 APK(2.1MB, `.apk`, APK MIME) multipart POST → github.com | `403 Deny_upload` |
| 같은 본문 → uploads.github.com / s3.amazonaws.com / objects.githubusercontent.com | 전부 `403 Deny_upload` |

- 어제 "git-receive-pack 만 막는 규칙" 이라고 적은 것은 **틀렸다.** 프록시는 업로드 본문의
  내용(바이너리·크기)을 보고 판정하며 목적지를 가리지 않는다
- 따라서 **GitHub 웹 UI 업로드도 막힌다.** 사내망에서 바이너리를 밖으로 내보내는 경로는
  없다고 보는 것이 맞다
- 이 정책을 우회하는 시도(확장자 변경, 분할, 인코딩, 압축)는 **하지 않는다**
- 남는 선택지: ① 메일 첨부 — 메일 게이트웨이는 별개 시스템이라 판정이 다를 수 있다
  ② 보안팀에 정식 예외 요청 ③ 개인 PC 로 개발 환경 이전 (§14.9)

#### 구성 결과

| 위치 | 내용 |
| --- | --- |
| `C:\workspace\certs\corp-truststore.jks` | JDK `cacerts` 복사본 + 사내 루트 CA. 암호는 `changeit` |
| `C:\workspace\tmp` | 빌드용 `TEMP`. AF_UNIX 소켓이 여기에 만들어진다 |
| `softguard/gradle.properties` | `systemProp.*` 로 프록시·트러스트스토어 지정 (빌드 JVM 용) |
| `softguard/gw.cmd` | `JAVA_HOME` · `TEMP`/`TMP` · `GRADLE_OPTS` 를 세팅한 뒤 `gradlew.bat` 을 호출하는 실행 래퍼 |

> **`gradlew` 를 직접 쓰면 실패한다. 반드시 `gw.cmd` 를 쓴다.**

#### Android Studio 에서 빌드할 때 (미검증)

Studio 가 띄우는 Gradle 데몬은 **Studio 프로세스의 환경변수를 물려받는다.** 따라서 문제 2(`TEMP`)가 그대로 재현될 가능성이 높다. 다음 중 하나가 필요하다.

- Studio 를 `TEMP`/`TMP` 가 설정된 상태에서 실행 (배치 스크립트로 띄우기)
- 또는 사용자 환경변수 `TEMP`/`TMP` 를 로컬 경로로 변경 (다른 프로그램에 영향이 갈 수 있어 권장하지 않음)
- Gradle JDK 설정은 Studio 가 번들 JDK 를 쓰므로 문제 1 은 자동 해결된다
- 프록시·트러스트스토어는 `gradle.properties` 에 있으므로 문제 3 은 자동 해결된다

> 당장은 **CLI(`gw.cmd`)로 빌드·테스트가 가능**하므로 M2 진행에 지장이 없다. Studio 빌드는 UI 작업(M3)에 들어갈 때 확인한다.

### 14.6 프로젝트 골격 (구성 완료)

```
C:\workspace\                  ← 저장소 밖. 커밋되지 않는다
  certs\corp-truststore.jks    ← 사내 CA 포함 트러스트스토어 (§14.5)
  tmp\                         ← 빌드용 TEMP (§14.5)
  tools\gradle-9.7.1\          ← wrapper 생성에 쓴 Gradle 배포본

  softguard\                   ← git 저장소 루트
    docs\agent.md              ← 이 문서
    gw.cmd                     ← 개발 PC 전용 실행 래퍼
    gradlew / gradlew.bat
    settings.gradle.kts
    build.gradle.kts
    gradle.properties
    .gitattributes / .gitignore
    gradle\libs.versions.toml  ← 버전 카탈로그
    app\                       ← 안드로이드 (AGP 9.4.0, minSdk 33, targetSdk 37)
      src\main\kotlin\kr\woorijip\softguard\
        SoftGuardApp.kt        ← 정책 초기화
        data\    Policies (filesDir/policy.json), DetectionLog (진단용 메모리 로그)
        service\ SoftGuardAccessibilityService, BlockOverlay
        ui\      MainActivity + 상태 / 차단앱 / 시간대 / 감지로그 탭
        util\    Permissions, InstalledApps
      src\main\res\xml\accessibility_service_config.xml
      proguard-rules.pro       ← R8 keep 규칙
    core\                      ← 순수 Kotlin/JVM
      src\main\kotlin\kr\woorijip\softguard\core\
        model\   TimeOfDay, AllowWindow, Policy
        policy\  WeeklySchedule, PolicyEngine, Verdict
        history\ DayKey, DuplicateSuppressor
        storage\ PolicyCodec, PolicyDto
      src\test\kotlin\...      ← 단위 테스트
```

> **사내망 설정은 저장소에 두지 않는다.** 프록시 주소·트러스트스토어 경로는
> `%USERPROFILE%\.gradle\gradle.properties` 에 있다. 저장소를 GitHub 에 올려도
> 사내 네트워크 정보가 새지 않는다.

**버전**: Gradle 9.7.1 · Kotlin 2.4.20 · `:core` 바이트코드 타깃 JVM 17 (`:app` 의 D8 이 읽을 수 있도록)
**`:app` 은 아직 없다.** AGP 9.4.0 · Compose BOM 2026.09.00 등은 `libs.versions.toml` 에 미리 올려두었다.
Room/KSP 는 KSP 최신(2.3.12)과 Kotlin 2.4.20 의 조합이 불확실해 M4 로 미뤘다.

**실행 방법**

```
cd C:\workspace\softguard
gw.cmd :core:test          단위 테스트
gw.cmd :app:assembleRelease  기기에 넣을 APK (축소됨, 약 2MB)
gw.cmd :app:assembleDebug    디버그 APK (축소 안 함, 약 28MB)
gw.cmd tasks                 태스크 목록
```

### 14.7 `:app` 을 올리며 부딪힌 것 (실측)

| 문제 | 원인과 해결 |
| --- | --- |
| `plugin is already on the classpath with an unknown version` | 플러그인 버전을 루트 `build.gradle.kts` 에서 `apply false` 로 한 번만 선언하고, 하위 모듈은 버전 없이 `alias(...)` 만 쓴다 |
| `The 'org.jetbrains.kotlin.android' plugin is no longer required since AGP 9.0` | **AGP 9 부터 Kotlin 지원이 AGP 에 내장되었다.** `kotlin.android` 플러그인을 선언하면 빌드가 거부된다. 제거해야 한다 (`kotlin.plugin.compose` 는 그대로 필요) |
| `local.properties` 없음 | `ANDROID_HOME` 이 설정되어 있지 않아 SDK 경로를 못 찾는다. `sdk.dir` 을 적어준다 (`.gitignore` 대상) |
| 데몬 Metaspace 부족 | `org.gradle.jvmargs` 에 `-XX:MaxMetaspaceSize=1024m` 추가 |
| `gw.cmd` 의 한글 주석이 오류를 뿜음 | cmd.exe 가 콘솔 코드페이지로 UTF-8 을 잘못 읽어 주석이 명령으로 해석된다. **배치 파일 주석은 ASCII 로 쓴다** |

#### APK 크기 — R8 축소가 선택이 아니라 필수다

기기에 직접 연결할 수 없어 **메일로 APK 를 옮긴다.** Gmail 첨부 한도는 25MB 다.

| 빌드 | 크기 | 내용 |
| --- | --- | --- |
| debug (축소 없음) | **28.44 MB** | `classes.dex` 17.7MB + `classes12.dex` 9.9MB — 거의 전부 DEX |
| ABI 1개로 제한 + ui-tooling 제거 | 28.44 MB | **효과 없음.** 네이티브 라이브러리는 0.01MB 뿐이었다 |
| release (R8 축소 + 리소스 축소) | **2.04 MB** | 이것만이 유효한 수단이었다 |

- 릴리스 keystore 는 M7 에서 만든다. 지금은 **디버그 키로 서명**해 그대로 설치할 수 있게 했다
- R8 이 매니페스트 컴포넌트와 `kotlinx.serialization` 을 건드리지 않도록 `app/proguard-rules.pro` 에 keep 규칙을 명시했다. 릴리스 빌드에서만 드러나는 사고를 기기에서 진단할 방법이 없기 때문이다

### 14.8 M1 기기 테스트 절차 (갤럭시 S20 이상)

APK: `gw.cmd :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

1. **설치** — 파일을 기기로 옮겨 실행. "출처를 알 수 없는 앱 설치" 를 1회 허용
2. **제한된 설정 허용** — 앱 실행 → [상태] 탭 → `앱 정보 열기` → 우측 상단 ⋮ → **"제한된 설정 허용"**
   *이 단계를 건너뛰면 다음 단계에서 토글이 회색으로 잠겨 눌리지 않는다*
3. **접근성 켜기** — [상태] 탭 → `접근성 설정 열기` → 설치된 앱 → 우리집 소프트가드 → 켜기
4. **오버레이 권한** — [상태] 탭 → `권한 설정 열기` → 허용
5. **정확 알람** — [상태] 탭 → `알람 권한 설정 열기` → 허용.
   없어도 동작하지만 구간 종료가 수 분 늦어진다
6. **배터리 최적화 제외** — [상태] 탭 → `배터리 설정 열기` → 제한 없음

   여기까지 하면 [상태] 탭 맨 위가 **"제어 동작 중"** 이 된다.
   "서비스 연결 대기 중" 이면 권한은 다 켜졌고 시스템이 서비스를 바인드하기를 기다리는 중이다.

7. **감지 확인** — 홈으로 나가 아무 앱이나 두세 개 열었다 돌아와 [감지 로그] 탭 확인.
   줄이 쌓이면 **접근성 서비스가 동작하는 것이다.** 비어 있으면 3번이 안 된 것
8. **차단 확인** — [차단 앱] 탭에서 앱 하나를 켠다 → [시간대] 탭을 비워 둔 채 그 앱을 실행
   → 차단 화면이 덮이면 **집행이 동작하는 것이다**
9. **시간대 진입 확인** — [시간대] 탭에서 지금 시각을 포함하는 구간을 추가 → 같은 앱이 열리는지 확인
10. **구간 종료 쫓아내기 확인** — 구간 종료를 **1~2분 뒤로** 잡아두고 그 앱을 열어 둔 채 기다린다.
    종료 시각에 차단 화면이 덮이면 §7.4 의 알람이 동작하는 것이다.
    [감지 로그] 에 `허용 구간 종료 → 차단 …` 줄이 남는다
11. **재부팅 확인** — 기기를 재부팅하고 **[상태] 탭이 "제어 동작 중" 인지, 차단이 여전히
    걸리는지**로 판정한다. 이 두 가지가 부팅 복구의 기준이다.

    > [감지 로그] 의 `기기 부팅` 줄은 있으면 참고가 되지만 **판정 기준으로 쓰지 말 것.**
    > 이 로그는 메모리에만 있어서, 접근성 서비스가 바인드되기 전에 프로세스가 정리되면
    > 사라진다. 그때도 제어는 정상 복구된다.

각 단계에서 막힌 지점과 화면에 나온 문구를 기록해 둘 것. 그것이 M1 의 산출물이다.

**진단 화면이 알려주는 것**

| 화면에 나오는 것 | 뜻 |
| --- | --- |
| "제어 중단됨" | 접근성 또는 오버레이 권한이 빠졌다 |
| "서비스 연결 대기 중" | 권한은 됐고 시스템 바인드를 기다린다. 앱을 닫고 잠시 뒤 다시 열어 본다 |
| "설정 화면을 열지 못했습니다" | 이 기기에 해당 설정 액티비티가 없다. 기기 설정 앱에서 직접 찾아야 한다 |
| "정책에 문제가 있습니다" | 정책 파일을 읽지 못했다. **아무것도 차단하지 않는 상태로 동작 중** |
| 로그에 "차단 화면을 띄우지 못해 홈으로 내보냈습니다" | 오버레이 권한이 없어 폴백으로 집행했다. 4번을 확인 |

**현재 상태 (2026-09-17)**: `gw.cmd :core:test` → **84개 테스트 전부 통과**

| 테스트 | 개수 | 검증 범위 |
| --- | --- | --- |
| `PolicyCodecTest` | 24 | 저장 포맷 골든 테스트, 왕복, 결정적 출력, 버전·기본정책 검증, 깨진 JSON·모르는 키·잘못된 요일/시각 거부, 실패 시 fail-open |
| `WeeklyScheduleTest` | 18 | 반열린 구간, 요일 분해, 구간 합치기, `24:00`, 자정 넘김, 주 경계 감싸기, 다음 주 탐색, 한 주 전체 허용 |
| `PolicyEngineTest` | 14 | §7.3 판정 5단계, 경계 정각, 초 단위 절삭, 복합 구간 |
| `DuplicateSuppressorTest` | 10 | 10초 억제, 왕복 억제, 반복 두드림 시 주기적 기록, 메모리 상한 |
| `TimeOfDayTest` | 7 | `HH:mm` 파싱, `24:00` 허용, 형식 검증 |
| `DayKeyTest` | 6 | `yyyyMMdd` 변환, 자정 경계, 180일 경계 계산 |
| `AllowWindowTest` | 5 | 구간 유효성 검증 |

**M2 완료.** 정책 모델 · 판정 엔진 · 시간 경계 로직 · 저장 포맷까지 끝났다. `:app` 은 `PolicyCodec` 이 만든 문자열을 DataStore 에 쓰고 읽기만 하면 된다.

### 14.9 APK 를 기기로 옮기는 문제 — 선택지 (미결)

사내 VDI 에서 빌드한 APK 를 공기계에 넣을 경로가 막혀 있다 (§14.5 문제 4 추가 확인).
직접 연결(USB)도 불가, GitHub·S3 등 외부 업로드도 내용 기준으로 차단된다.

| 선택지 | 내용 | 비고 |
| --- | --- | --- |
| **① 메일 첨부** | 사내 메일로 개인 주소에 APK 첨부 | 메일 게이트웨이는 프록시와 별개 시스템이라 판정이 다를 수 있다. **가장 먼저 시도** |
| ② 보안팀 예외 요청 | 목적(가족용 개인 앱 테스트)을 밝히고 1회 반출 승인 요청 | 정식 경로. 회사 판단에 따른다 |
| ③ 개인 PC 로 개발 이전 | 빌드·배포를 개인 PC 에서 한다 | 소스(텍스트 ~2,500줄)와 설계문서를 옮겨야 한다. 코드는 설계문서에서 다시 만들 수 있으므로 **`docs/agent.md` 가 가장 중요한 산출물**이다 |

- 어느 경우든 **DLP 를 속이는 방법(확장자 변경·분할·인코딩)은 쓰지 않는다.** 회사가 막은 것은
  막힌 대로 두고, 허용된 경로로만 간다
- ③ 으로 가면 이 PC 의 §14.5 우회 설정(프록시·트러스트스토어·TEMP·`gw.cmd`)은 전부 불필요해진다.
  일반 `gradlew` 로 바로 빌드된다

