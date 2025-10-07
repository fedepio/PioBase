# IP Camera Scanner - Documentazione

## Descrizione

Sistema automatico per rilevare e monitorare IP camera sulla rete locale tramite porta RTSP (554).

## Funzionalità

### 1. **Scansione Automatica**
- All'avvio dell'applicazione, il servizio scansiona automaticamente la rete locale
- Cerca dispositivi con porta 554 (RTSP) aperta
- Salva il primo IP trovato in `config/ipcam.json`

### 2. **Monitoraggio Continuo**
- Ogni 3 secondi verifica se la cam è online tramite ping ICMP e controllo porta RTSP
- Invia aggiornamenti in tempo reale ai client tramite Server-Sent Events (SSE)
- Broadcast dello stato della cam (online/offline)

### 3. **Recupero Automatico**
- Se la cam va offline, il sistema ri-scansiona automaticamente la rete
- Quando trova nuovamente la cam (anche con IP diverso), aggiorna la configurazione
- Salva il nuovo IP sovrascrivendo quello precedente

### 4. **File di Configurazione**
Il file `config/ipcam.json` viene creato automaticamente con:
```json
{
  "ip": "192.168.1.150",
  "lastUpdate": 1701234567890,
  "lastScan": "Sun Oct 05 14:30:00 CET 2025"
}
```

## API Endpoints

### Monitoraggio in tempo reale (SSE)
```
GET /api/ipcam/monitor
```
**Descrizione**: Stream SSE per ricevere aggiornamenti in tempo reale sullo stato della cam

**Risposta evento SSE**:
```json
{
  "ip": "192.168.1.150",
  "timestamp": 1701234567890,
  "online": true,
  "type": "ipcam",
  "port": 554,
  "rtspUrl": "rtsp://192.168.1.150:554/"
}
```

**Esempio JavaScript**:
```javascript
const eventSource = new EventSource('http://localhost:8080/api/ipcam/monitor');

eventSource.addEventListener('status', (event) => {
  const data = JSON.parse(event.data);
  console.log('Stato cam:', data);
  
  if (data.online) {
    console.log('Cam online! URL RTSP:', data.rtspUrl);
  } else {
    console.log('Cam offline');
  }
});

eventSource.onerror = (error) => {
  console.error('Errore SSE:', error);
};
```

### Ottieni IP corrente
```
GET /api/ipcam/ip
```
**Descrizione**: Restituisce l'IP corrente della cam salvato in configurazione

**Risposta**:
```json
{
  "ip": "192.168.1.150",
  "rtspUrl": "rtsp://192.168.1.150:554/",
  "message": "IP cam trovata"
}
```

### Forza nuova scansione
```
POST /api/ipcam/scan
```
**Descrizione**: Forza una nuova scansione della rete per cercare la cam

**Risposta**:
```
Scansione rete avviata
```

## Architettura

### Servizi

#### **IpCamScannerService**
- Scansiona la rete locale per dispositivi con porta 554 aperta
- Gestisce il file di configurazione JSON
- Monitora periodicamente lo stato della cam
- Ri-scansiona automaticamente in caso di cam offline

#### **DeviceMonitoringService**
- Gestisce le sottoscrizioni SSE dei client
- Invia broadcast degli aggiornamenti di stato
- Cache dello stato di tutti i dispositivi monitorati

### Flusso di Lavoro

1. **Avvio Applicazione**
   - `IpCamScannerService` si inizializza
   - Crea directory `config/` se non esiste
   - Carica IP da `config/ipcam.json` (se esiste)
   - Se non esiste, avvia scansione automatica della rete

2. **Monitoraggio Continuo** (ogni 3 secondi)
   - Verifica se cam è online (ping ICMP + porta 554)
   - Prepara stato con timestamp, IP, porta, URL RTSP
   - Invia broadcast ai client sottoscritti via SSE
   - Se offline → avvia ri-scansione automatica

3. **Re-discovery Automatico**
   - Quando cam va offline, scansiona rete
   - Trova nuovo IP con porta 554 aperta
   - Sovrascrive configurazione con nuovo IP
   - Riprende monitoraggio normale

4. **Client Subscription**
   - Client si connette a `/api/ipcam/monitor`
   - Riceve subito stato corrente (se disponibile)
   - Riceve aggiornamenti automatici ogni volta che lo stato cambia
   - Connessione SSE persistente (Long-lived)

## Configurazione

Il sistema usa le seguenti costanti (configurabili in `IpCamScannerService.java`):

```java
RTSP_PORT = 554              // Porta RTSP standard
TIMEOUT_MS = 500             // Timeout connessione socket (ms)
PING_TIMEOUT_MS = 1000       // Timeout ping ICMP (ms)
MAX_THREADS = 50             // Thread paralleli per scansione
CAM_CONFIG_DIR = "config"    // Directory configurazione
CAM_CONFIG_FILE = "ipcam.json" // File configurazione
```

Intervallo monitoraggio: `@Scheduled(fixedDelay = 3000)` → ogni 3 secondi

## Integrazione con Sistema Esistente

Il sistema si integra perfettamente con il monitoraggio PC esistente:
- Usa lo stesso `DeviceMonitoringService` per broadcast SSE
- Stesso pattern di sottoscrizione dei PC (`/api/monitor/{ip}`)
- Cache condivisa degli stati dispositivi
- Client può monitorare sia PC che cam con la stessa API

## Testing

### Test manuale con curl

**Ottieni IP cam**:
```bash
curl http://localhost:8080/api/ipcam/ip
```

**Forza scansione**:
```bash
curl -X POST http://localhost:8080/api/ipcam/scan
```

**Monitor SSE**:
```bash
curl -N http://localhost:8080/api/ipcam/monitor
```

### Test con VLC
Una volta ottenuto l'IP, puoi testare lo stream RTSP:
```
rtsp://192.168.1.150:554/
```

## Note Tecniche

- **Scansione rete**: Usa 50 thread paralleli per velocità (scansione completa ~10-15 secondi)
- **Ping**: Prima verifica ICMP, poi porta 554 per ridurre falsi positivi
- **JSON**: Usa Jackson ObjectMapper per serializzazione automatica
- **SSE**: Connessioni Long-lived con timeout infinito (`Long.MAX_VALUE`)
- **Thread-safe**: Usa `CopyOnWriteArrayList` e `ConcurrentHashMap` per gestione concorrente

## Troubleshooting

**Cam non trovata**:
- Verifica che la cam sia accesa e connessa alla rete
- Controlla che la porta 554 sia aperta
- Verifica il firewall

**IP salvato errato**:
- Forza nuova scansione: `POST /api/ipcam/scan`
- Oppure elimina `config/ipcam.json` e riavvia l'app

**SSE non funziona**:
- Verifica CORS se client è su dominio diverso
- Controlla log server per errori
- Usa browser developer tools per vedere stream SSE

