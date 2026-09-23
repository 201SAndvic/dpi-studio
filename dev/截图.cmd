@echo off
chcp 65001 >nul
setlocal
set "SDL=C:\Users\Admin\AppData\Local\Android\Sdk"
set "PATH=%SDL%\platform-tools;%PATH%"

cd /d "%~dp0.."
if not exist "shots" mkdir "shots"
for /f "tokens=1-4 delims=/:. " %%a in ("%date% %time%") do set "STAMP=%%a%%b%%c_%%d"

adb exec-out screencap -p > "shots\emu_%STAMP%.png"
echo   已保存：shots\emu_%STAMP%.png
pause
endlocal
