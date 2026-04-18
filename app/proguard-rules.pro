# Intentionally minimal for foundation scaffolding.

# ---- Vosk / Kaldi ----
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# ---- JNA (required by Vosk) ----
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# ---- Picovoice / Porcupine ----
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**
