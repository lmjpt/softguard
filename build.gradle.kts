// 우리집 소프트가드 — 루트 빌드 스크립트.
// 플러그인 버전은 여기서 한 번만 선언하고, 하위 모듈은 버전 없이 id 만 적습니다.
// 조합은 가족 앱(family-hub/android)에서 GitHub Actions 빌드로 검증된 것입니다.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
