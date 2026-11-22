# ---------- youtubedl-android ----------
# VideoInfo / VideoFormat are filled by Jackson through reflection + @JsonProperty on
# Kotlin properties. Without this keep rule R8 renames the fields and every getInfo() call
# deserialises into an object with null title / 0 duration.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class com.yausername.youtubedl_android.YoutubeDLRequest { *; }
-keep class com.yausername.youtubedl_android.YoutubeDLOptions { *; }
-keep class com.yausername.youtubedl_android.YoutubeDLResponse { *; }
-keep class com.yausername.youtubedl_android.YoutubeDL { *; }
-keep class com.yausername.youtubedl_android.YoutubeDLException { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# Native entry points resolve by name.
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------- Jackson (transitive of youtubedl-android) ----------
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotation
-dontwarn com.fasterxml.jackson.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}
-dontwarn org.joda.time.**
-dontwarn org.w3c.dom.bootstrap.**

# ---------- commons-io / transitive noise ----------
-dontwarn org.apache.commons.io.**
-dontwarn java.awt.**
-dontwarn javax.**

# ---------- Room: entities + generated impls ----------
-keep class dev.hamza.localtube.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ---------- Media3 / ExoPlayer ----------
-dontwarn androidx.media3.**

# ---------- Kotlin/Compose housekeeping ----------
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class coil3.** { *; }
-dontwarn coil3.**

# Strip debug info from release builds.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
