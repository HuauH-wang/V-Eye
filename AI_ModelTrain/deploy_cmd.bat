@echo off
chcp 65001 >nul
set STEDGEAI=D:\STEdge\4.0\Utilities\windows\stedgeai.exe
set TFLITE=D:\嵌赛\AI_ModelTrain\output_model\cmd_int8.tflite
set OUT=D:\嵌赛\AI_ModelTrain\deploy_temp\cmd
set AI=D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\Firmware\STM32CubeU5\Projects\NUCLEO-U575ZI-Q\Examples\GPIO\GPIO_IOToggle\AI

if not exist "%STEDGEAI%" (echo ERROR: STEdgeAI not found & exit /b 1)
if not exist "%TFLITE%" (echo ERROR: %TFLITE% not found & exit /b 1)

mkdir "%OUT%" 2>nul
"%STEDGEAI%" generate --target stm32u5 --type tflite --model "%TFLITE%" --name network_cmd --output "%OUT%" --allocate-inputs --allocate-outputs --verbosity 1
if errorlevel 1 exit /b 1

copy /Y "%OUT%\network_cmd.c" "%OUT%\network_cmd.h" "%OUT%\network_cmd_data.c" "%OUT%\network_cmd_data.h" "%OUT%\network_cmd_details.h" "%AI%\"
echo Done: cmd model -> %AI%
pause
