# Gestione IP Camera - Guida Utilizzo

## 🎥 Funzionalità Implementate

Il server **cerca automaticamente la IP camera sulla rete locale all'avvio** utilizzando una scansione intelligente simile allo script Python fornito.

### Credenziali Camera
- **Username**: `tony`
- **Password**: `747`

Le credenziali sono hardcoded nel servizio e vengono incluse automaticamente negli URL di streaming.

---

## 🚀 Avvio Automatico

**All'avvio del server**, il servizio:

1. ✅ Rileva automaticamente la subnet locale (es. `192.168.1.0/24`)
2. ✅ Scansiona tutti gli IP della rete (da .1 a .254)
3. ✅ Verifica le porte comuni delle IP cam:
   - **80, 8080** (HTTP)
   - **554, 8554** (RTSP)
   - **443, 8443** (HTTPS)
   - **37777** (Dahua)
   - **34567** (Hikvision)
   - **9000** (Generic)
4. ✅ Testa la risposta RTSP per confermare che sia una vera IP camera
5. ✅ Seleziona automaticamente la miglior cam trovata (priorità a chi supporta RTSP)

**La scansione avviene in background** con 50 thread paralleli per velocità massima.

---

## 📡 Endpoint API

### 1. **Verifica Stato Camera**
```http
GET /api/cam/status
```

**Risposta quando trovata:**
```json
{
  "configured": true,
  "available": true,
  "scanCompleted": true,
  "message": "IP cam operativa: 192.168.1.100:80",
  "ip": "192.168.1.100",
  "port": 80
}
```

**Risposta se scansione in corso:**
```json
{
  "configured": false,
  "available": false,
  "scanCompleted": false,
  "message": "Scansione automatica in corso..."
}
```

---

### 2. **Ottieni URL Stream HTTP/MJPEG**
```http
GET /api/cam/stream?path=video.mjpg
```

**Risposta:**
```json
{
  "streamUrl": "http://tony:747@192.168.1.100:80/video.mjpg",
  "type": "HTTP/MJPEG",
  "ip": "192.168.1.100",
  "message": "Utilizzare questo URL per visualizzare lo stream"
}
```

L'URL include già le credenziali e può essere usato direttamente in:
- Browser
- Tag HTML `<img src="...">`
- Player multimediali

---

### 3. **Ottieni URL Stream RTSP**
```http
GET /api/cam/rtsp-url?path=stream1
```

**Risposta:**
```json
{
  "rtspUrl": "rtsp://tony:747@192.168.1.100:554/stream1",
  "type": "RTSP",
  "message": "Utilizzare questo URL con un player RTSP (es. VLC)"
}
```

Utilizzabile con:
- VLC Media Player
- FFmpeg
- OpenCV
- Altri player RTSP

---

### 4. **Scansione Manuale**
```http
POST /api/cam/scan
Content-Type: application/json

{
  "subnet": "192.168.1"
}
```

**Risposta:**
```json
{
  "found": 2,
  "devices": ["192.168.1.100:80", "192.168.1.101:8080"],
  "message": "Scansione completata. IP cam configurata automaticamente.",
  "selectedCam": "192.168.1.100:80"
}
```

---

### 5. **Configurazione Manuale**
```http
POST /api/cam/config
Content-Type: application/json

{
  "ip": "192.168.1.100",
  "port": 80
}
```

**Risposta:**
```json
{
  "message": "IP cam configurata correttamente",
  "ip": "192.168.1.100",
  "port": "80"
}
```

---

### 6. **Riavvia Ricerca Automatica**
```http
POST /api/cam/rediscover
```

Riavvia la scansione automatica della rete.

**Risposta:**
```json
{
  "message": "IP cam trovata e configurata",
  "ip": "192.168.1.100",
  "port": "80"
}
```

---

## 🔍 Logica di Ricerca Automatica

Il servizio Java implementa la stessa logica dello script Python:

