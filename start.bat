@echo off
chcp 65001 >nul 2>&1

:: AzurLaneServer 启动脚本
:: 自动添加 Java 17+ 所需的 JVM 参数

set JAVA_OPTS=--enable-native-access=ALL-UNNAMED

:: 检查 Java 版本
java -version 2>&1 | findstr /i "version" >nul
if errorlevel 1 (
    echo [ERROR] Java not found. Please install JDK 17+.
    pause
    exit /b 1
)

echo Starting AzurLaneServer...
java %JAVA_OPTS% -jar server.jar %*
