# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in proguard-android-optimize.txt

# Keep model classes
-keep class com.smsauto.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
