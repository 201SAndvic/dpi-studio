@echo off
chcp 65001 >nul
setlocal

rem ---- 环境（这台机器的固定路径）----
set "SDL=C:\Users\Admin\AppData\Local\Android\Sdk"
set "ANDROID_HOME=%SDL%"
set "ANDROID_SDK_ROOT=%SDL%"
set "ANDROID_USER_HOME=C:\Users\Admin\.android"
set "PATH=%SDL%\platform-tools;%SDL%\emulator;%PATH%"

echo.
echo   可用的模拟器：
"%SDL%\emulator\emulator.exe" -list-avds
echo.
echo   正在启动 phone（带窗口）...
echo   想用别的就改下面这行的 phone，比如 car1920x1440
echo.

start "" "%SDL%\emulator\emulator.exe" -avd phone -no-audio -gpu auto
endlocal
