# Type-safe navigation routes are @Serializable objects; keep their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class app.fitos.personal.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.fitos.personal.**$$serializer { *; }