### 1. **Rilevamento Rete**
```java
Socket socket = new Socket();
socket.connect(new InetSocketAddress("8.8.8.8", 80), 2000);
String localIp = socket.getLocalAddress().getHostAddress();
// Estrae subnet: 192.168.1.x → 192.168.1
```

### 2. **Scansione Parallela**
- 50 thread in parallelo
- Timeout connessione: 1 secondo
- Timeout ping: 500ms

### 3. **Test Porte**
Per ogni host raggiungibile, verifica:
```java
private static final int[] COMMON_PORTS = {
    80, 8080,      // HTTP
    554, 8554,     // RTSP
    443, 8443,     // HTTPS
    37777,         // Dahua
    34567,         // Hikvision
    9000           // Generic Cam
};
```

### 4. **Verifica RTSP**
Invia comando RTSP OPTIONS per confermare che sia una vera IP cam:
```
OPTIONS rtsp://192.168.1.100:554/ RTSP/1.0
CSeq: 1
```

Se la risposta contiene "RTSP" → **è una IP camera!** ✓

### 5. **Selezione Automatica**
- Priorità alle cam che rispondono a RTSP
- Fallback sulla prima cam con porte aperte

---

## 💡 Esempi d'Uso

### Visualizzare lo stream in una pagina web:

```javascript
// 1. Ottieni l'URL stream
fetch('/api/cam/stream?path=video.mjpg')
  .then(res => res.json())
  .then(data => {
    // 2. Mostra lo stream in un tag img
    document.getElementById('camera').src = data.streamUrl;
  });
```

```html
<img id="camera" alt="IP Camera Stream" />
```

### Con VLC Player:
```bash
vlc rtsp://tony:747@192.168.1.100:554/stream1
```

---

## 📊 Log del Server

Durante l'avvio vedrai:

```
=== Avvio ricerca automatica IP Camera ===
IP locale rilevato: 192.168.1.50 -> Subnet: 192.168.1.0/24
Subnet rilevata: 192.168.1.0/24
Scansione della rete in corso...
Inizio scansione rete: 192.168.1.0/24
Dispositivo trovato: 192.168.1.100:80 (Porte: [80, 554])
✓ 192.168.1.100:554 risponde a comandi RTSP!
IP Cam selezionata: 192.168.1.100:80 (RTSP: 554)
=== IP Camera trovata e configurata ===
IP: 192.168.1.100:80
RTSP disponibile su porta: 554
```

---

## ⚙️ Configurazione

Le credenziali sono hardcoded in `IpCamService.java`:

```java
private static final String CAM_USERNAME = "tony";
private static final String CAM_PASSWORD = "747";
```

Per cambiarle, modifica queste costanti e ricompila.

---

## 🛠️ Troubleshooting

**La cam non viene trovata?**
1. Verifica che la cam sia accesa e connessa alla stessa rete
2. Controlla i log del server per vedere quali IP sono stati scansionati
3. Prova una scansione manuale: `POST /api/cam/scan`
4. Configura manualmente se conosci l'IP: `POST /api/cam/config`

**La scansione è lenta?**
- La scansione completa di 254 IP richiede ~10-20 secondi
- Avviene solo all'avvio e in background

**Stream non funziona?**
- Verifica che il path sia corretto (es. `video.mjpg`, `stream1`, etc.)
- Alcune cam usano path diversi: `/live`, `/h264`, `/video.cgi`, etc.
- Consulta il manuale della tua IP camera per il path corretto

---

## 📝 Note Tecniche

- **Thread Pool**: 50 thread paralleli per la scansione
- **Timeout**: 1s per connessione, 500ms per ping, 2s per RTSP
- **Formato URL HTTP**: `http://username:password@ip:port/path`
- **Formato URL RTSP**: `rtsp://username:password@ip:port/path`
- **Autenticazione**: HTTP Basic Auth inclusa nell'URL

