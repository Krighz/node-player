@echo off
echo ========================================================
echo  Node Player - Fast Build and Install on Android Device
echo ========================================================

cd /d "%~dp0\.."

echo [1/3] Building debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Gradle build failed!
    exit /b %ERRORLEVEL%
)

echo [2/3] Checking connected ADB devices...
adb devices

echo [3/3] Installing APK onto target device...
adb install -r app-android\build\outputs\apk\debug\app-android-debug.apk
if %ERRORLEVEL% neq 0 (
    echo [ERROR] APK installation failed!
    exit /b %ERRORLEVEL%
)

echo [DONE] Starting Node Player...
adb shell am start -n com.nodeplayer.music/.MainActivity
echo App successfully deployed and launched.
