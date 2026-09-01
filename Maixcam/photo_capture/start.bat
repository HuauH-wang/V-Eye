@echo off
chcp 65001 >nul
REM MaixCAM ���� - �� PC �ϴ���Զ������
REM �÷�:
REM   start.bat        �ϴ������� Demo���������壩
REM   start.bat run    �ϴ���������ʽ���������崮�ڣ�
REM �޸��·� MAIX_IP

set MAIX_IP=192.168.4.1
set MAIX_USER=root
set REMOTE_DIR=/root/photo_capture
set LOCAL_DIR=%~dp0
set MODE=demo

if /I "%~1"=="run" set MODE=run
if /I "%~1"=="demo" set MODE=demo

echo ==========================================
echo   MaixCAM Photo Capture - Deploy
echo   Target: %MAIX_USER%@%MAIX_IP%:%REMOTE_DIR%
echo   Mode  : %MODE%
echo ==========================================

where scp >nul 2>&1
if errorlevel 1 (
    echo [ERROR] δ�ҵ� scp���밲װ OpenSSH �ͻ���
    pause
    exit /b 1
)

echo [1/3] �ϴ��� MaixCAM ...
scp -r "%LOCAL_DIR%*" %MAIX_USER%@%MAIX_IP%:%REMOTE_DIR%/
if errorlevel 1 (
    echo [ERROR] �ϴ�ʧ�ܡ���ȷ��:
    echo   - MaixCAM �ѿ������� PC ͬһ����
    echo   - IP ��ȷ���ȵ�ģʽĬ�� 192.168.4.1��
    echo   - �ѿ��� SSH
    pause
    exit /b 1
)

echo [2/3] ���ÿ�ִ��Ȩ�� ...
ssh %MAIX_USER%@%MAIX_IP% "chmod +x %REMOTE_DIR%/start.sh %REMOTE_DIR%/demo.sh"

echo [3/3] Զ������ (%MODE%) ...
if /I "%MODE%"=="demo" (
    ssh -t %MAIX_USER%@%MAIX_IP% "cd %REMOTE_DIR% && sh demo.sh"
) else (
    ssh -t %MAIX_USER%@%MAIX_IP% "cd %REMOTE_DIR% && sh start.sh"
)

pause
