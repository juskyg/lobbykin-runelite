@echo off
setlocal
cd /d "%~dp0"
set "LOBBYKIN_JDK="
call :checkJdk "%JAVA17_HOME%"
if not defined LOBBYKIN_JDK call :checkJdk "%JDK17_HOME%"
if not defined LOBBYKIN_JDK call :checkJdk "%JAVA_HOME%"
for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Microsoft\jdk-17*" "%ProgramFiles%\Amazon Corretto\jdk17*" "%ProgramFiles%\Zulu\zulu17*" "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17*" "%USERPROFILE%\.jdks\*") do if not defined LOBBYKIN_JDK call :checkJdk "%%~fJ"
for /f "delims=" %%J in ('where javac.exe 2^>nul') do if not defined LOBBYKIN_JDK call :checkJdk "%%~dpJ.."
if not defined LOBBYKIN_JDK (
 echo Lobbykin needs a full JDK 17. Java 11 or 25 will not work with this launcher.
 echo Install Temurin JDK 17 for Windows x64 from https://adoptium.net/temurin/releases/?version=17
 echo If installed elsewhere, set JAVA17_HOME to its folder and start again.
 pause
 exit /b 1
)
set "JAVA_HOME=%LOBBYKIN_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JDK 17: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
call gradlew.bat "-Dorg.gradle.java.home=%JAVA_HOME%" run
set "LOBBYKIN_EXIT=%ERRORLEVEL%"
if not "%LOBBYKIN_EXIT%"=="0" pause
exit /b %LOBBYKIN_EXIT%

:checkJdk
if "%~1"=="" exit /b
if not exist "%~1\bin\java.exe" exit /b
if not exist "%~1\bin\javac.exe" exit /b
if not exist "%~1\release" exit /b
findstr /b /c:"JAVA_VERSION=.17\." "%~1\release" >nul 2>nul
if errorlevel 1 exit /b
for %%P in ("%~1") do set "LOBBYKIN_JDK=%%~fP"
exit /b
