# kotlinx.serialization: zachowanie wygenerowanych serializatorów ustawień i pamięci tekstów.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.blacksingularities.openlyrics.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.blacksingularities.openlyrics.**$$serializer { *; }
-dontwarn org.slf4j.**
