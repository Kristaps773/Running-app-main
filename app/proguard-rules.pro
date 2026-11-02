# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
# R8 full mode + Kotlin suspend: keep Continuation so Retrofit can read return types.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class okhttp3.ResponseBody

# Gson + Retrofit generics (release minify otherwise erases List<T> → ClassCastException
# LinkedTreeMap, which we mis-labeled as "invalid routing data" / response_parse).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# R8 3+ strips generic signatures on anonymous TypeToken subclasses without these.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <fields>;
    <methods>;
}

# Data models — keep fields + ctors so Gson reflection keeps List<OsrmWaypoint> etc.
-keep class com.orienteer.app.data.model.** { *; }
-keepclassmembers class com.orienteer.app.data.model.** {
    <init>(...);
    <fields>;
}

# Backup JSON (Gson encode/decode of Room entities + settings snapshot)
-keep class com.orienteer.app.data.backup.** { *; }
-keep class com.orienteer.app.data.local.entity.** { *; }

# Retrofit API interfaces (release minify must keep suspend service contracts + signatures)
-keep,allowobfuscation interface com.orienteer.app.data.api.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface com.orienteer.app.data.api.** {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OSMDroid
-keep class org.osmdroid.** { *; }

# Custom map marker bitmaps (release shrink must not strip these)
-keep class com.orienteer.app.presentation.map.CheckpointMarkerIcon { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt / Dagger (required for release minify — missing rules cause instant startup crash)
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.internal.OriginatingElement <methods>;
}
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class com.orienteer.app.Hilt_* { *; }
-keep class com.orienteer.app.**_HiltComponents$* { *; }
-keep class com.orienteer.app.*_GeneratedInjector { *; }
-keep class hilt_aggregated_deps.** { *; }
