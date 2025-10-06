# Integrazione HLS: FFmpeg vs MediaMTX

## 📋 Panoramica

Il progetto PioBase supporta **due implementazioni HLS parallele**:

1. **FFmpeg HLS** - Conversione RTSP→HLS tramite FFmpeg esterno
2. **MediaMTX HLS** - HLS nativo integrato in MediaMTX (raccomandato)

---

## 🎯 Sistema Raccomandato: MediaMTX HLS

### Perché MediaMTX?

✅ **Già integrato** - MediaMTX è già configurato per WebRTC  
✅ **HLS nativo** - Nessun processo FFmpeg separato  
✅ **Più efficiente** - Una sola conversione RTSP→HLS  
✅ **Auto-configurato** - Porta 8890 già attiva quando MediaMTX è in esecuzione  
✅ **Più stabile** - Gestione automatica dei segmenti e playlist  

### Come usare MediaMTX HLS

#### 1. Avvia MediaMTX
```bash
POST http://localhost:8080/api/webrtc/start
```

#### 2. Accedi allo stream HLS
```
http://localhost:8890/cam/index.m3u8
```

#### 3. Configurazione per client HTML/JavaScript
```javascript
const video = document.getElementById('video');
const hls = new Hls();
hls.loadSource('http://localhost:8890/cam/index.m3u8');
hls.attachMedia(video);
```

#### 4. Configurazione per iOS nativo (Safari)
```html
<video controls autoplay>
  <source src="http://localhost:8890/cam/index.m3u8" type="application/vnd.apple.mpegurl">
</video>
```

---

## 🔧 Sistema Alternativo: FFmpeg HLS

### Quando usare FFmpeg HLS?

- Hai bisogno di parametri di encoding specifici
- Vuoi controllare manualmente la qualità/bitrate
- MediaMTX non è disponibile

### Come usare FFmpeg HLS

#### 1. Verifica FFmpeg installato
```bash
ffmpeg -version
```

#### 2. Avvia stream FFmpeg
```bash
GET http://localhost:8080/api/stream/start-auto
```

#### 3. Accedi allo stream HLS
```
http://localhost:8080/api/stream/hls/stream.m3u8
```

---

## ⚠️ Gestione Warning "File HLS non trovato"

### Cosa significa il warning?

```
WARN - File HLS non trovato o non leggibile: stream.m3u8
```

Questo warning appare quando un client richiede `stream.m3u8` ma:
- Lo stream FFmpeg non è stato avviato
- Lo stream è stato fermato
- Il browser ricarica automaticamente la pagina

### ✅ Soluzione Ottimale (dopo le modifiche)

Il controller ora gestisce intelligentemente questa situazione:

#### Scenario 1: Nessuno stream attivo
```
HTTP 503 Service Unavailable
X-Stream-Status: inactive
X-Stream-Message: Nessuno stream attivo. Usa POST /api/stream/start-auto (FFmpeg) o POST /api/webrtc/start (MediaMTX)
```
**Log**: `DEBUG` (non visibile nei log normali)

#### Scenario 2: Solo MediaMTX attivo
```
HTTP 503 Service Unavailable
X-Stream-Status: ffmpeg-inactive-mediamtx-active
X-MediaMTX-HLS-URL: http://localhost:8890/cam/index.m3u8
```
**Log**: `DEBUG` + suggerimento di usare MediaMTX HLS

#### Scenario 3: FFmpeg attivo ma file mancante
```
HTTP 404 Not Found
X-Stream-Status: active-but-file-missing
```
**Log**: `WARN` (problema reale)

---

## 🚀 Endpoint API Completi

### FFmpeg HLS Endpoints

| Endpoint | Metodo | Descrizione |
|----------|--------|-------------|
| `/api/stream/start` | POST | Avvia FFmpeg con URL RTSP custom |
| `/api/stream/start-auto` | GET | Avvia FFmpeg con IP cam rilevata |
| `/api/stream/stop` | GET | Ferma FFmpeg |
| `/api/stream/status` | GET | Stato stream FFmpeg |
| `/api/stream/hls/stream.m3u8` | GET | Playlist HLS FFmpeg |
| `/api/stream/hls/segment*.ts` | GET | Segmenti video FFmpeg |

### MediaMTX Endpoints

| Endpoint | Metodo | Descrizione |
|----------|--------|-------------|
| `/api/webrtc/start` | POST | Avvia MediaMTX |
| `/api/webrtc/stop` | GET | Ferma MediaMTX |
| `/api/webrtc/status` | GET | Stato MediaMTX |
| `http://localhost:8890/cam/index.m3u8` | GET | Playlist HLS MediaMTX (diretto) |

---

## 📊 Confronto Prestazioni

| Caratteristica | FFmpeg HLS | MediaMTX HLS |
|----------------|------------|--------------|
| **Latenza** | ~4-10 sec | ~2-6 sec |
| **CPU Usage** | Alta (re-encoding) | Bassa (copy stream) |
| **Memoria** | ~100-200 MB | ~50-100 MB |
| **Stabilità** | Buona | Ottima |
| **Configurazione** | Manuale | Automatica |
| **Dipendenze** | FFmpeg esterno | MediaMTX già presente |

---

## 🎬 Esempi Pratici

### Client JavaScript Intelligente

Questo client prova prima MediaMTX HLS, poi fallback su FFmpeg:

