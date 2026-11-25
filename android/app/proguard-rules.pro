# Keep Kotlinx Serialization models & Retrofit interfaces
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.ghtrending.data.remote.**$$serializer { *; }
-keepclassmembers class com.example.ghtrending.data.remote.** { *** Companion; }
