# Ferrostar's Rust core is reached through UniFFI + JNA. Keep the generated bindings
# and JNA intact when minification is enabled.
-keep class uniffi.ferrostar.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# Keep line numbers so a release stack trace can be read back through mapping.txt (archived by CI
# and attached to every GitHub release); the file names themselves are collapsed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