```javascript
async function startHLSStream() {
    // 1. Verifica se MediaMTX è attivo
    const webrtcStatus = await fetch('http://localhost:8080/api/webrtc/status').then(r => r.json());
    
    if (webrtcStatus.isRunning) {
        // Usa HLS nativo MediaMTX
        console.log('Usando MediaMTX HLS (ottimale)');
        loadHLS('http://localhost:8890/cam/index.m3u8');
    } else {
        // Fallback su FFmpeg HLS
        console.log('MediaMTX non attivo, provo FFmpeg HLS');
        
        // Avvia FFmpeg se necessario
        const streamStatus = await fetch('http://localhost:8080/api/stream/status').then(r => r.json());
        if (!streamStatus.isStreaming) {
            await fetch('http://localhost:8080/api/stream/start-auto');
        }
        
        loadHLS('http://localhost:8080/api/stream/hls/stream.m3u8');
    }
}

function loadHLS(url) {
    const video = document.getElementById('video');
    
    if (Hls.isSupported()) {
        const hls = new Hls({
            enableWorker: true,
            lowLatencyMode: true,
            backBufferLength: 90
        });
        
        hls.loadSource(url);
        hls.attachMedia(video);
        
        hls.on(Hls.Events.ERROR, (event, data) => {
            if (data.fatal) {
                console.error('Errore HLS fatale:', data);
            }
        });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        // Safari nativo
        video.src = url;
    }
}
```

### Gestione Errori Client-Side

```javascript
async function fetchHLSFile(filename) {
    const response = await fetch(`http://localhost:8080/api/stream/hls/${filename}`);
    
    if (response.status === 503) {
        const streamStatus = response.headers.get('X-Stream-Status');
        
        if (streamStatus === 'ffmpeg-inactive-mediamtx-active') {
            // MediaMTX è attivo, usa il suo HLS
            const mediamtxUrl = response.headers.get('X-MediaMTX-HLS-URL');
            console.log('Switching to MediaMTX HLS:', mediamtxUrl);
            loadHLS(mediamtxUrl);
        } else if (streamStatus === 'inactive') {
            // Nessuno stream attivo, avvia MediaMTX
            console.log('Avvio MediaMTX...');
            await fetch('http://localhost:8080/api/webrtc/start', { method: 'POST' });
            
            // Attendi che MediaMTX sia pronto
            await new Promise(resolve => setTimeout(resolve, 3000));
            
            loadHLS('http://localhost:8890/cam/index.m3u8');
        }
    }
}
```

---

## 🔍 Debugging

### Verificare stato completo sistema

```bash
# Stato FFmpeg HLS
curl http://localhost:8080/api/stream/status

# Stato MediaMTX (include HLS)
curl http://localhost:8080/api/webrtc/status

# Test diretto HLS MediaMTX
curl -I http://localhost:8890/cam/index.m3u8

# Test diretto HLS FFmpeg
curl -I http://localhost:8080/api/stream/hls/stream.m3u8
```

### Log Levels

Con le nuove modifiche:
- **DEBUG**: Situazioni normali (stream non attivo, client probe)
- **INFO**: Operazioni standard (start/stop stream)
- **WARN**: Problemi reali (stream attivo ma file mancante)
- **ERROR**: Errori critici (FFmpeg crash, I/O error)

---

## 📝 Raccomandazioni Finali

### Per Produzione
1. ✅ **Usa MediaMTX HLS** come soluzione principale
2. ✅ Configura il client per leggere gli header `X-Stream-Status`
3. ✅ Implementa retry logic lato client
4. ✅ Monitora i log solo per WARN ed ERROR

### Per Sviluppo
1. Testa entrambe le implementazioni
2. Usa FFmpeg HLS se hai bisogno di debug dettagliato dell'encoding
3. Verifica latenza e qualità con entrambi i sistemi

### Configurazione Logging (application.properties)
```properties
# Riduci verbosità log per situazioni normali
logging.level.it.PioSoft.PioBase.controller.StreamController=INFO

# Per debugging dettagliato
# logging.level.it.PioSoft.PioBase.controller.StreamController=DEBUG
```

---

## 🆘 Risoluzione Problemi Comuni

### Warning persistente "File HLS non trovato"

**Causa**: Il browser/client continua a richiedere `/api/stream/hls/stream.m3u8` ma FFmpeg non è avviato.

**Soluzione**:
```bash
# Opzione 1: Avvia FFmpeg HLS
curl http://localhost:8080/api/stream/start-auto

# Opzione 2 (raccomandato): Usa MediaMTX HLS
curl -X POST http://localhost:8080/api/webrtc/start
# Poi usa: http://localhost:8890/cam/index.m3u8
```

### MediaMTX HLS non funziona

**Verifica**:
```bash
# 1. MediaMTX è in esecuzione?
curl http://localhost:8080/api/webrtc/status

# 2. Porta 8890 aperta?
curl -I http://localhost:8890/cam/index.m3u8

# 3. IP cam accessibile?
curl -I rtsp://tony:747@[IP_CAM]:554/
```

### FFmpeg HLS lento/instabile

**Causa**: Re-encoding video consuma risorse.

**Soluzione**: Passa a MediaMTX HLS che usa `-c:v copy` (nessun re-encoding).

---

## 📚 Riferimenti

- [MediaMTX Documentation](https://github.com/bluenviron/mediamtx)
- [HLS.js Documentation](https://github.com/video-dev/hls.js/)
- [Apple HLS Specification](https://developer.apple.com/streaming/)
- [FFmpeg HLS Guide](https://ffmpeg.org/ffmpeg-formats.html#hls-2)

