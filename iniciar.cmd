@echo off
setlocal
cd /d "%~dp0"
docker compose up -d --wait --wait-timeout 90
if errorlevel 1 (
    echo Nao foi possivel iniciar o PostgreSQL. Verifique o Docker Desktop.
    pause
    exit /b 1
)
call mvn clean javafx:run
if errorlevel 1 pause
