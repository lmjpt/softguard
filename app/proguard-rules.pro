# 지금은 R8 축소를 끄고 있어 이 규칙은 쓰이지 않습니다.
# 나중에 isMinifyEnabled = true 로 바꾸면 최소한 아래는 지켜야 합니다 (설계문서 §11).
-keep class kr.woorijip.softguard.service.** { *; }
-keep class kr.woorijip.softguard.data.db.** { *; }
-keep class kr.woorijip.softguard.core.storage.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepattributes *Annotation*, InnerClasses, Signature
