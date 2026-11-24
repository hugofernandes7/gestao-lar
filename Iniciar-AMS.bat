@echo off
rem Inicia o sistema AMS com um duplo clique: abre o Docker Desktop (se estiver fechado),
rem arranca a aplicacao e abre-a no browser. Os dados ficam guardados entre utilizacoes.
rem Nota: o texto fica sem acentos de proposito (a consola do Windows nao os mostra bem).
title AMS - Gestao do Lar
cd /d "%~dp0"

echo.
echo  ==========================================
echo    AMS - a iniciar o sistema. Aguarde...
echo    (pode demorar 1 a 3 minutos)
echo  ==========================================
echo.

rem 1. Docker Desktop: se nao estiver a correr, abre-o e espera ate estar pronto.
docker info >nul 2>&1
if not errorlevel 1 goto docker_pronto
echo [1/3] A abrir o Docker Desktop...
if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
    start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
) else (
    echo.
    echo  NAO FOI POSSIVEL ENCONTRAR O DOCKER DESKTOP.
    echo  Peca ajuda a quem instalou o sistema.
    pause
    exit /b 1
)
set /a tentativas=0
:esperar_docker
docker info >nul 2>&1
if not errorlevel 1 goto docker_pronto
set /a tentativas+=1
if %tentativas% GEQ 60 (
    echo.
    echo  O Docker Desktop nao arrancou a tempo. Feche esta janela e tente outra vez.
    pause
    exit /b 1
)
ping -n 4 127.0.0.1 >nul
goto esperar_docker

:docker_pronto
echo [1/3] Docker pronto.

rem 2. Aplicacao
echo [2/3] A arrancar a aplicacao...
docker compose up -d
if errorlevel 1 (
    echo.
    echo  NAO FOI POSSIVEL ARRANCAR A APLICACAO. Peca ajuda a quem instalou o sistema.
    pause
    exit /b 1
)

rem 3. Espera que a aplicacao responda e abre-a no browser
echo [3/3] A esperar que a aplicacao fique pronta...
set /a tentativas=0
:esperar_app
curl -s http://localhost:8080/actuator/health 2>nul | find "UP" >nul
if not errorlevel 1 goto app_pronta
set /a tentativas+=1
if %tentativas% GEQ 60 (
    echo.
    echo  A aplicacao demorou demasiado a arrancar. Espere um pouco e abra http://localhost:8080/tablet
    pause
    exit /b 1
)
ping -n 4 127.0.0.1 >nul
goto esperar_app

:app_pronta
echo.
echo  Pronto! A abrir o AMS...
start "" http://localhost:8080/tablet
ping -n 6 127.0.0.1 >nul
exit /b 0
