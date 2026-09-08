@echo off
setlocal enabledelayedexpansion
:: NOTE: no chcp 65001 here -- it corrupts batch reading under output
:: redirection (cmd re-reads the file at a wrong offset and dies silently).
:: Script output is ASCII English on purpose.
title JumpDaily APK Builder

:: ============================================================
:: JumpDaily one-click build script (2026-09-07)
::
:: Usage: double-click (defaults: all channels, release), or:
::   build_apk.bat                          -> all channels, release
::   build_apk.bat huawei                   -> huawei channel, release
::   build_apk.bat xiaomi debug             -> xiaomi channel, debug
::   build_apk.bat official release install -> build + adb install
::
:: Output archived to: dist\<today>\  (folder named by date)
:: APK name: JumpDaily_v1.0.0_huawei_release_20260907.apk
:: ============================================================

:: ===== Toolchain paths (edit here when switching dev machines) =====
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"
set "ANDROID_HOME=D:\dev\Android\Sdk"

:: ===== Args =====
set "FLAVOR=%~1"
if "%FLAVOR%"=="" set "FLAVOR=all"
set "BUILDTYPE=%~2"
if "%BUILDTYPE%"=="" set "BUILDTYPE=release"
set "DO_INSTALL=%~3"

set "FLAVOR_OK=0"
for %%c in (all official huawei xiaomi) do if /i "%FLAVOR%"=="%%c" set "FLAVOR_OK=1"
if "%FLAVOR_OK%"=="0" (
    echo [ERROR] invalid channel: %FLAVOR%
    echo Usage: build_apk.bat [all^|official^|huawei^|xiaomi] [release^|debug] [install]
    pause
    exit /b 1
)
for %%c in (all official huawei xiaomi) do if /i "%FLAVOR%"=="%%c" set "FLAVOR=%%c"

set "TYPE_OK=0"
for %%t in (release debug) do if /i "%BUILDTYPE%"=="%%t" set "TYPE_OK=1"
if "%TYPE_OK%"=="0" (
    echo [ERROR] invalid build type: %BUILDTYPE% - release/debug only
    pause
    exit /b 1
)
for %%t in (release debug) do if /i "%BUILDTYPE%"=="%%t" set "BUILDTYPE=%%t"

:: Gradle task needs capitalized channel/type: assembleHuaweiRelease
set "FLAVOR_CAP=Official"
if "%FLAVOR%"=="huawei" set "FLAVOR_CAP=Huawei"
if "%FLAVOR%"=="xiaomi" set "FLAVOR_CAP=Xiaomi"
set "TYPE_CAP=Release"
if "%BUILDTYPE%"=="debug" set "TYPE_CAP=Debug"

:: ===== Project root (script lives in tools\, root is one level up) =====
cd /d "%~dp0.."

echo.
echo ==================================================
echo   JumpDaily build    channel=%FLAVOR%    type=%BUILDTYPE%
echo ==================================================
echo.

:: ===== Build =====
if "%FLAVOR%"=="all" (
    if "%BUILDTYPE%"=="release" (
        call gradlew.bat assembleRelease
    ) else (
        call gradlew.bat assembleDebug
    )
) else (
    call gradlew.bat assemble%FLAVOR_CAP%%TYPE_CAP%
)
if errorlevel 1 goto :fail

:: ===== Archive: copy APKs to dist\^<today^>\ =====
:: PowerShell gets the date; %date% format varies by region
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd"') do set "TODAY=%%i"
set "DIST=dist\%TODAY%"
if not exist "%DIST%" mkdir "%DIST%"

set "APKDIR=app\build\outputs\apk"
if "%FLAVOR%"=="all" (
    for %%d in (official huawei xiaomi) do (
        if exist "%APKDIR%\%%d\%BUILDTYPE%\*.apk" copy /y "%APKDIR%\%%d\%BUILDTYPE%\*.apk" "%DIST%\" >nul
    )
) else (
    if exist "%APKDIR%\%FLAVOR%\%BUILDTYPE%\*.apk" copy /y "%APKDIR%\%FLAVOR%\%BUILDTYPE%\*.apk" "%DIST%\" >nul
)

:: ===== Optional: install to connected device after build =====
:: For "all" only the official APK is installed (same applicationId, one is enough)
if /i "%DO_INSTALL%"=="install" (
    echo.
    echo ===== Installing to device =====
    set "INSTALL_APK=%APKDIR%\official\%BUILDTYPE%"
    if not "%FLAVOR%"=="all" set "INSTALL_APK=%APKDIR%\%FLAVOR%\%BUILDTYPE%"
    for %%f in ("!INSTALL_APK!\*.apk") do (
        adb install -r "%%~ff"
    )
)

:: ===== Result list =====
echo.
echo ===== DONE! Artifacts in %DIST% =====
for %%f in ("%DIST%\*.apk") do (
    set /a MB=%%~zf / 1048576
    echo   %%~nxf   ^(!MB! MB^)
)
echo.
pause
exit /b 0

:fail
echo.
echo [FAILED] build failed. Check errors above - keystore config or network for deps.
pause
exit /b 1
