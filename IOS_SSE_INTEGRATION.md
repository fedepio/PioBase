# Guida Integrazione SSE per App iOS

## Panoramica

Questa guida spiega come integrare gli eventi Server-Sent Events (SSE) in un'app iOS per ricevere aggiornamenti in tempo reale dal backend PioBase. Il sistema SSE permette di monitorare lo stato di PC e IP Camera senza dover effettuare polling continuo.

## Indice

1. [Endpoint SSE Disponibili](#endpoint-sse-disponibili)
2. [Implementazione Swift](#implementazione-swift)
3. [Librerie Consigliate](#librerie-consigliate)
4. [Esempi di Codice](#esempi-di-codice)
5. [Gestione Errori](#gestione-errori)
6. [Best Practices](#best-practices)

---

## Endpoint SSE Disponibili

### 1. Monitoraggio Singolo Dispositivo
**Endpoint:** `GET /api/monitor/{ip}`

Monitora un singolo dispositivo (PC o IP Camera) tramite il suo indirizzo IP.

**Esempio URL:**
```
http://your-server:8080/api/monitor/192.168.1.100
```

**Formato Evento:**
```json
{
  "event": "status",
  "data": {
    "ip": "192.168.1.100",
    "timestamp": 1696694400000,
    "online": true,
    "hostname": "DESKTOP-PC",
    "os": "Windows 11",
    "uptime": "2 days, 5 hours"
  }
}
```

---

### 2. Monitoraggio Sistema Completo (PC + IP Camera)
**Endpoint:** `GET /api/monitor/system/{pcIp}`

Monitora contemporaneamente un PC e la sua IP Camera associata, ricevendo aggiornamenti unificati.

**Esempio URL:**
```
http://your-server:8080/api/monitor/system/192.168.1.100
```

**Formato Evento:**
```json
{
  "event": "systemStatus",
  "data": {
    "timestamp": 1696694400000,
    "pcIp": "192.168.1.100",
    "pcOnline": true,
    "pcHostname": "DESKTOP-PC",
    "pcOs": "Windows 11",
    "pcUptime": "2 days, 5 hours",
    "camIp": "192.168.1.50",
    "camOnline": true,
    "camRtspUrl": "rtsp://192.168.1.50:554/"
  }
}
```

**Stati Possibili:**
- `pcOnline`: `true` se il PC è raggiungibile e SSH disponibile
- `camOnline`: `true` se la IP Camera risponde al ping
- `pcError`: messaggio di errore se il PC ha problemi (es. "SSH timeout")
- `camError`: messaggio di errore se la camera ha problemi

---

### 3. Monitoraggio IP Camera
**Endpoint:** `GET /api/ipcam/monitor`

Monitora solo la IP Camera rilevata automaticamente dal sistema.

**Esempio URL:**
```
http://your-server:8080/api/ipcam/monitor
```

---

## Implementazione Swift

### Opzione 1: Implementazione Nativa (iOS 13+)

Utilizza `URLSession` nativo per gestire gli SSE senza dipendenze esterne.

```swift
import Foundation
import Combine

class SSEManager: NSObject, URLSessionDataDelegate {
    private var session: URLSession?
    private var dataTask: URLSessionDataTask?
    private var buffer = Data()
    
    // Publisher per ricevere eventi
    let eventPublisher = PassthroughSubject<SSEEvent, Never>()
    
    // Struttura per gli eventi SSE
    struct SSEEvent {
        let event: String
        let data: [String: Any]
    }
    
    override init() {
        super.init()
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = .infinity
        config.timeoutIntervalForResource = .infinity
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }
    
    // Connessione all'endpoint SSE
    func connect(to url: URL) {
        var request = URLRequest(url: url)
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("keep-alive", forHTTPHeaderField: "Connection")
        request.timeoutInterval = .infinity
        
        dataTask = session?.dataTask(with: request)
        dataTask?.resume()
        
        print("SSE: Connessione avviata a \(url)")
    }
    
    func disconnect() {
        dataTask?.cancel()
        dataTask = nil
        buffer.removeAll()
        print("SSE: Disconnesso")
    }
    
    // MARK: - URLSessionDataDelegate
    
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        buffer.append(data)
        
        // Processa i dati ricevuti
        if let string = String(data: buffer, encoding: .utf8) {
            let lines = string.components(separatedBy: "\n\n")
            
            for i in 0..<(lines.count - 1) {
                processSSEMessage(lines[i])
            }
            
            // Mantieni l'ultimo frammento incompleto nel buffer
            if let lastLine = lines.last, !lastLine.isEmpty {
                buffer = lastLine.data(using: .utf8) ?? Data()
            } else {
                buffer.removeAll()
            }
        }
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error {
            print("SSE Errore: \(error.localizedDescription)")
            
            // Riconnessione automatica dopo 3 secondi
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
                if let url = task.originalRequest?.url {
                    self?.connect(to: url)
                }
            }
        }
    }
    
    // Parsing del messaggio SSE
    private func processSSEMessage(_ message: String) {
        var eventType = "message"
        var eventData: String?
        
        let lines = message.components(separatedBy: "\n")
        for line in lines {
            if line.hasPrefix("event:") {
                eventType = String(line.dropFirst(6).trimmingCharacters(in: .whitespaces))
            } else if line.hasPrefix("data:") {
                eventData = String(line.dropFirst(5).trimmingCharacters(in: .whitespaces))
            }
        }
        
        guard let jsonString = eventData,
              let jsonData = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
            return
        }
        
        let event = SSEEvent(event: eventType, data: json)
        eventPublisher.send(event)
    }
}
```

### Utilizzo in SwiftUI

```swift
import SwiftUI
import Combine

struct DeviceMonitorView: View {
    @StateObject private var viewModel = DeviceMonitorViewModel()
    
    var body: some View {
        VStack(spacing: 20) {
            // Stato PC
            VStack(alignment: .leading, spacing: 8) {
                Text("PC Status")
                    .font(.headline)
                
                HStack {
                    Circle()
                        .fill(viewModel.pcOnline ? Color.green : Color.red)
                        .frame(width: 12, height: 12)
                    
                    Text(viewModel.pcOnline ? "Online" : "Offline")
                        .foregroundColor(viewModel.pcOnline ? .green : .red)
                }
                
                if viewModel.pcOnline {
                    Text("Hostname: \(viewModel.pcHostname)")
                    Text("OS: \(viewModel.pcOs)")
                    Text("Uptime: \(viewModel.pcUptime)")
                }
            }
            .padding()
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
            
            // Stato Camera
            VStack(alignment: .leading, spacing: 8) {
                Text("IP Camera Status")
                    .font(.headline)
                
                HStack {
                    Circle()
                        .fill(viewModel.camOnline ? Color.green : Color.red)
                        .frame(width: 12, height: 12)
                    
                    Text(viewModel.camOnline ? "Online" : "Offline")
                        .foregroundColor(viewModel.camOnline ? .green : .red)
                }
                
                if viewModel.camOnline {
                    Text("IP: \(viewModel.camIp)")
                    Text("RTSP: \(viewModel.camRtspUrl)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding()
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
        }
        .padding()
        .onAppear {
            viewModel.startMonitoring()
        }
        .onDisappear {
            viewModel.stopMonitoring()
        }
    }
}

class DeviceMonitorViewModel: ObservableObject {
    @Published var pcOnline = false
    @Published var pcHostname = "N/A"
    @Published var pcOs = "N/A"
    @Published var pcUptime = "N/A"
    @Published var camOnline = false
    @Published var camIp = "N/A"
    @Published var camRtspUrl = "N/A"
    
    private let sseManager = SSEManager()
    private var cancellables = Set<AnyCancellable>()
    
    private let serverURL = "http://192.168.1.10:8080" // Modifica con il tuo server
    private let pcIP = "192.168.1.100" // IP del tuo PC
    
    func startMonitoring() {
        // Sottoscrizione agli eventi SSE
        sseManager.eventPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                self?.handleSSEEvent(event)
            }
            .store(in: &cancellables)
        
        // Connessione all'endpoint di monitoraggio sistema
        let url = URL(string: "\(serverURL)/api/monitor/system/\(pcIP)")!
        sseManager.connect(to: url)
    }
    
    func stopMonitoring() {
        sseManager.disconnect()
        cancellables.removeAll()
    }
    
    private func handleSSEEvent(_ event: SSEManager.SSEEvent) {
        print("SSE Event ricevuto: \(event.event)")
        
        if event.event == "systemStatus" {
            // Aggiorna lo stato del PC
            if let pcOnline = event.data["pcOnline"] as? Bool {
                self.pcOnline = pcOnline
            }
            
            if let hostname = event.data["pcHostname"] as? String {
                self.pcHostname = hostname
            }
            
            if let os = event.data["pcOs"] as? String {
                self.pcOs = os
            }
            
            if let uptime = event.data["pcUptime"] as? String {
                self.pcUptime = uptime
            }
            
            // Aggiorna lo stato della camera
            if let camOnline = event.data["camOnline"] as? Bool {
                self.camOnline = camOnline
            }
            
            if let camIp = event.data["camIp"] as? String {
                self.camIp = camIp
            }
            
            if let rtspUrl = event.data["camRtspUrl"] as? String {
                self.camRtspUrl = rtspUrl
            }
        }
    }
}
```

---

## Librerie Consigliate

### 1. **EventSource** (Consigliata)

La libreria più popolare e manutenuta per SSE su iOS.

**Installazione con CocoaPods:**
```ruby
pod 'IKEventSource'
```

**Installazione con Swift Package Manager:**
```swift
dependencies: [
    .package(url: "https://github.com/inaka/EventSource.git", from: "4.0.0")
]
```

**Esempio di utilizzo:**

```swift
import EventSource

class SSEService {
    private var eventSource: EventSource?
    
    func connect(to urlString: String) {
        guard let url = URL(string: urlString) else { return }
        
        eventSource = EventSource(url: url)
        
        // Gestione evento specifico
        eventSource?.addEventListener("systemStatus") { id, event, data in
            guard let jsonData = data?.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
                return
            }
            
            // Processa i dati
            print("System Status: \(json)")
        }
        
        // Gestione connessione aperta
        eventSource?.onOpen {
            print("SSE: Connessione aperta")
        }
        
        // Gestione errori
        eventSource?.onError { error in
            print("SSE Errore: \(error?.localizedDescription ?? "Unknown")")
        }
        
        eventSource?.connect()
    }
    
    func disconnect() {
        eventSource?.disconnect()
        eventSource = nil
    }
}
```

### 2. **Alamofire + EventSource**

Puoi anche combinare Alamofire per le richieste HTTP con EventSource per gli SSE.

---

## Esempi di Codice Completi

### Monitoraggio Dispositivo Singolo

```swift
import SwiftUI

struct SingleDeviceMonitor: View {
    @StateObject private var monitor = SingleDeviceViewModel()
    let deviceIP: String
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Circle()
                    .fill(monitor.isOnline ? Color.green : Color.red)
                    .frame(width: 20, height: 20)
                
                Text(monitor.isOnline ? "Online" : "Offline")
                    .font(.title2)
                    .bold()
            }
            
            if monitor.isOnline {
                Group {
                    InfoRow(title: "IP", value: monitor.ipAddress)
                    InfoRow(title: "Hostname", value: monitor.hostname)
                    InfoRow(title: "OS", value: monitor.os)
                    InfoRow(title: "Uptime", value: monitor.uptime)
                }
            }
            
            if let error = monitor.errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .font(.caption)
            }
        }
        .padding()
        .onAppear {
            monitor.startMonitoring(ip: deviceIP)
        }
        .onDisappear {
            monitor.stopMonitoring()
        }
    }
}

struct InfoRow: View {
    let title: String
    let value: String
    
    var body: some View {
        HStack {
            Text(title)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .bold()
        }
    }
}

class SingleDeviceViewModel: ObservableObject {
    @Published var isOnline = false
    @Published var ipAddress = ""
    @Published var hostname = "N/A"
    @Published var os = "N/A"
    @Published var uptime = "N/A"
    @Published var errorMessage: String?
    
    private let sseManager = SSEManager()
    private var cancellables = Set<AnyCancellable>()
    private let serverURL = "http://192.168.1.10:8080"
    
    func startMonitoring(ip: String) {
        ipAddress = ip
        
        sseManager.eventPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                self?.handleEvent(event)
            }
            .store(in: &cancellables)
        
        let url = URL(string: "\(serverURL)/api/monitor/\(ip)")!
        sseManager.connect(to: url)
    }
    
    func stopMonitoring() {
        sseManager.disconnect()
        cancellables.removeAll()
    }
    
    private func handleEvent(_ event: SSEManager.SSEEvent) {
        if event.event == "status" {
            isOnline = event.data["online"] as? Bool ?? false
            hostname = event.data["hostname"] as? String ?? "N/A"
            os = event.data["os"] as? String ?? "N/A"
            uptime = event.data["uptime"] as? String ?? "N/A"
            
            if let error = event.data["systemInfoError"] as? String {
                errorMessage = error
            } else {
                errorMessage = nil
            }
        }
    }
}
```

---

## Gestione Errori

### Errori Comuni e Soluzioni

1. **Timeout Connessione**
   ```swift
   func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
       if let error = error as NSError? {
           if error.code == NSURLErrorTimedOut {
               print("Timeout - tentativo di riconnessione...")
               reconnect()
           }
       }
   }
   
   private func reconnect() {
       DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
           self?.connect(to: self?.currentURL)
       }
   }
   ```

2. **Gestione Background**
   
   Gli SSE non funzionano in background su iOS. Usa notifiche push per aggiornamenti in background.
   
   ```swift
   func sceneDidEnterBackground(_ scene: UIScene) {
       sseManager.disconnect()
   }
   
   func sceneWillEnterForeground(_ scene: UIScene) {
       sseManager.reconnect()
   }
   ```

3. **Gestione Rete Instabile**
   
   ```swift
   import Network
   
   class NetworkMonitor {
       static let shared = NetworkMonitor()
       private let monitor = NWPathMonitor()
       
       var isConnected: Bool = true {
           didSet {
               NotificationCenter.default.post(
                   name: .networkStatusChanged, 
                   object: nil
               )
           }
       }
       
       init() {
           monitor.pathUpdateHandler = { [weak self] path in
               self?.isConnected = path.status == .satisfied
           }
           monitor.start(queue: DispatchQueue.global(qos: .background))
       }
   }
   ```

---

## Best Practices

### 1. **Gestione del Ciclo di Vita**

Sempre disconnettere quando la view non è visibile:

```swift
struct MyView: View {
    @StateObject private var monitor = DeviceMonitor()
    
    var body: some View {
        // ... UI
        .onAppear { monitor.connect() }
        .onDisappear { monitor.disconnect() }
    }
}
```

### 2. **Timeout e Riconnessione**

Configura timeout appropriati e logica di riconnessione:

```swift
private func setupConnection() {
    let config = URLSessionConfiguration.default
    config.timeoutIntervalForRequest = 60
    config.timeoutIntervalForResource = .infinity
    config.requestCachePolicy = .reloadIgnoringLocalCacheData
}
```

### 3. **Gestione Thread**

Aggiorna sempre la UI sul main thread:

```swift
sseManager.eventPublisher
    .receive(on: DispatchQueue.main) // Importante!
    .sink { event in
        // Aggiorna @Published properties
    }
```

### 4. **Logging e Debug**

Aggiungi logging dettagliato per il debug:

```swift
enum SSELogLevel {
    case debug, info, warning, error
}

class SSELogger {
    static func log(_ message: String, level: SSELogLevel = .info) {
        #if DEBUG
        let prefix = "SSE [\(level)]:"
        print("\(prefix) \(message)")
        #endif
    }
}
```

### 5. **Gestione Memoria**

Usa `weak self` per evitare retain cycles:

```swift
sseManager.eventPublisher
    .sink { [weak self] event in
        self?.handleEvent(event)
    }
    .store(in: &cancellables)
```

---

## Esempio Completo di App

### ContentView.swift

```swift
import SwiftUI

struct ContentView: View {
    @StateObject private var appState = AppState()
    
    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Dispositivi")) {
                    NavigationLink(destination: DeviceMonitorView()) {
                        HStack {
                            Image(systemName: "desktopcomputer")
                            Text("Monitora Sistema")
                        }
                    }
                }
                
                Section(header: Text("Configurazione")) {
                    TextField("Server URL", text: $appState.serverURL)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    TextField("PC IP", text: $appState.pcIP)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }
            }
            .navigationTitle("PioBase Monitor")
        }
    }
}

class AppState: ObservableObject {
    @Published var serverURL = "http://192.168.1.10:8080"
    @Published var pcIP = "192.168.1.100"
}
```

---

## Testing

### Unit Test per SSE Manager

```swift
import XCTest
@testable import YourApp

class SSEManagerTests: XCTestCase {
    var sseManager: SSEManager!
    var cancellables: Set<AnyCancellable>!
    
    override func setUp() {
        super.setUp()
        sseManager = SSEManager()
        cancellables = []
    }
    
    func testEventParsing() {
        let expectation = expectation(description: "Event received")
        
        sseManager.eventPublisher
            .sink { event in
                XCTAssertEqual(event.event, "status")
                XCTAssertNotNil(event.data["online"])
                expectation.fulfill()
            }
            .store(in: &cancellables)
        
        // Simula evento SSE
        let testData = "event: status\ndata: {\"online\":true}\n\n"
        // ... trigger parsing
        
        waitForExpectations(timeout: 5)
    }
}
```

---

## Troubleshooting

### Problema: Gli eventi non arrivano

1. Verifica la connessione di rete
2. Controlla i log del server
3. Verifica che l'URL sia corretto
4. Assicurati che `timeoutInterval` sia infinito

### Problema: Disconnessioni frequenti

1. Implementa keep-alive
2. Verifica la stabilità della rete
3. Aggiungi logica di riconnessione automatica

### Problema: Memory leak

1. Usa `weak self` in tutte le closure
2. Rimuovi observers in `deinit`
3. Cancella subscriptions in `onDisappear`

---

## Risorse Aggiuntive

- **Documentazione Apple URLSession:** https://developer.apple.com/documentation/foundation/urlsession
- **Server-Sent Events Spec:** https://html.spec.whatwg.org/multipage/server-sent-events.html
- **EventSource Library:** https://github.com/inaka/EventSource

---

## Conclusione

L'integrazione SSE permette di ricevere aggiornamenti in tempo reale dal backend PioBase senza polling continuo, migliorando l'efficienza e l'esperienza utente. Segui le best practices per una implementazione robusta e performante.

Per domande o supporto: feder@piosoft.it

