# Integrazione SSE per Android

Guida per ricevere aggiornamenti in tempo reale dal server PioBase tramite Server-Sent Events (SSE).

## Endpoint SSE Disponibili

### 1. Monitoraggio Combinato PC + IP Cam
```
GET /api/monitor/system/{pcIpAddress}
```
Ricevi aggiornamenti combinati su stato PC e IP Cam ogni 2 secondi.

**Evento SSE:** `systemStatus`

**Payload esempio:**
```json
{
  "timestamp": 1234567890,
  "pcIp": "192.168.1.100",
  "pcOnline": true,
  "pcHostname": "MyPC",
  "pcOs": "Windows 11",
  "pcUptime": "2 days, 3 hours",
  "camIp": "192.168.1.101",
  "camOnline": true,
  "camRtspUrl": "rtsp://192.168.1.101:554/"
}
```

### 2. Monitoraggio Singolo Dispositivo
```
GET /api/monitor/{ipAddress}
```
Ricevi aggiornamenti su un singolo dispositivo.

**Evento SSE:** `status`

**Payload esempio:**
```json
{
  "ip": "192.168.1.100",
  "timestamp": 1234567890,
  "online": true,
  "hostname": "MyPC",
  "os": "Windows 11",
  "uptime": "2 days, 3 hours"
}
```

### 3. Monitoraggio IP Camera
```
GET /api/ipcam/monitor
```
Ricevi aggiornamenti sulla IP Camera trovata automaticamente.

**Evento SSE:** `status`

**Payload esempio:**
```json
{
  "timestamp": 1234567890,
  "ip": "192.168.1.101",
  "online": true,
  "rtspUrl": "rtsp://192.168.1.101:554/"
}
```

## Note Aggiuntive
- Assicurati che il dispositivo sia connesso alla stessa rete del server PioBase.
- Gli indirizzi IP negli esempi sono indicativi; utilizzare gli indirizzi reali dei propri dispositivi.

