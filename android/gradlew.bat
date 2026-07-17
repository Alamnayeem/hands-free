@rem ------------------------------------------------------------------------
@rem Gradle Startup Script for Windows
@rem ------------------------------------------------------------------------

@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar

@rem Determine the Java command to use
set JAVA_EXE=java.exe
if not "%JAVA_HOME%" == "" set JAVA_EXE="%JAVA_HOME%\bin\java.exe"

%JAVA_EXE% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
