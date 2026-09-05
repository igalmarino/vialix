# Ferrostar's Rust core is reached through UniFFI + JNA. Keep the generated bindings
# and JNA intact when minification is enabled.
-keep class uniffi.ferrostar.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
