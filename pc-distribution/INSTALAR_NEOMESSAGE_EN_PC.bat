@echo off
chcp 65001 > nul
cls
echo =====================================================================
echo           INSTALADOR OFICIAL DE NEOMESSAGE PARA WINDOWS PC
echo =====================================================================
echo.
echo Instalando NeoMessage con icono oficial en tu Escritorio y Menu Inicio...
echo.

set CURRENT_DIR=%~dp0
set TARGET_HTML=%CURRENT_DIR%NeoMessage-Desktop.html
set ICON_FILE=%CURRENT_DIR%NeoMessage.ico
set DESKTOP_LNK=%USERPROFILE%\Desktop\NeoMessage.lnk
set STARTMENU_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs
set STARTMENU_LNK=%STARTMENU_DIR%\NeoMessage.lnk

:: Crear script VBS temporal para generar los accesos directos
set VBS_FILE=%TEMP%\InstallNeoMessage.vbs
echo Set oWS = WScript.CreateObject("WScript.Shell") > "%VBS_FILE%"

:: 1. Acceso directo en el Escritorio
echo Set oLink = oWS.CreateShortcut("%DESKTOP_LNK%") >> "%VBS_FILE%"
echo oLink.TargetPath = "%TARGET_HTML%" >> "%VBS_FILE%"
echo oLink.IconLocation = "%ICON_FILE%,0" >> "%VBS_FILE%"
echo oLink.Description = "NeoMessage Soberana PC" >> "%VBS_FILE%"
echo oLink.WorkingDirectory = "%CURRENT_DIR%" >> "%VBS_FILE%"
echo oLink.Save >> "%VBS_FILE%"

:: 2. Acceso directo en el Menu Inicio de Windows
echo Set oStartLink = oWS.CreateShortcut("%STARTMENU_LNK%") >> "%VBS_FILE%"
echo oStartLink.TargetPath = "%TARGET_HTML%" >> "%VBS_FILE%"
echo oStartLink.IconLocation = "%ICON_FILE%,0" >> "%VBS_FILE%"
echo oStartLink.Description = "NeoMessage Soberana PC" >> "%VBS_FILE%"
echo oStartLink.WorkingDirectory = "%CURRENT_DIR%" >> "%VBS_FILE%"
echo oStartLink.Save >> "%VBS_FILE%"

:: Ejecutar VBS
cscript /nologo "%VBS_FILE%"
if exist "%VBS_FILE%" del "%VBS_FILE%"

echo =====================================================================
echo  [EXITO] ¡NeoMessage se instalo correctamente con su icono oficial!
echo  1. Icono agregado en tu Escritorio de Windows.
echo  2. Icono agregado en tu Menu Inicio de Windows.
echo =====================================================================
echo.
echo Iniciando NeoMessage...
start "" "%DESKTOP_LNK%"
timeout /t 3 > nul
exit
