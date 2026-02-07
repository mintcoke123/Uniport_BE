@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ====== .env 로드 (같은 폴더 기준) ======
set "ENV_FILE=%~dp0.env"
if exist "%ENV_FILE%" (
  for /f "usebackq tokens=* delims=" %%A in ("%ENV_FILE%") do (
    set "LINE=%%A"
    if not "!LINE!"=="" if "!LINE:~0,1!" NEQ "#" (
      for /f "tokens=1* delims==" %%K in ("!LINE!") do (
        set "%%K=%%L"
      )
    )
  )
) else (
  echo [env] .env not found: %ENV_FILE%
)

REM ====== 확인(원하면) ======
REM echo SPRING_DATASOURCE_URL=%SPRING_DATASOURCE_URL%
REM echo KIS_API_APPKEY=%KIS_API_APPKEY%

REM ====== 기존 실행 ======
chcp 65001 >nul
call gradlew.bat bootRun
endlocal
