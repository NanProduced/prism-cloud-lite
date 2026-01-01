@echo off
setlocal

set "BASEDIR=%~dp0"
if "%BASEDIR:~-1%"=="\" set "BASEDIR=%BASEDIR:~0,-1%"
set "WRAPPER_DIR=%BASEDIR%\.mvn\wrapper"
set "JAR=%WRAPPER_DIR%\maven-wrapper.jar"

if not exist "%JAR%" (
  echo maven-wrapper.jar is missing: %JAR%
  exit /b 1
)

set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_CMD%" (
  set "JAVA_CMD=java"
)

"%JAVA_CMD%" -classpath "%JAR%" "-Dmaven.multiModuleProjectDirectory=%BASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
