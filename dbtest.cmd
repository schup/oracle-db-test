@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%app\build\libs\oracle-jdbc-test-1.0.0.jar"

if not exist "%JAR_PATH%" (
    echo Error: JAR not found at %JAR_PATH% >&2
    echo Run 'gradlew shadowJar' to build. >&2
    exit /b 2
)

java -jar "%JAR_PATH%" %*
