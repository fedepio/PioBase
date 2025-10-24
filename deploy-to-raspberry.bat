@echo off
REM Script per compilare il progetto localmente e trasferirlo al Raspberry Pi
REM Autore: PioSoft
REM Data: 2025-10-21

echo ========================================
echo Deploy PioBase sul Raspberry Pi
echo ========================================
echo.

REM Configurazione
set RASPBERRY_USER=raspberry
set RASPBERRY_HOST=100.116.8.49
set RASPBERRY_PASSWORD=fedepio1
set RASPBERRY_PATH=/home/raspberry/Desktop/PioHomeDeluxeServer/PioBase
set LOCAL_PROJECT=C:\Users\feder\Desktop\Progetti\PioBase
set JAR_NAME=PioBase-0.6.5-BETA.jar

REM Configurazione Java
set JAVA_HOME=C:\Users\feder\.jdks\openjdk-22.0.2
set PATH=%JAVA_HOME%\bin;%PATH%

REM Verifica che Java sia disponibile
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERRORE: Java non trovato in %JAVA_HOME%
    pause
    exit /b 1
)

echo Usando Java: %JAVA_HOME%
echo.

echo [1/4] Pulizia build precedenti...
cd "%LOCAL_PROJECT%"
call "%LOCAL_PROJECT%\mvnw.cmd" clean
if errorlevel 1 (
    echo ERRORE: Pulizia fallita!
    pause
    exit /b 1
)

echo.
echo [2/4] Compilazione del progetto...
call "%LOCAL_PROJECT%\mvnw.cmd" package -DskipTests
if errorlevel 1 (
    echo ERRORE: Compilazione fallita!
    pause
    exit /b 1
)

echo.
echo [3/4] Verifica del JAR compilato...
if not exist "target\%JAR_NAME%" (
    echo ERRORE: JAR non trovato in target\%JAR_NAME%
    pause
    exit /b 1
)

echo.
echo [4/4] Trasferimento al Raspberry Pi...
scp -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no "target\%JAR_NAME%" "%RASPBERRY_USER%@%RASPBERRY_HOST%:%RASPBERRY_PATH%/target/"
if errorlevel 1 (
    echo ERRORE: Trasferimento fallito!
    echo Assicurati che:
    echo - Il Raspberry Pi sia acceso e raggiungibile
    echo - SSH sia configurato correttamente
    echo - Hai i permessi di scrittura sulla directory di destinazione
    pause
    exit /b 1
)

echo.
echo ========================================
echo Deploy completato con successo!
echo ========================================
echo JAR disponibile su: %RASPBERRY_PATH%/target/%JAR_NAME%
echo.
echo Vuoi riavviare il servizio myapp sul Raspberry Pi? (S/N)
set /p RESTART_SERVICE="> "
if /i "%RESTART_SERVICE%"=="S" (
    echo.
    echo Riavvio del servizio myapp...
    ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no "%RASPBERRY_USER%@%RASPBERRY_HOST%" "echo %RASPBERRY_PASSWORD% | sudo -S systemctl restart myapp"
    if errorlevel 1 (
        echo ATTENZIONE: Riavvio servizio fallito!
    ) else (
        echo Servizio riavviato con successo!
        timeout /t 2 >nul
        echo.
        echo Stato del servizio:
        ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no "%RASPBERRY_USER%@%RASPBERRY_HOST%" "sudo systemctl status myapp --no-pager -l"
    )
) else (
    echo.
    echo Per avviare/riavviare manualmente l'applicazione sul Raspberry Pi:
    echo ssh %RASPBERRY_USER%@%RASPBERRY_HOST%
    echo sudo systemctl restart myapp
    echo.
    echo Oppure esegui direttamente:
    echo cd %RASPBERRY_PATH%
    echo java -Xmx256m -jar target/%JAR_NAME%
)
echo.
pause
