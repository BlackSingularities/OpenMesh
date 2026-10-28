# Compose, AndroidX i Play Services dostarczają własne reguły konsumenckie.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# kotlinx.serialization: zachowanie wygenerowanych serializatorów modeli pakietów i danych.
-keepclassmembers @kotlinx.serialization.Serializable class com.blacksingularities.openmesh.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.blacksingularities.openmesh.**$$serializer { *; }
-keepclasseswithmembers class com.blacksingularities.openmesh.** {
    kotlinx.serialization.KSerializer serializer(...);
}
