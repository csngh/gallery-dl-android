# Chaquopy's native code (libchaquopy_java) looks its Java classes, fields and methods up by their
# original names through JNI. If R8 renames or strips them, Python.start() segfaults in release builds.
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Python calls these methods by name through reflection, so R8 must not rename or remove them.
-keep interface app.gallerydl.download.DownloadListener { *; }
-keep class * implements app.gallerydl.download.DownloadListener { *; }
