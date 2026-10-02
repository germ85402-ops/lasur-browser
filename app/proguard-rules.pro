# The browser's own classes are small; keep them intact (JavaScript bridge, reflection-free but safest).
-keep class com.lumen.browser.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface,*Annotation*
-dontwarn androidx.**
-assumenosideeffects class android.util.Log { public static int d(...); public static int v(...); }
