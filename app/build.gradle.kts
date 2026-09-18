// :app — 안드로이드 모듈. 접근성 서비스, 오버레이, Room 이력, Compose UI, 알람, 워커.
// 빌드는 GitHub Actions(.github/workflows/android.yml)에서만 합니다.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "kr.woorijip.softguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.woorijip.softguard"
        minSdk = 33
        targetSdk = 35
        // 버전을 올릴 때는 두 줄을 함께. versionCode 는 설치된 것보다 커야 업데이트됩니다.
        versionCode = 1
        versionName = "0.1"
    }

    // 서명 키는 GitHub Actions 의 secret 에서 옵니다 (원본은 PC 의 android-signing.local 폴더).
    // 환경 변수가 없으면 서명 없이 빌드만 됩니다.
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = rootProject.file(ks)
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 기기에서 logcat 을 볼 수 없으므로 R8 이 만드는 문제를 진단할 방법이 없습니다.
            // APK 는 GitHub 에서 내려받으므로 크기 제한도 없습니다. 축소는 끕니다.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
