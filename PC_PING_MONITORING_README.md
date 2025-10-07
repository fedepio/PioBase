# Sistema di Monitoraggio PC tramite Ping Attivo

## Panoramica

Il sistema di monitoraggio dello stato del PC utilizza un approccio basato su **ping attivi** invece del polling tradizionale. Il PC invia periodicamente un ping al server per segnalare che è online. Se il server non riceve ping per un periodo superiore a 10 secondi, marca automaticamente il PC come offline.

## Architettura

### Server-Side

1. **PcPingMonitorService**: Gestisce la ricezione dei ping e il monitoraggio dei timeout
2. **DeviceMonitoringService**: Utilizza lo stato dei ping per aggiornare lo stato combinato PC+Cam
3. **SSE (Server-Sent Events)**: Invia aggiornamenti in tempo reale ai client quando lo stato cambia

### Client-Side

Il PC deve inviare ping periodici al server ogni **3 secondi** per mantenere lo stato "online".

## API Endpoints

### Ricevi Ping dal PC
```http
POST /api/pc/ping/{pcIp}
```

**Parametri:**
- `pcIp` (path): Indirizzo IP del PC

**Risposta:**
```json
{
  "status": "ok",
  "timestamp": 1728345678901,
  "pcIp": "192.168.1.100"
}
```

### Monitoraggio SSE Stato Combinato
```http
GET /api/monitor/system/{pcIp}
```

**Parametri:**
- `pcIp` (path): Indirizzo IP del PC da monitorare

**SSE Events:**
```javascript
event: systemStatus
data: {
  "timestamp": 1728345678901,
  "pcIp": "192.168.1.100",
  "pcOnline": true,
  "pcHostname": "MyPC",
  "pcOs": "macOS",
  "pcUptime": "5:23:45",
  "camIp": "192.168.1.200",
  "camOnline": true,
  "camRtspUrl": "rtsp://192.168.1.200:554/"
}
```

## Configurazione Timeout

- **Intervallo invio ping dal PC**: 3 secondi
- **Timeout server per rilevare offline**: 10 secondi
- **Controllo timeout server**: Ogni 2 secondi
- **Aggiornamento SSE**: Immediato al cambio di stato

## Implementazione Client

### Python (Desktop Script)

```python
#!/usr/bin/env python3
import requests
import time
import socket
import sys

# Configurazione
SERVER_URL = "http://192.168.1.50:8080"  # IP del server
PING_INTERVAL = 3  # secondi
RETRY_DELAY = 5    # secondi tra i retry in caso di errore

def get_local_ip():
    """Ottiene l'IP locale del PC"""
    try:
        # Crea una connessione temporanea per ottenere l'IP locale
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        return local_ip
    except Exception as e:
        print(f"Errore nel recuperare IP locale: {e}")
        return None

def send_ping(server_url, pc_ip):
    """Invia un ping al server"""
    try:
        response = requests.post(
            f"{server_url}/api/pc/ping/{pc_ip}",
            timeout=2
        )
        if response.status_code == 200:
            return True
        else:
            print(f"Errore server: {response.status_code}")
            return False
    except requests.exceptions.Timeout:
        print("Timeout connessione al server")
        return False
    except requests.exceptions.ConnectionError:
        print("Errore connessione al server")
        return False
    except Exception as e:
        print(f"Errore invio ping: {e}")
        return False

def main():
    pc_ip = get_local_ip()
    
    if not pc_ip:
        print("Impossibile ottenere IP locale. Uscita.")
        sys.exit(1)
    
    print(f"Avvio monitoraggio PC con IP: {pc_ip}")
    print(f"Server: {SERVER_URL}")
    print(f"Intervallo ping: {PING_INTERVAL} secondi\n")
    
    consecutive_failures = 0
    
    while True:
        if send_ping(SERVER_URL, pc_ip):
            timestamp = time.strftime('%H:%M:%S')
            print(f"[{timestamp}] Ping inviato con successo")
            consecutive_failures = 0
            time.sleep(PING_INTERVAL)
        else:
            consecutive_failures += 1
            print(f"Errore {consecutive_failures} - Riprovo tra {RETRY_DELAY} secondi...")
            time.sleep(RETRY_DELAY)
            
            # Se troppi errori consecutivi, prova a riottenere l'IP
            if consecutive_failures >= 5:
                print("Troppi errori, riprovo a ottenere IP locale...")
                new_ip = get_local_ip()
                if new_ip and new_ip != pc_ip:
                    pc_ip = new_ip
                    print(f"Nuovo IP locale: {pc_ip}")
                consecutive_failures = 0

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nMonitoraggio terminato dall'utente")
        sys.exit(0)
```

