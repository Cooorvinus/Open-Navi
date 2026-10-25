# MapLibre Native
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.opennavi.**$$serializer { *; }
-keepclassmembers class com.opennavi.** {
    *** Companion;
}
-keepclasseswithmembers class com.opennavi.** {
    kotlinx.serialization.KSerializer serializer(...);
}
