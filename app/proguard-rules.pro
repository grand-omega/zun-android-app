# ---- Retrofit ----
# Retrofit inspects service interfaces and their generic return types via reflection.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface * extends retrofit2.Call
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keep,allowobfuscation interface dev.zun.flux.data.api.** { *; }

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ---- kotlinx.serialization ----
# The plugin generates $$serializer singletons that are referenced only reflectively.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class dev.zun.flux.data.api.**$$serializer { *; }
-keepclassmembers class dev.zun.flux.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class dev.zun.flux.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Coil ----
-dontwarn coil.**

# ---- Kotlin metadata (required for Retrofit suspend functions) ----
-keep class kotlin.Metadata { *; }