**Installazione dipendenze Python:**
```bash
pip install requests
```

**Esecuzione:**
```bash
python3 pc_ping_monitor.py
```

**Esecuzione come servizio (Linux/macOS):**

Creare il file `/etc/systemd/system/pc-ping-monitor.service`:
```ini
[Unit]
Description=PC Ping Monitor Service
After=network.target

[Service]
Type=simple
User=youruser
ExecStart=/usr/bin/python3 /path/to/pc_ping_monitor.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Abilitare e avviare:
```bash
sudo systemctl enable pc-ping-monitor
sudo systemctl start pc-ping-monitor
```

### Node.js (Desktop Script)

```javascript
const axios = require('axios');
const os = require('os');

// Configurazione
const SERVER_URL = 'http://192.168.1.50:8080';
const PING_INTERVAL = 3000; // millisecondi
const RETRY_DELAY = 5000;   // millisecondi

// Ottiene l'IP locale
function getLocalIP() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return null;
}

// Invia ping al server
async function sendPing(serverUrl, pcIp) {
    try {
        const response = await axios.post(
            `${serverUrl}/api/pc/ping/${pcIp}`,
            {},
            { timeout: 2000 }
        );
        return response.status === 200;
    } catch (error) {
        console.error('Errore invio ping:', error.message);
        return false;
    }
}

// Main loop
async function main() {
    const pcIp = getLocalIP();
    
    if (!pcIp) {
        console.error('Impossibile ottenere IP locale. Uscita.');
        process.exit(1);
    }
    
    console.log(`Avvio monitoraggio PC con IP: ${pcIp}`);
    console.log(`Server: ${SERVER_URL}`);
    console.log(`Intervallo ping: ${PING_INTERVAL}ms\n`);
    
    let consecutiveFailures = 0;
    
    while (true) {
        const success = await sendPing(SERVER_URL, pcIp);
        
        if (success) {
            const timestamp = new Date().toLocaleTimeString();
            console.log(`[${timestamp}] Ping inviato con successo`);
            consecutiveFailures = 0;
            await new Promise(resolve => setTimeout(resolve, PING_INTERVAL));
        } else {
            consecutiveFailures++;
            console.log(`Errore ${consecutiveFailures} - Riprovo tra ${RETRY_DELAY}ms...`);
            await new Promise(resolve => setTimeout(resolve, RETRY_DELAY));
        }
    }
}

// Gestione interruzione
process.on('SIGINT', () => {
    console.log('\n\nMonitoraggio terminato dall\'utente');
    process.exit(0);
});

main();
```

**Installazione dipendenze:**
```bash
npm install axios
```

**Esecuzione:**
```bash
node pc_ping_monitor.js
```

### Windows PowerShell

```powershell
# Configurazione
$SERVER_URL = "http://192.168.1.50:8080"
$PING_INTERVAL = 3  # secondi
$RETRY_DELAY = 5    # secondi

# Ottiene l'IP locale
function Get-LocalIP {
    try {
        $ip = (Get-NetIPAddress -AddressFamily IPv4 | 
               Where-Object {$_.InterfaceAlias -notlike "*Loopback*" -and $_.IPAddress -notlike "169.254.*"} | 
               Select-Object -First 1).IPAddress
        return $ip
    }
    catch {
        Write-Host "Errore nel recuperare IP locale: $_"
        return $null
    }
}

