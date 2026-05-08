# ---- Project Specific -------------------------------------------------------
-keep class dev.zun.flux.data.api.** { *; }
-keep interface dev.zun.flux.data.api.** { *; }

# ---- Retrofit (R8 full mode) ------------------------------------------------
# Retrofit 2.7+ ships consumer-rules.pro, but R8 full mode still needs these
# to preserve generic return types and suspend continuations.
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Service method parameters must be retained.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn javax.annotation.**
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ---- Tink (via androidx.security.crypto / EncryptedSharedPreferences) -------
# Tink references compile-only error-prone annotations that aren't on the
# runtime classpath; safe to ignore.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# ---- kotlinx.serialization (canonical rules from the upstream README) -------
# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep class * implements androidx.room.Entity

# ---- SQLCipher --------------------------------------------------------------
# JNI entry points + classes referenced from native code.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# ---- Coil -------------------------------------------------------------------
-dontwarn coil.**

# ---- Kotlin metadata --------------------------------------------------------
-keep class kotlin.Metadata { *; }
