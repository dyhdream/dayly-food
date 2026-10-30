# Keep Hilt and Room generated code usable when minified.
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn dagger.hilt.**