# Invia ping al server
function Send-PcPing {
    param([string]$ServerUrl, [string]$PcIp)
    
    try {
        $response = Invoke-RestMethod -Uri "$ServerUrl/api/pc/ping/$PcIp" `
                                      -Method Post `
                                      -TimeoutSec 2
        return $true
    }
    catch {
        Write-Host "Errore invio ping: $_"
        return $false
    }
}

# Main
$pcIp = Get-LocalIP

if (-not $pcIp) {
    Write-Host "Impossibile ottenere IP locale. Uscita."
    exit 1
}

Write-Host "Avvio monitoraggio PC con IP: $pcIp"
Write-Host "Server: $SERVER_URL"
Write-Host "Intervallo ping: $PING_INTERVAL secondi`n"

$consecutiveFailures = 0

while ($true) {
    if (Send-PcPing -ServerUrl $SERVER_URL -PcIp $pcIp) {
        $timestamp = Get-Date -Format "HH:mm:ss"
        Write-Host "[$timestamp] Ping inviato con successo"
        $consecutiveFailures = 0
        Start-Sleep -Seconds $PING_INTERVAL
    }
    else {
        $consecutiveFailures++
        Write-Host "Errore $consecutiveFailures - Riprovo tra $RETRY_DELAY secondi..."
        Start-Sleep -Seconds $RETRY_DELAY
    }
}
```

**Esecuzione:**
```powershell
powershell -ExecutionPolicy Bypass -File pc_ping_monitor.ps1
```

## Integrazione Mobile

### iOS (Swift)

```swift
import Foundation
import Network

class PcPingMonitor {
    private let serverURL: String
    private let pingInterval: TimeInterval = 3.0
    private var timer: Timer?
    private var pcIP: String?
    
    init(serverURL: String) {
        self.serverURL = serverURL
        self.pcIP = getLocalIPAddress()
    }
    
    // Ottiene l'IP locale del dispositivo
    private func getLocalIPAddress() -> String? {
        var address: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        
        if getifaddrs(&ifaddr) == 0 {
            var ptr = ifaddr
            while ptr != nil {
                defer { ptr = ptr?.pointee.ifa_next }
                
                let interface = ptr?.pointee
                let addrFamily = interface?.ifa_addr.pointee.sa_family
                
                if addrFamily == UInt8(AF_INET) {
                    let name = String(cString: (interface?.ifa_name)!)
                    if name == "en0" || name == "en1" {
                        var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                        getnameinfo(interface?.ifa_addr,
                                  socklen_t((interface?.ifa_addr.pointee.sa_len)!),
                                  &hostname,
                                  socklen_t(hostname.count),
                                  nil,
                                  socklen_t(0),
                                  NI_NUMERICHOST)
                        address = String(cString: hostname)
                    }
                }
            }
            freeifaddrs(ifaddr)
        }
        return address
    }
    
    // Invia ping al server
    private func sendPing() {
        guard let pcIP = pcIP else {
            print("IP locale non disponibile")
            return
        }
        
        let urlString = "\(serverURL)/api/pc/ping/\(pcIP)"
        guard let url = URL(string: urlString) else {
            print("URL non valido")
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 2.0
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("Errore invio ping: \(error.localizedDescription)")
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse,
               httpResponse.statusCode == 200 {
                let timestamp = DateFormatter.localizedString(from: Date(),
                                                             dateStyle: .none,
                                                             timeStyle: .medium)
                print("[\(timestamp)] Ping inviato con successo")
            }
        }
        task.resume()
    }
    
    // Avvia il monitoraggio
    func startMonitoring() {
        guard let pcIP = pcIP else {
            print("Impossibile avviare monitoraggio: IP non disponibile")
            return
        }
        
        print("Avvio monitoraggio PC con IP: \(pcIP)")
        print("Server: \(serverURL)")
        print("Intervallo ping: \(pingInterval) secondi\n")
        
        // Invia subito il primo ping
        sendPing()
        
        // Configura timer per invii periodici
        timer = Timer.scheduledTimer(withTimeInterval: pingInterval, repeats: true) { [weak self] _ in
            self?.sendPing()
        }
    }
    
    // Ferma il monitoraggio
    func stopMonitoring() {
        timer?.invalidate()
        timer = nil
        print("Monitoraggio fermato")
    }
}

