@echo off
echo ==========================================
echo    REPARADOR MAESTRO DE NEXUS (REBORN)
echo ==========================================
echo Cerrando procesos de Gradle...
taskkill /f /im java.exe >nul 2>&1
echo Borrando archivos duplicados (.kts) que causan conflicto...
del /f /q "build.gradle.kts"
del /f /q "settings.gradle.kts"
del /f /q "app\build.gradle.kts"
del /f /q "gradle\libs.versions.toml"
echo Limpiando carpetas de cache corruptas...
rd /s /q ".gradle"
rd /s /q ".idea"
rd /s /q "app\build"
echo ==========================================
echo LIMPIEZA COMPLETADA. 
echo Ahora abre Android Studio y dale a "Sync Project".
echo ==========================================
pause
