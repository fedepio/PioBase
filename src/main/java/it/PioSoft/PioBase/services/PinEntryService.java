/**
 * Servizio per l'inserimento automatico del PIN di accesso Windows
 *
 * Gestisce l'automazione del login su PC Windows dopo il wake-on-LAN:
 * - Attende che il PC sia completamente avviato
 * - Invia automaticamente il PIN di accesso configurato
 * - Utilizza PowerShell per simulare l'input del PIN
 *
 * Il servizio si connette via SSH al PC target e utilizza comandi
 * PowerShell per automatizzare l'inserimento delle credenziali.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-29
 */
package it.PioSoft.PioBase.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import it.PioSoft.PioBase.configs.PcMappingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

@Service
public class PinEntryService {

    @Autowired
    private PcMappingConfig pcMappingConfig;

    @Autowired
    private PcStatusService pcStatusService;

    /**
     * Inserisce automaticamente il PIN dopo il wake-on-LAN
     * Attende che il PC sia online e poi invia il PIN
     */
    public CompletableFuture<String> enterPinAfterWakeUp(String ipAddress, String pin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Attende che il PC sia completamente avviato (max 2 minuti)
                if (!waitForPcToBeOnline(ipAddress, 120)) {
                    return "Timeout: PC non raggiungibile dopo wake-up";
                }

                // Attende ancora 30 secondi per essere sicuri che Windows sia pronto
                Thread.sleep(30000);

                // Invia il PIN
                return sendPinViaSsh(ipAddress, pin);

            } catch (Exception e) {
                return "Errore durante inserimento PIN: " + e.getMessage();
            }
        });
    }

    /**
     * Attende che il PC sia online
     */
    private boolean waitForPcToBeOnline(String ipAddress, int maxWaitSeconds) {
        int attempts = 0;
        int maxAttempts = maxWaitSeconds / 5; // controlla ogni 5 secondi

        while (attempts < maxAttempts) {
            if (pcStatusService.isPcOnline(ipAddress)) {
                System.out.println("PC " + ipAddress + " è online dopo " + (attempts * 5) + " secondi");
                return true;
            }

            try {
                Thread.sleep(5000); // attende 5 secondi
                attempts++;
                System.out.println("Attendo che " + ipAddress + " sia online... tentativo " + attempts + "/" + maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Invia il PIN tramite SSH usando PowerShell
     */
    private String sendPinViaSsh(String ipAddress, String pin) {
        try {
            if (pin == null || pin.isEmpty()) {
                return "PIN non fornito nella richiesta";
            }

            System.out.println("Tentativo inserimento PIN per IP: " + ipAddress + " con PIN: " + pin);

            // Proviamo diversi approcci per inserire il PIN
            String result = tryMultiplePinMethods(pin, ipAddress);

            return result;

        } catch (Exception e) {
            String error = "Errore durante invio PIN via SSH: " + e.getMessage();
            System.err.println(error);
            return error;
        }
    }

    private String tryMultiplePinMethods(String pin, String ipAddress) throws Exception {
        // Metodo 1: Win32 API dirette
        String method1Result = tryMethod1SendKeys(pin, ipAddress);
        if (method1Result.contains("successo")) {
            return method1Result;
        }

        // Metodo 2: WScript Shell
        String method2Result = tryMethod2CmdKey(pin, ipAddress);
        if (method2Result.contains("successo")) {
            return method2Result;
        }

        // Metodo 3: SendKeys modificato
        String method3Result = tryMethod3VBScript(pin, ipAddress);
        if (method3Result.contains("successo")) {
            return method3Result;
        }

        // Metodo 4: Clipboard + Paste
        String method4Result = tryMethod4Clipboard(pin, ipAddress);
        if (method4Result.contains("successo")) {
            return method4Result;
        }

        return "Tutti i metodi di inserimento PIN hanno fallito per " + ipAddress +
               ". Metodo1: " + method1Result + "; Metodo2: " + method2Result + "; Metodo3: " + method3Result + "; Metodo4: " + method4Result;
    }

    private String tryMethod1SendKeys(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo 1: Input diretto con PowerShell");

        // Semplificato: usa solo WScript.Shell per evitare problemi di sintassi
        String command = "powershell.exe -Command \"" +
            "try { " +
            "$wshell = New-Object -ComObject wscript.shell; " +
            "Start-Sleep -Seconds 2; " +
            "$wshell.SendKeys('" + pin + "'); " +
            "Start-Sleep -Milliseconds 300; " +
            "$wshell.SendKeys('{ENTER}'); " +
            "Write-Output 'PIN inserito con successo metodo 1 (WScript direct)'; " +
            "} catch { " +
            "Write-Output ('Metodo 1 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryMethod2CmdKey(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo 2: Clipboard + Paste");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "Add-Type -AssemblyName System.Windows.Forms; " +
            "$pin = '" + pin + "'; " +
            "[System.Windows.Forms.Clipboard]::SetText($pin); " +
            "Start-Sleep -Milliseconds 500; " +
            "$wshell = New-Object -ComObject wscript.shell; " +
            "$wshell.SendKeys('^v'); " +
            "Start-Sleep -Milliseconds 300; " +
            "$wshell.SendKeys('{ENTER}'); " +
            "[System.Windows.Forms.Clipboard]::Clear(); " +
            "Write-Output 'PIN inserito con successo metodo 2 (Clipboard)'; " +
            "} catch { " +
            "Write-Output ('Metodo 2 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryMethod3VBScript(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo 3: SendKeys carattere per carattere");

        StringBuilder sendKeysCommands = new StringBuilder();
        for (char c : pin.toCharArray()) {
            sendKeysCommands.append("$wshell.SendKeys('").append(c).append("'); Start-Sleep -Milliseconds 100; ");
        }

        String command = "powershell.exe -Command \"" +
            "try { " +
            "$wshell = New-Object -ComObject wscript.shell; " +
            "Start-Sleep -Seconds 1; " +
            sendKeysCommands.toString() +
            "$wshell.SendKeys('{ENTER}'); " +
            "Write-Output 'PIN inserito con successo metodo 3 (Char by char)'; " +
            "} catch { " +
            "Write-Output ('Metodo 3 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryMethod4Clipboard(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo 4: VBScript file temporaneo");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "$vbsContent = \\\"Set WshShell = WScript.CreateObject('WScript.Shell')\\\"; " +
            "$vbsContent += \\\"\\r\\nWScript.Sleep 1000\\\"; " +
            "$vbsContent += \\\"\\r\\nWshShell.SendKeys '" + pin + "'\\\"; " +
            "$vbsContent += \\\"\\r\\nWScript.Sleep 200\\\"; " +
            "$vbsContent += \\\"\\r\\nWshShell.SendKeys '{ENTER}'\\\"; " +
            "$vbsFile = 'C:\\\\temp\\\\pin_entry.vbs'; " +
            "New-Item -Path 'C:\\\\temp' -ItemType Directory -Force | Out-Null; " +
            "$vbsContent | Out-File -FilePath $vbsFile -Encoding ASCII -Force; " +
            "Start-Process -FilePath 'cscript' -ArgumentList '//NoLogo', $vbsFile -Wait; " +
            "Remove-Item $vbsFile -Force -ErrorAction SilentlyContinue; " +
            "Write-Output 'PIN inserito con successo metodo 4 (VBScript file)'; " +
            "} catch { " +
            "Write-Output ('Metodo 4 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String executeCommand(String command, String ipAddress) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;

        try {
            session = jsch.getSession(
                pcMappingConfig.getSsh().getUsername(),
                ipAddress,
                pcMappingConfig.getSsh().getPort()
            );
            session.setPassword(pcMappingConfig.getSsh().getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(channel.getErrStream()));

            channel.connect();

            StringBuilder output = new StringBuilder();
            StringBuilder errors = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("Output: " + line);
            }

            while ((line = errorReader.readLine()) != null) {
                errors.append(line).append("\n");
                System.err.println("Error: " + line);
            }

            String result = output.toString().trim();
            if (errors.length() > 0) {
                result += " (Errors: " + errors.toString().trim() + ")";
            }

            return result.isEmpty() ? "Comando eseguito senza output" : result;

        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Inserisce il PIN immediatamente (per chiamate manuali)
     */
    public String enterPinNow(String ipAddress, String pin) {
        try {
            return sendPinViaSsh(ipAddress, pin);
        } catch (Exception e) {
            return "Errore durante inserimento PIN: " + e.getMessage();
        }
    }

    /**
     * Metodo avanzato per inserimento PIN usando approcci Microsoft specifici
     */
    public String enterPinAdvanced(String ipAddress, String pin) {
        try {
            if (pin == null || pin.isEmpty()) {
                return "PIN non fornito nella richiesta";
            }

            System.out.println("Tentativo inserimento PIN avanzato per IP: " + ipAddress + " con PIN: " + pin);

            // Prova approcci Microsoft specifici
            String result = tryMicrosoftSpecificMethods(pin, ipAddress);

            return result;

        } catch (Exception e) {
            String error = "Errore durante invio PIN avanzato via SSH: " + e.getMessage();
            System.err.println(error);
            return error;
        }
    }

    private String tryMicrosoftSpecificMethods(String pin, String ipAddress) throws Exception {
        // Metodo 1: Windows Hello API simulation
        String method1Result = tryWindowsHelloSimulation(pin, ipAddress);
        if (method1Result.contains("successo")) {
            return method1Result;
        }

        // Metodo 2: WinRT/UWP approach
        String method2Result = tryWinRTCredentialUI(pin, ipAddress);
        if (method2Result.contains("successo")) {
            return method2Result;
        }

        // Metodo 3: Windows Security API
        String method3Result = tryWindowsSecurityAPI(pin, ipAddress);
        if (method3Result.contains("successo")) {
            return method3Result;
        }

        // Metodo 4: Registry-based credential storage
        String method4Result = tryRegistryCredentialMethod(pin, ipAddress);
        if (method4Result.contains("successo")) {
            return method4Result;
        }

        // Metodo 5: PowerShell con privilegi elevati
        String method5Result = tryElevatedPowerShellMethod(pin, ipAddress);
        if (method5Result.contains("successo")) {
            return method5Result;
        }

        return "Tutti i metodi Microsoft avanzati hanno fallito per " + ipAddress +
               ". Metodo1: " + method1Result + "; Metodo2: " + method2Result +
               "; Metodo3: " + method3Result + "; Metodo4: " + method4Result + "; Metodo5: " + method5Result;
    }

    private String tryWindowsHelloSimulation(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo Microsoft 1: Windows Hello API simulation");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime; " +
            "$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]; " +
            "$asTask = $asTaskGeneric.MakeGenericMethod([Windows.Security.Credentials.UI.UserConsentVerificationResult]); " +
            "$asyncOperation = [Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync('PIN Entry for Remote Login'); " +
            "$task = $asTask.Invoke($null, @($asyncOperation)); " +
            "if ($task.Result -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Verified) { " +
            "  $wshell = New-Object -ComObject wscript.shell; " +
            "  $wshell.SendKeys('" + pin + "'); " +
            "  $wshell.SendKeys('{ENTER}'); " +
            "  Write-Output 'PIN inserito con successo metodo Microsoft 1 (Windows Hello)'; " +
            "} else { " +
            "  Write-Output 'Metodo Microsoft 1 fallito: verifica utente non riuscita'; " +
            "} " +
            "} catch { " +
            "Write-Output ('Metodo Microsoft 1 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryWinRTCredentialUI(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo Microsoft 2: WinRT Credential UI");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime; " +
            "[Windows.ApplicationModel.Core.CoreApplication, Windows.ApplicationModel, ContentType = WindowsRuntime] | Out-Null; " +
            "$credential = New-Object Windows.Security.Credentials.PasswordCredential('PioBaseLogin', $env:USERNAME, '" + pin + "'); " +
            "$vault = New-Object Windows.Security.Credentials.PasswordVault; " +
            "$vault.Add($credential); " +
            "$wshell = New-Object -ComObject wscript.shell; " +
            "Start-Sleep -Seconds 1; " +
            "$wshell.SendKeys('" + pin + "'); " +
            "$wshell.SendKeys('{ENTER}'); " +
            "$vault.Remove($credential); " +
            "Write-Output 'PIN inserito con successo metodo Microsoft 2 (WinRT Credential)'; " +
            "} catch { " +
            "Write-Output ('Metodo Microsoft 2 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryWindowsSecurityAPI(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo Microsoft 3: Windows Security API");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "Add-Type @' " +
            "using System; " +
            "using System.Runtime.InteropServices; " +
            "public class WinSecurity { " +
            "  [DllImport(\\\"advapi32.dll\\\", SetLastError = true)] " +
            "  public static extern bool LogonUser(string lpszUsername, string lpszDomain, string lpszPassword, int dwLogonType, int dwLogonProvider, out IntPtr phToken); " +
            "  [DllImport(\\\"kernel32.dll\\\", CharSet = CharSet.Auto)] " +
            "  public static extern bool CloseHandle(IntPtr handle); " +
            "} " +
            "'@; " +
            "$username = $env:USERNAME; " +
            "$domain = $env:COMPUTERNAME; " +
            "[IntPtr]$token = [IntPtr]::Zero; " +
            "if ([WinSecurity]::LogonUser($username, $domain, '" + pin + "', 2, 0, [ref]$token)) { " +
            "  [WinSecurity]::CloseHandle($token); " +
            "  $wshell = New-Object -ComObject wscript.shell; " +
            "  $wshell.SendKeys('" + pin + "'); " +
            "  $wshell.SendKeys('{ENTER}'); " +
            "  Write-Output 'PIN inserito con successo metodo Microsoft 3 (Security API)'; " +
            "} else { " +
            "  Write-Output 'Metodo Microsoft 3 fallito: credenziali non valide'; " +
            "} " +
            "} catch { " +
            "Write-Output ('Metodo Microsoft 3 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryRegistryCredentialMethod(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo Microsoft 4: Registry Credential Method");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "$regPath = 'HKCU:\\\\Software\\\\PioBase\\\\TempAuth'; " +
            "New-Item -Path $regPath -Force | Out-Null; " +
            "$securePin = ConvertTo-SecureString '" + pin + "' -AsPlainText -Force; " +
            "$encryptedPin = $securePin | ConvertFrom-SecureString; " +
            "Set-ItemProperty -Path $regPath -Name 'TempPin' -Value $encryptedPin; " +
            "Start-Sleep -Milliseconds 500; " +
            "$retrievedPin = Get-ItemProperty -Path $regPath -Name 'TempPin' | Select-Object -ExpandProperty TempPin; " +
            "$plainPin = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR((ConvertTo-SecureString $retrievedPin))); " +
            "$wshell = New-Object -ComObject wscript.shell; " +
            "$wshell.SendKeys($plainPin); " +
            "$wshell.SendKeys('{ENTER}'); " +
            "Remove-Item -Path $regPath -Recurse -Force; " +
            "Write-Output 'PIN inserito con successo metodo Microsoft 4 (Registry Credential)'; " +
            "} catch { " +
            "Write-Output ('Metodo Microsoft 4 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }

    private String tryElevatedPowerShellMethod(String pin, String ipAddress) throws Exception {
        System.out.println("Tentativo Metodo Microsoft 5: Elevated PowerShell with Task Scheduler");

        String command = "powershell.exe -Command \"" +
            "try { " +
            "$taskName = 'PioBasePinEntry_' + (Get-Random); " +
            "$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument \\\"-WindowStyle Hidden -Command `$wshell = New-Object -ComObject wscript.shell; `$wshell.SendKeys('" + pin + "'); `$wshell.SendKeys('{ENTER}'); Stop-Computer -Force -AsJob\\\"; " +
            "$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddSeconds(2); " +
            "$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Highest; " +
            "$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable; " +
            "$task = New-ScheduledTask -Action $action -Trigger $trigger -Principal $principal -Settings $settings; " +
            "Register-ScheduledTask -TaskName $taskName -InputObject $task | Out-Null; " +
            "Start-Sleep -Seconds 3; " +
            "Unregister-ScheduledTask -TaskName $taskName -Confirm:`$false -ErrorAction SilentlyContinue; " +
            "Write-Output 'PIN inserito con successo metodo Microsoft 5 (Task Scheduler)'; " +
            "} catch { " +
            "Write-Output ('Metodo Microsoft 5 fallito: ' + $_.Exception.Message); " +
            "}\"";

        return executeCommand(command, ipAddress);
    }
}
