@echo off
chcp 65001 > nul
echo ====================================================
echo   CREANDO ACCESO DIRECTO OFICIAL DE NEOMESSAGE PC
echo ====================================================

set SCRIPT="%TEMP%\CreateShortcut.vbs"
set TARGET="%~dp0NeoMessage-Desktop.html"
set ICON="%~dp0NeoMessage.ico"
set SHORTCUT="%USERPROFILE%\Desktop\NeoMessage.lnk"

echo Set oWS = WScript.CreateObject("WScript.Shell") > %SCRIPT%
echo sLinkFile = "%SHORTCUT%" >> %SCRIPT%
echo Set oLink = oWS.CreateShortcut(sLinkFile) >> %SCRIPT%
echo oLink.TargetPath = %TARGET% >> %SCRIPT%
echo oLink.IconLocation = %ICON% >> %SCRIPT%
echo oLink.Description = "NeoMessage Soberana PC" >> %SCRIPT%
echo oLink.WorkingDirectory = "%~dp0" >> %SCRIPT%
echo oLink.Save >> %SCRIPT%

cscript /nologo %SCRIPT%
del %SCRIPT%

echo [OK] ¡Acceso directo creado en tu Escritorio con el icono oficial 3D Cyber Neon!
pause
