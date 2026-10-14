# ProGuard / R8 rules for PixelSchool
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# OkHttp & Okio
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# WorkManager
-keep class androidx.work.** { *; }

# Security Crypto & Tink
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Data Models used in serialization and cache
-keep class com.pixelschool.app.data.model.** { *; }
