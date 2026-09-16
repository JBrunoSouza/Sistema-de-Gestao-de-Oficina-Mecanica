@echo off
setlocal
cd /d "%~dp0"
if not exist "target\engrenar-1.0.0.jar" (
  echo Execute mvn clean package antes de iniciar.
  pause
  exit /b 1
)
java -jar "target\engrenar-1.0.0.jar"
if errorlevel 1 pause
