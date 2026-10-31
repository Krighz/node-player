@echo off
setlocal
cd /d "%~dp0"

echo [Node Player] Building Debug APK...
set "ANDROID_HOME=D:\AI MAKING\android-sdk"
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    exit /b %ERRORLEVEL%
)

echo [Node Player] Installing APK to connected device...
adb install -r app-android\build\outputs\apk\debug\app-android-debug.apk

if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] adb install failed or hung, falling back to pm install...
    adb push app-android\build\outputs\apk\debug\app-android-debug.apk /data/local/tmp/app-debug.apk
    adb shell pm install -r /data/local/tmp/app-debug.apk
    adb shell rm /data/local/tmp/app-debug.apk
)

echo [Node Player] Launching Node Player...
adb shell am start -n com.nodeplayer.music/.MainActivity

echo [Node Player] Done!
endlocal