// Utilizzo nell'AppDelegate o SceneDelegate
class AppDelegate: UIResponder, UIApplicationDelegate {
    var pingMonitor: PcPingMonitor?
    
    func application(_ application: UIApplication,
                    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        // Inizializza il monitor
        pingMonitor = PcPingMonitor(serverURL: "http://192.168.1.50:8080")
        pingMonitor?.startMonitoring()
        
        return true
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        pingMonitor?.stopMonitoring()
    }
}
```

**Configurazione Info.plist per permettere HTTP:**
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

### iOS (SwiftUI con Background Task)

```swift
import SwiftUI
import BackgroundTasks

@main
struct PcMonitorApp: App {
    @StateObject private var pingService = PingService()
    
    init() {
        // Registra background task
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "com.yourapp.pcping",
            using: nil
        ) { task in
            self.handleBackgroundPing(task: task as! BGAppRefreshTask)
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(pingService)
                .onAppear {
                    pingService.startMonitoring()
                    scheduleBackgroundPing()
                }
        }
    }
    
    private func handleBackgroundPing(task: BGAppRefreshTask) {
        scheduleBackgroundPing()
        
        Task {
            await pingService.sendPing()
            task.setTaskCompleted(success: true)
        }
    }
    
    private func scheduleBackgroundPing() {
        let request = BGAppRefreshTaskRequest(identifier: "com.yourapp.pcping")
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // 15 minuti
        
        try? BGTaskScheduler.shared.submit(request)
    }
}

class PingService: ObservableObject {
    @Published var isOnline = false
    private let serverURL = "http://192.168.1.50:8080"
    private var timer: Timer?
    
    func startMonitoring() {
        timer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] _ in
            Task {
                await self?.sendPing()
            }
        }
    }
    
    func sendPing() async {
        guard let pcIP = getLocalIP(),
              let url = URL(string: "\(serverURL)/api/pc/ping/\(pcIP)") else {
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 2.0
        
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse,
               httpResponse.statusCode == 200 {
                await MainActor.run {
                    self.isOnline = true
                }
            }
        } catch {
            print("Errore ping: \(error)")
            await MainActor.run {
                self.isOnline = false
            }
        }
    }
    
    private func getLocalIP() -> String? {
        // Implementazione come sopra
        return "192.168.1.100" // Placeholder
    }
}
```

### Android (Kotlin)

```kotlin
import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import okhttp3.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class PcPingMonitor(private val context: Context) {
    private val serverUrl = "http://192.168.1.50:8080"
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
    
    private var monitoringJob: Job? = null
    
    // Ottiene l'IP locale
    private fun getLocalIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    // Invia ping al server
    private suspend fun sendPing(pcIp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/api/pc/ping/$pcIp")
                .post(RequestBody.create(null, ByteArray(0)))
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("PcPingMonitor", "Errore invio ping: ${e.message}")
            false
        }
    }
    
    // Avvia il monitoraggio
    fun startMonitoring() {
        val pcIp = getLocalIPAddress() ?: run {
            android.util.Log.e("PcPingMonitor", "IP locale non disponibile")
            return
        }
        
        android.util.Log.i("PcPingMonitor", "Avvio monitoraggio con IP: $pcIp")
        
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val success = sendPing(pcIp)
                if (success) {
                    android.util.Log.d("PcPingMonitor", "Ping inviato con successo")
                }
                delay(3000) // 3 secondi
            }
        }
    }
    
    // Ferma il monitoraggio
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        android.util.Log.i("PcPingMonitor", "Monitoraggio fermato")
    }
}

// Utilizzo nell'Activity o Application
class MainActivity : AppCompatActivity() {
    private lateinit var pingMonitor: PcPingMonitor
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        pingMonitor = PcPingMonitor(this)
        pingMonitor.startMonitoring()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pingMonitor.stopMonitoring()
    }
}
```

**Dipendenze Gradle:**
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.work:work-runtime-ktx:2.8.1'
}
```

**Permessi AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Android (Background Service)

