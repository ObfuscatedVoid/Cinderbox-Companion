# JavaSteam / SteamKit - keep protobuf and crypto classes
-keep class in.dragonbra.javasteam.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class org.spongycastle.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# SpongyCastle — LDAP/JNDI classes not available on Android
-dontwarn javax.naming.**

# Tink / ErrorProne annotations — compile-time only
-dontwarn com.google.errorprone.annotations.**

# JavaSteam — optional Zstd dependency not bundled
-dontwarn com.github.luben.zstd.**
