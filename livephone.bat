@echo off
title Node Player - Live Phone Sync
cd /d "%~dp0"

:: Point to your D: drive Android SDK (change this path if you extracted it elsewhere)
if not defined ANDROID_HOME set "ANDROID_HOME=D:\Android"
set "D:\Node player\android-sdk\platform-tools"
echo ==================================================
echo  Checking for connected Android device...
echo ==================================================
adb devices

echo.
echo [!] If your phone is listed above as "device", it is ready.
echo [!] If it says "unauthorized", tap Allow USB Debugging on your phone.
echo.
pause

echo ==================================================
echo  Starting Continuous Build & Install (Ctrl+C to stop)
echo  Save any .kt file to push changes to your phone!
echo ==================================================
call gradlew.bat installDebug --continuous
pause