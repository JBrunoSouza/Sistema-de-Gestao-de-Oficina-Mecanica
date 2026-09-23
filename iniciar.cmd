@echo off
setlocal
cd /d "%~dp0"
docker compose up -d
mvn clean javafx:run
if errorlevel 1 pause
