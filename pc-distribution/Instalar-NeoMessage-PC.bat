@echo off
chcp 65001 >nul
title Instalador NeoMessage Desktop para Windows
color 0A

echo =======================================================
echo          INSTALADOR NEOMESSAGE DESKTOP
echo =======================================================
echo.
echo [1/3] Preparando directorio de aplicacion en tu PC...
set "APP_DIR=%USERPROFILE%\AppData\Local\NeoMessage"
if not exist "%APP_DIR%" mkdir "%APP_DIR%"

echo [2/3] Copiando archivos del cliente de escritorio...
copy /Y "%~dp0NeoMessage-Desktop.html" "%APP_DIR%\NeoMessage-Desktop.html" >nul

echo [3/3] Creando acceso directo en el Escritorio de Windows...
set "TARGET_HTML=%APP_DIR%\NeoMessage-Desktop.html"
set "SHORTCUT_PATH=%USERPROFILE%\Desktop\NeoMessage Desktop.lnk"

powershell -Command "$WshShell = New-Object -comObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%SHORTCUT_PATH%'); $EdgePath = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'; if (-not (Test-Path $EdgePath)) { $EdgePath = 'C:\Program Files\Microsoft\Edge\Application\msedge.exe' }; $Shortcut.TargetPath = $EdgePath; $Shortcut.Arguments = '--app=\"file:///' + ('%TARGET_HTML%'.Replace('\', '/')) + '\"'; $Shortcut.Description = 'NeoMessage Desktop'; $Shortcut.Save()"

echo.
echo =======================================================
echo    INSTALACION COMPLETADA EXITOSAMENTE!
echo    Se ha creado el icono en tu Escritorio:
echo    "NeoMessage Desktop"
echo =======================================================
echo.
echo Abriendo NeoMessage Desktop ahora mismo...
start "" "%SHORTCUT_PATH%"
exit /b 0
