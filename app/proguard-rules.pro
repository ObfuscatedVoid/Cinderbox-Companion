# JavaSteam / SteamKit - keep protobuf and crypto classes
-keep class in.dragonbra.javasteam.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class org.spongycastle.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
