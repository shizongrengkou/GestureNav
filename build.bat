@echo off
setlocal enabledelayedexpansion
set SDK=C:\Users\Administrator\AppData\Local\Android\Sdk
set BUILD_TOOLS=%SDK%\build-tools\34.0.0
set PLATFORM=%SDK%\platforms\android-30
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set JAVAC=%JAVA_HOME%\bin\javac.exe

pushd %~dp0
set ROOT=%CD%
popd

set SRC=%ROOT%\src
set RES=%ROOT%\res
set BLDDIR=%ROOT%\build
set OBJ=%BLDDIR%\obj
set DEXDIR=%BLDDIR%\dex
set GEN=%BLDDIR%\gen
set MANIFEST=%ROOT%\AndroidManifest.xml
set AAPT2=%BUILD_TOOLS%\aapt2.exe
set D8=%BUILD_TOOLS%\d8.bat
set ZIPALIGN=%BUILD_TOOLS%\zipalign.exe
set APKSIGNER=%BUILD_TOOLS%\apksigner.bat

echo =====================================
echo  Building M3H Gesture Nav
echo =====================================

rem Clean build dirs
if exist "%BLDDIR%" rd /s /q "%BLDDIR%"
mkdir "%BLDDIR%" "%OBJ%" "%DEXDIR%" "%GEN%"

rem Step 1: compile resources
echo [1/5] Compiling resources...
pushd "%RES%"
"%AAPT2%" compile -o "%OBJ%" --dir .
popd
if errorlevel 1 goto :error

rem Step 2: link
echo [2/5] Linking resources...
"%AAPT2%" link -o "%BLDDIR%\base.apk" -I "%PLATFORM%\android.jar" --manifest "%MANIFEST%" --java "%GEN%" --min-sdk-version 30 --target-sdk-version 30 "%OBJ%\*.flat"
if errorlevel 1 goto :error

rem Step 3: compile Java
echo [3/5] Compiling Java sources...
"%JAVAC%" -d "%OBJ%" -classpath "%PLATFORM%\android.jar" -sourcepath "%SRC%;%GEN%" --release 11 "%SRC%\com\m3h\gesturenav\*.java" "%GEN%\com\m3h\gesturenav\*.java"
if errorlevel 1 goto :error

rem Step 4: dex
echo [4/5] Converting to dex...
call "%D8%" --lib "%PLATFORM%\android.jar" --output "%DEXDIR%" "%OBJ%\com\m3h\gesturenav\*.class"
if errorlevel 1 goto :error

"%AAPT2%" add "%BLDDIR%\base.apk" "%DEXDIR%\classes.dex"
if errorlevel 1 goto :error

rem Step 5: sign
echo [5/5] Signing APK...

if not exist "%BLDDIR%\debug.keystore" (
    "%JAVA_HOME%\bin\keytool.exe" -genkey -v -keystore "%BLDDIR%\debug.keystore" -alias debug -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug, OU=Dev, O=M3H, L=SZ, ST=GD, C=CN" -noprompt 2>nul
)

"%ZIPALIGN%" -p 4 "%BLDDIR%\base.apk" "%BLDDIR%\aligned.apk"
if errorlevel 1 goto :error

call "%APKSIGNER%" sign --ks "%BLDDIR%\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "%ROOT%\GestureNav.apk" "%BLDDIR%\aligned.apk"
if errorlevel 1 goto :error

echo.
echo =====================================
echo  BUILD SUCCESS
echo  APK: %ROOT%\GestureNav.apk
echo =====================================
goto :end

:error
echo.
echo =====================================
echo  BUILD FAILED
echo =====================================
endlocal
exit /b 1

:end
endlocal
exit /b 0
