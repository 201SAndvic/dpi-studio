@echo off
chcp 65001 >nul
setlocal

rem ---- 环境（这台机器的固定路径）----
set "SDL=C:\Users\Admin\AppData\Local\Android\Sdk"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "ANDROID_HOME=%SDL%"
set "ANDROID_SDK_ROOT=%SDL%"
set "ANDROID_USER_HOME=C:\Users\Admin\.android"
set "PATH=%SDL%\platform-tools;%PATH%"

cd /d "%~dp0.."

echo.
echo   [1/3] 编译...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo   编译失败，请把上面的报错发我。
    pause
    exit /b 1
)

echo.
echo   [2/3] 安装到模拟器...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

echo.
echo   [3/3] 启动应用...
adb shell am start -n com.appconfig.injector/.MainActivity

echo.
echo   完成。窗口里就是 DPI 工坊。
pause
endlocal
