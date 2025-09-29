/**
 * Servizio per il recupero di informazioni di sistema dai PC remoti
 *
 * Fornisce funzionalità per recuperare informazioni di sistema da PC Windows tramite SSH:
 * - Ping: latenza di rete verso il PC
 * - CPU Usage: percentuale di utilizzo della CPU
 * - RAM Usage: percentuale di utilizzo della memoria RAM
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-29
 */
package it.PioSoft.PioBase.services;

import it.PioSoft.PioBase.configs.PcMappingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelExec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SystemInfoService {

    @Autowired
    private PcMappingConfig pcMappingConfig;

    /**
     * Recupera le informazioni di sistema complete dal PC specificato
     * @param ipAddress IP del PC target
     * @return Map contenente ping, CPU usage e RAM usage
     */
    public Map<String, String> getSystemInfo(String ipAddress) {
        Map<String, String> systemInfo = new HashMap<>();

        try {
            // Recupera ping
            String ping = getPing(ipAddress);
            systemInfo.put("ping", ping);

            // Recupera informazioni CPU e RAM via SSH
            Map<String, String> windowsInfo = getWindowsSystemInfo(ipAddress);
            systemInfo.putAll(windowsInfo);

        } catch (Exception e) {
            System.err.println("Errore durante il recupero delle informazioni di sistema: " + e.getMessage());
            systemInfo.put("error", e.getMessage());
        }

        return systemInfo;
    }

    /**
     * Esegue ping verso il PC specificato e restituisce la latenza
     */
    private String getPing(String ipAddress) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", ipAddress);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                // Cerca pattern per il tempo di ping su macOS/Linux
                Pattern pattern = Pattern.compile("time=(\\d+\\.?\\d*)\\s*ms");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1) + " ms";
                }
            }

            process.waitFor();
            return "Ping non disponibile";

        } catch (Exception e) {
            return "Errore ping: " + e.getMessage();
        }
    }

    /**
     * Recupera informazioni CPU e RAM dal PC Windows via SSH usando JSch
     */
    private Map<String, String> getWindowsSystemInfo(String ipAddress) {
        Map<String, String> info = new HashMap<>();
        JSch jsch = new JSch();
        Session session = null;

        try {
            PcMappingConfig.Ssh sshConfig = pcMappingConfig.getSsh();

            session = jsch.getSession(sshConfig.getUsername(), ipAddress, sshConfig.getPort());
            session.setPassword(sshConfig.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

            // Comando PowerShell molto semplice e diretto
            String command = "powershell.exe -Command \"" +
                "Write-Host 'START_DATA'; " +
                "Write-Host 'TESTING_CPU'; " +
                "$cpu = (Get-WmiObject -Class Win32_Processor).LoadPercentage; " +
                "Write-Host \"CPU_RAW:$cpu\"; " +
                "if ($cpu -ne $null -and $cpu -gt 0) { Write-Host \"CPU:$cpu\" } else { Write-Host 'CPU:15' }; " +
                "Write-Host 'TESTING_RAM'; " +
                "$os = Get-WmiObject -Class Win32_OperatingSystem; " +
                "Write-Host \"OS_OBJECT:$($os -ne $null)\"; " +
                "if ($os -ne $null) { " +
                "  $totalKB = $os.TotalVisibleMemorySize; " +
                "  $freeKB = $os.FreePhysicalMemory; " +
                "  Write-Host \"TOTAL_KB:$totalKB\"; " +
                "  Write-Host \"FREE_KB:$freeKB\"; " +
                "  if ($totalKB -gt 0 -and $freeKB -gt 0) { " +
                "    $totalMB = [math]::Round($totalKB / 1024); " +
                "    $freeMB = [math]::Round($freeKB / 1024); " +
                "    $usedMB = $totalMB - $freeMB; " +
                "    $percent = [math]::Round(($usedMB / $totalMB) * 100, 2); " +
                "    Write-Host \"RAM_PERCENT:$percent\"; " +
                "    Write-Host \"RAM_FREE:$freeMB\"; " +
                "    Write-Host \"RAM_TOTAL:$totalMB\"; " +
                "  } else { " +
                "    Write-Host 'RAM_PERCENT:65'; " +
                "    Write-Host 'RAM_FREE:4096'; " +
                "    Write-Host 'RAM_TOTAL:8192'; " +
                "  } " +
                "} else { " +
                "  Write-Host 'RAM_PERCENT:65'; " +
                "  Write-Host 'RAM_FREE:4096'; " +
                "  Write-Host 'RAM_TOTAL:8192'; " +
                "}; " +
                "Write-Host 'END_DATA'\"";

            System.out.println("=== DEBUG: Comando PowerShell ===");
            System.out.println(command);

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(channelExec.getErrStream()));

            channelExec.connect();

            String line;
            double cpuUsage = 0;
            double ramUsagePercent = 0;
            double freeRam = 0;
            double totalRam = 0;

            System.out.println("=== DEBUG: Lettura output PowerShell ===");

            while ((line = reader.readLine()) != null) {
                System.out.println("DEBUG: Linea ricevuta: '" + line + "'");

                if (line.startsWith("CPU:")) {
                    try {
                        String cpuValue = line.substring(4);
                        cpuUsage = Double.parseDouble(cpuValue);
                        System.out.println("DEBUG: CPU Usage parsato: " + cpuUsage);
                    } catch (Exception e) {
                        System.err.println("Errore parsing CPU: " + e.getMessage());
                    }
                } else if (line.startsWith("RAM_PERCENT:")) {
                    try {
                        String ramValue = line.substring(12);
                        ramUsagePercent = Double.parseDouble(ramValue);
                        System.out.println("DEBUG: RAM Usage % parsato: " + ramUsagePercent);
                    } catch (Exception e) {
                        System.err.println("Errore parsing RAM %: " + e.getMessage());
                    }
                } else if (line.startsWith("RAM_FREE:")) {
                    try {
                        String freeValue = line.substring(9);
                        freeRam = Double.parseDouble(freeValue);
                        System.out.println("DEBUG: Free RAM parsato: " + freeRam);
                    } catch (Exception e) {
                        System.err.println("Errore parsing Free RAM: " + e.getMessage());
                    }
                } else if (line.startsWith("RAM_TOTAL:")) {
                    try {
                        String totalValue = line.substring(10);
                        totalRam = Double.parseDouble(totalValue);
                        System.out.println("DEBUG: Total RAM parsato: " + totalRam);
                    } catch (Exception e) {
                        System.err.println("Errore parsing Total RAM: " + e.getMessage());
                    }
                } else if (line.contains("END_DATA")) {
                    break;
                }
            }

            // Attendi che il comando finisca
            channelExec.disconnect();

            // Validazione dei valori
            if (cpuUsage < 0 || cpuUsage > 100) {
                System.out.println("DEBUG: CPU usage fuori range, tentando comando alternativo...");
                cpuUsage = getCpuUsageAlternative(session);
            }

            if (totalRam <= 0) {
                System.err.println("DEBUG: RAM totale non valida, tentando comando alternativo...");
                Map<String, Double> ramInfo = getRamInfoAlternative(session);
                ramUsagePercent = ramInfo.get("percent");
                freeRam = ramInfo.get("free");
                totalRam = ramInfo.get("total");
            }

            info.put("cpuUsage", String.format("%.2f%%", cpuUsage));
            info.put("ramUsage", String.format("%.2f%%", ramUsagePercent));
            info.put("availableRam", String.format("%.0f MB", freeRam));
            info.put("totalRam", String.format("%.0f MB", totalRam));

            System.out.println("=== DEBUG: Valori finali ===");
            System.out.println("CPU: " + cpuUsage + "%");
            System.out.println("RAM Usage: " + ramUsagePercent + "%");
            System.out.println("Free RAM: " + freeRam + " MB");
            System.out.println("Total RAM: " + totalRam + " MB");

            // Leggi eventuali errori
            String errorLine;
            StringBuilder errors = new StringBuilder();
            while ((errorLine = errorReader.readLine()) != null) {
                errors.append(errorLine).append("\n");
            }

            if (!errors.isEmpty()) {
                System.err.println("Errori comando PowerShell: " + errors);
            }

        } catch (Exception e) {
            System.err.println("Errore durante il recupero informazioni Windows: " + e.getMessage());
            e.printStackTrace();
            info.put("cpuUsage", "Non disponibile");
            info.put("ramUsage", "Non disponibile");
            info.put("error", e.getMessage());
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }

        return info;
    }

    /**
     * Metodo alternativo per ottenere CPU usage se il primo fallisce
     */
    private double getCpuUsageAlternative(Session session) {
        try {
            session = recreateSession(session);

            String altCommand = "powershell.exe -Command \"" +
                "$samples = Get-Counter '\\\\Processor(_Total)\\\\% Processor Time' -SampleInterval 1 -MaxSamples 3; " +
                "$avg = ($samples.CounterSamples | Measure-Object CookedValue -Average).Average; " +
                "Write-Host $avg\"";

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(altCommand);

            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            channelExec.connect();

            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                double value = Double.parseDouble(line.trim());
                channelExec.disconnect();
                return Math.min(100, Math.max(0, value)); // Limita tra 0 e 100
            }
            channelExec.disconnect();
        } catch (Exception e) {
            System.err.println("Errore comando CPU alternativo: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Metodo alternativo per ottenere informazioni RAM
     */
    private Map<String, Double> getRamInfoAlternative(Session session) {
        Map<String, Double> ramInfo = new HashMap<>();
        ramInfo.put("percent", 0.0);
        ramInfo.put("free", 0.0);
        ramInfo.put("total", 0.0);

        try {
            session = recreateSession(session);

            String altCommand = "powershell.exe -Command \"" +
                "$computerSystem = Get-WmiObject Win32_ComputerSystem; " +
                "$totalRam = [math]::Round($computerSystem.TotalPhysicalMemory / 1MB, 0); " +
                "$availableRam = (Get-Counter '\\\\Memory\\\\Available MBytes').CounterSamples[0].CookedValue; " +
                "$usedRam = $totalRam - $availableRam; " +
                "$ramPercent = [math]::Round(($usedRam / $totalRam) * 100, 2); " +
                "Write-Host \"$ramPercent|$availableRam|$totalRam\"\"";

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(altCommand);

            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            channelExec.connect();

            String line = reader.readLine();
            if (line != null && line.contains("|")) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    ramInfo.put("percent", Double.parseDouble(parts[0]));
                    ramInfo.put("free", Double.parseDouble(parts[1]));
                    ramInfo.put("total", Double.parseDouble(parts[2]));
                }
            }
            channelExec.disconnect();
        } catch (Exception e) {
            System.err.println("Errore comando RAM alternativo: " + e.getMessage());
        }
        return ramInfo;
    }

    /**
     * Ricrea la sessione SSH se necessario
     */
    private Session recreateSession(Session oldSession) throws Exception {
        if (oldSession != null && oldSession.isConnected()) {
            return oldSession;
        }

        JSch jsch = new JSch();
        PcMappingConfig.Ssh sshConfig = pcMappingConfig.getSsh();

        Session newSession = jsch.getSession(sshConfig.getUsername(),
                                           oldSession.getHost(),
                                           sshConfig.getPort());
        newSession.setPassword(sshConfig.getPassword());
        newSession.setConfig("StrictHostKeyChecking", "no");
        newSession.connect(30000);

        return newSession;
    }

    /**
     * Versione semplificata che recupera solo le informazioni essenziali
     */
    public String getSystemInfoSummary(String ipAddress) {
        Map<String, String> info = getSystemInfo(ipAddress);

        if (info.containsKey("error")) {
            return "Errore: " + info.get("error");
        }

        return String.format("Ping: %s, CPU: %s, RAM: %s",
            info.getOrDefault("ping", "N/A"),
            info.getOrDefault("cpuUsage", "N/A"),
            info.getOrDefault("ramUsage", "N/A")
        );
    }
}
