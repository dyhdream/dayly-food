# Keep Hilt and Room generated code usable when minified.
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn dagger.hilt.**

# ML Kit text recognition loads its pipeline by reflection; R8 must not rename or strip it.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-dontwarn com.google.mlkit.**