```kotlin
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PcPingService : Service() {
    private lateinit var pingMonitor: PcPingMonitor
    
    override fun onCreate() {
        super.onCreate()
        
        // Crea notifica per servizio foreground
        val notification = NotificationCompat.Builder(this, "pc_monitor_channel")
            .setContentTitle("PC Monitor")
            .setContentText("Monitoraggio PC attivo")
            .setSmallIcon(R.drawable.ic_monitor)
            .build()
        
        startForeground(1, notification)
        
        pingMonitor = PcPingMonitor(this)
        pingMonitor.startMonitoring()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pingMonitor.stopMonitoring()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

// Avvio del servizio
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Avvia servizio foreground
        val serviceIntent = Intent(this, PcPingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
```

### Android (WorkManager per background persistente)

```kotlin
import androidx.work.*

class PingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val pingMonitor = PcPingMonitor(applicationContext)
        val pcIp = pingMonitor.getLocalIPAddress() ?: return Result.retry()
        
        return if (pingMonitor.sendPing(pcIp)) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}

// Programmazione periodica
fun schedulePingWork(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    
    val pingRequest = PeriodicWorkRequestBuilder<PingWorker>(
        15, TimeUnit.MINUTES  // Minimo intervallo per WorkManager
    )
        .setConstraints(constraints)
        .build()
    
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "pc_ping_work",
            ExistingPeriodicWorkPolicy.KEEP,
            pingRequest
        )
}
```

## Test e Debug

### Test dell'endpoint ping

```bash
# Test manuale con curl
curl -X POST http://192.168.1.50:8080/api/pc/ping/192.168.1.100
```

### Monitor SSE da terminale

```bash
# Ascolta eventi SSE
curl -N http://192.168.1.50:8080/api/monitor/system/192.168.1.100
```

### Test con Postman

1. Crea una richiesta POST a `/api/pc/ping/{pcIp}`
2. Per SSE, usa una GET request a `/api/monitor/system/{pcIp}`

## Risoluzione Problemi

### Il PC viene sempre mostrato come offline

1. Verifica che il client stia inviando ping: controlla i log del server
2. Verifica l'IP del PC: deve corrispondere a quello configurato
3. Controlla il firewall del server: deve permettere connessioni sulla porta 8080

### Il PC va offline dopo pochi secondi

1. Verifica che il client invii ping ogni 3 secondi (non più di 10)
2. Controlla errori di rete tra PC e server
3. Verifica timeout nel log del server

### Performance e Ottimizzazioni

1. **Riduzione consumo batteria mobile**: Aumenta l'intervallo di ping a 5-10 secondi
2. **Network roaming**: Disabilita ping quando non su WiFi
3. **Background execution**: Usa WorkManager su Android, Background Tasks su iOS

## Best Practices

1. **Gestione errori**: Implementa retry logic con backoff esponenziale
2. **Gestione IP dinamico**: Rileva cambi di IP e aggiorna automaticamente
3. **Logging**: Mantieni log dettagliati per debug
4. **Sicurezza**: Usa HTTPS in produzione
5. **Autenticazione**: Aggiungi token JWT per autenticare i ping

## Sicurezza

### Autenticazione con Token (Opzionale)

Modifica l'endpoint per richiedere un token:

```java
@PostMapping("/pc/ping/{pcIp}")
public ResponseEntity<Map<String, Object>> receivePcPing(
    @PathVariable String pcIp,
    @RequestHeader("Authorization") String token
) {
    // Valida token
    if (!isValidToken(token)) {
        return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
    }
    
    // ... resto del codice
}
```

Client con token:
```python
headers = {"Authorization": "Bearer YOUR_SECRET_TOKEN"}
response = requests.post(url, headers=headers, timeout=2)
```

## Note di Implementazione

- Il sistema è stato progettato per essere **leggero e veloce**
- **Nessun polling** dal server verso il PC (solo ricezione ping)
- **Aggiornamenti SSE istantanei** al cambio di stato
- **Compatibile** con tutti i sistemi operativi (Windows, macOS, Linux, iOS, Android)
- **Scalabile**: Supporta multipli PC senza overhead significativo

