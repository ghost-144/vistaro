# Keep JavascriptInterface for WebView player bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit & OkHttp
-dontnote retrofit2.Platform
-dontwarn retrofit2.Platform$Java8
-keepattributes Signature, Exceptions
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# MongoDB & BSON
-dontwarn com.mongodb.**
-dontwarn org.bson.**
-dontwarn org.ietf.jgss.**
-dontwarn io.netty.**
-dontwarn org.xerial.snappy.**
-dontwarn com.github.luben.zstd.**
-dontwarn io.micrometer.**
-dontwarn org.slf4j.**
-dontwarn reactor.**
-keepattributes EnclosingMethod
-keep class com.mongodb.** { *; }
-keep class org.bson.** { *; }
