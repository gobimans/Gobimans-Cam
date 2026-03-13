# Proguard rules for Gobimans Cam
# This file is used to configure ProGuard code obfuscation

# Keep the main class
-keep public class com.gobimans.cam.MainActivity

# Keep all Activity subclasses
-keep public class * extends android.app.Activity

# Keep Camera2 classes
-keep class android.hardware.camera2.** { *; }

# Keep camera permission requirements
-keep class android.Manifest$permission { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Don't obfuscate names of classes that extend Activity or Fragment
-keep class * extends android.app.Fragment
-keep class * extends androidx.fragment.app.Fragment
