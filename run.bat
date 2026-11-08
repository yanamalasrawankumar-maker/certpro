@echo off
rem ============================================================
rem  CertChain - one-click run file.
rem  Keep this file, Core.java, Engine.java, Screens.java,
rem  CertChainApp.java and style.css together in ONE folder.
rem  Double-click run.bat. Missing jar files are downloaded once.
rem ============================================================
setlocal
set JAVAFX_LIB=C:\javafx

rem ---- E-mail OTP (optional). Remove "rem" from the next 2 lines and fill them in to send real OTP e-mails.
rem Gmail: turn on 2-step verification, then create an "App password" at myaccount.google.com/apppasswords
set CERT_SMTP_USER=certpro6@gmail.com
set CERT_SMTP_PASS=PUT_APP_PASSWORD_HERE
rem Replace PUT_APP_PASSWORD_HERE with the 16-letter app password of certpro6@gmail.com (no spaces).
rem Without these two lines the app runs in DEMO MODE and shows the OTP on the screen.
cd /d "%~dp0"

where javac >nul 2>nul || (echo [!] JDK not found. Install JDK 17 or newer from adoptium.net, then run again. & pause & exit /b 1)
where curl >nul 2>nul || (echo [!] curl not found. Use Windows 10 or newer. & pause & exit /b 1)

if not exist "%JAVAFX_LIB%" mkdir "%JAVAFX_LIB%"
set MVN=https://repo1.maven.org/maven2
if not exist "%JAVAFX_LIB%\javafx-base-21.0.5-win.jar"     curl -L -o "%JAVAFX_LIB%\javafx-base-21.0.5-win.jar"     %MVN%/org/openjfx/javafx-base/21.0.5/javafx-base-21.0.5-win.jar
if not exist "%JAVAFX_LIB%\javafx-graphics-21.0.5-win.jar" curl -L -o "%JAVAFX_LIB%\javafx-graphics-21.0.5-win.jar" %MVN%/org/openjfx/javafx-graphics/21.0.5/javafx-graphics-21.0.5-win.jar
if not exist "%JAVAFX_LIB%\javafx-controls-21.0.5-win.jar" curl -L -o "%JAVAFX_LIB%\javafx-controls-21.0.5-win.jar" %MVN%/org/openjfx/javafx-controls/21.0.5/javafx-controls-21.0.5-win.jar
if not exist sqlite-jdbc.jar curl -L -o sqlite-jdbc.jar %MVN%/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar
if not exist slf4j-api.jar   curl -L -o slf4j-api.jar   %MVN%/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar

for %%F in (Core.java Engine.java Screens.java CertChainApp.java style.css) do if not exist %%F (echo [!] %%F is missing in this folder & pause & exit /b 1)

echo Compiling...
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 --module-path "%JAVAFX_LIB%" --add-modules javafx.controls -cp "sqlite-jdbc.jar;slf4j-api.jar" -d out Core.java Engine.java Screens.java CertChainApp.java
if errorlevel 1 (echo [!] Compile failed. Check that all 4 .java files are the newest versions. & pause & exit /b 1)
mkdir out\certchain\ui
copy /y style.css out\certchain\ui\ >nul

echo Starting CertChain...
java --module-path "%JAVAFX_LIB%" --add-modules javafx.controls -cp "out;sqlite-jdbc.jar;slf4j-api.jar" certchain.ui.CertChainApp
if errorlevel 1 pause
endlocal
