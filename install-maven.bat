@echo off
echo Installing Apache Maven...
echo.

REM Download Maven using PowerShell (single command, no script execution needed)
powershell -Command "& {Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\maven.zip'}"

echo Download completed!
echo.

REM Extract using PowerShell
echo Extracting Maven...
powershell -Command "& {Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%' -Force}"
if exist "%USERPROFILE%\maven" rmdir /s /q "%USERPROFILE%\maven"
rename "%USERPROFILE%\apache-maven-3.9.6" maven

echo.
echo Maven extracted to %USERPROFILE%\maven
echo.

REM Add to PATH permanently
echo Adding Maven to system PATH...
setx M2_HOME "%USERPROFILE%\maven"
setx PATH "%PATH%;%USERPROFILE%\maven\bin"

echo.
echo Maven installed successfully!
echo.
echo IMPORTANT: Close this terminal and open a NEW terminal, then run:
echo   mvn --version
echo.
pause
