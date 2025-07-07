@echo off
setlocal enabledelayedexpansion

echo [INFO] Starting handler-deploy.bat...

REM Get current UTC datetime
for /f "tokens=1 delims=." %%a in ('wmic os get localdatetime ^| findstr /r /c:"^[0-9]"') do set dt=%%a
set ZIPNAME=ActionHandler-%dt:~0,12%.zip
echo [INFO] Generated ZIPNAME from datetime: %ZIPNAME%

REM Rename ActionHandler.zip if it exists
if exist GameFunctions\ActionHandler.zip (
    echo [INFO] Found ActionHandler.zip. Renaming to %ZIPNAME%
    move /Y GameFunctions\ActionHandler.zip %ZIPNAME%
) else (
    echo [INFO] ActionHandler.zip not found. Searching for existing versioned ZIP...
    for %%f in (ActionHandler-*.zip) do (
        echo [INFO] Found existing ZIP: %%f
        set ZIPNAME=%%f
    )
)

REM Check if ZIPNAME was set
if not defined ZIPNAME (
    echo [ERROR] No ZIP file found. Aborting.
    exit /b 1
)

REM Final ZIP name used for deployment
echo [INFO] Using ZIP for deployment: !ZIPNAME!

REM Delete other ZIPs
echo [INFO] Cleaning up other ZIP files...
for %%f in (ActionHandler-*.zip) do (
    if /I not "%%f"=="!ZIPNAME!" (
        echo [INFO] Deleting: %%f
        del /F /Q "%%f"
    )
)

REM Patch the original template.yaml in-place
echo [INFO] Injecting ZIP name into template.yaml...
powershell -Command "(Get-Content template.yaml) -replace 'CodeUri: ActionHandler.*\.zip', 'CodeUri: !ZIPNAME!' | Set-Content template.yaml"


REM Deploy with SAM
echo [INFO] Deploying with SAM using ZIP: !ZIPNAME!
sam deploy --s3-bucket gamesbe --s3-prefix functions --capabilities CAPABILITY_IAM --force-upload --no-confirm-changeset

endlocal
