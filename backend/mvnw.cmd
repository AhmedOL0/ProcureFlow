@REM Minimal Maven Wrapper launcher — run from the backend\ directory.
@REM Resolves the Maven distribution declared in .mvn\wrapper\maven-wrapper.properties
@REM and caches it under %USERPROFILE%\.m2\wrapper on first run (network required once).
@echo off
set "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo error: Maven wrapper jar not found at %WRAPPER_JAR% 1>&2
  exit /b 1
)
java "-Dmaven.multiModuleProjectDirectory=%~dp0." -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
