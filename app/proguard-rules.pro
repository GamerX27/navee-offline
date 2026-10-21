# Mapsforge loads render themes and graphics reflectively.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**
-dontwarn com.caverock.androidsvg.**
-dontwarn org.slf4j.**

# Release builds drop verbose/debug logging entirely, including the frame dumps.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
