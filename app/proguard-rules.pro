# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Compose 运行时无需额外规则，AGP 已内置
