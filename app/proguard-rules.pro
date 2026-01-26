# R8/Proguard keep rules
#
# Startup relies on the Application class declared in AndroidManifest.xml.
# If it gets stripped, the app crashes on launch with ClassNotFoundException.
-keep class com.trimsytrack.TrimsyApp { *; }

# Core graph is referenced widely and must never be stripped.
-keep class com.trimsytrack.AppGraph { *; }

