# Room entities — keep table field names for generated mappings
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# SQLCipher JNI (net.zetetic:sqlcipher-android)
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Tink (security-crypto) references compile-only errorprone annotations
-dontwarn com.google.errorprone.annotations.**
