# Sistema HLS Streaming - Documentazione Completa

## 📋 Panoramica

Sistema completo per convertire stream RTSP della IP cam in formato HLS (HTTP Live Streaming) utilizzabile da qualsiasi browser web.

### Componenti Implementati

1. **HlsStreamService** - Servizio per gestire FFmpeg e conversione RTSP → HLS
2. **StreamController** - Controller REST per controllo streaming
3. **ipcam-test.html** - Pagina web di test con player HLS integrato

---

## ⚙️ Prerequisiti

### FFmpeg Installation

**Windows:**
```bash
# Scarica FFmpeg da https://ffmpeg.org/download.html
# Oppure usa Chocolatey:
choco install ffmpeg

# Oppure Scoop:
scoop install ffmpeg
```

**Verifica installazione:**
```bash
ffmpeg -version
```

---

## 🚀 API Endpoints

### 1. Avvio Stream Manuale
```http
POST /api/stream/start?rtspUrl=rtsp://192.168.1.150:554/
```

**Parametri:**
- `rtspUrl` - URL RTSP completo della cam

**Risposta Success:**
```json
{
  "success": true,
  "message": "Stream HLS avviato con successo",
  "playlistUrl": "/hls/stream.m3u8",
  "rtspUrl": "rtsp://192.168.1.150:554/"
}
```

**Risposta Error:**
```json
{
  "success": false,
  "message": "FFmpeg non disponibile",
  "suggestion": "Installare FFmpeg e aggiungerlo al PATH di sistema"
}
```

---

### 2. Avvio Stream Automatico
```http
POST /api/stream/start-auto
```

**Descrizione:** Avvia lo stream automaticamente dalla IP cam rilevata dal sistema di scansione.

**Risposta Success:**
```json
{
  "success": true,
  "message": "Stream HLS avviato con successo",
  "playlistUrl": "/hls/stream.m3u8",
  "rtspUrl": "rtsp://192.168.1.150:554/"
}
```

**Risposta Error (cam non trovata):**
```json
{
  "success": false,
  "message": "IP cam non ancora trovata",
  "suggestion": "Attendere che la scansione rete trovi la cam"
}
```

---

### 3. Stop Stream
```http
GET /api/stream/stop
```

**Risposta:**
```json
{
  "success": true,
  "message": "Stream fermato con successo"
}
```

---

### 4. Stato Stream
```http
GET /api/stream/status
```

**Risposta:**
```json
{
  "isStreaming": true,
  "rtspUrl": "rtsp://192.168.1.150:554/",
  "processAlive": true,
  "playlistUrl": "/hls/stream.m3u8",
  "playlistExists": true
}
```

---

### 5. File HLS (Playlist e Segmenti)
```http
GET /api/stream/hls/stream.m3u8
GET /api/stream/hls/segment001.ts
GET /api/stream/hls/segment002.ts
```

**Headers Risposta:**
```
Content-Type: application/vnd.apple.mpegurl  (.m3u8)
Content-Type: video/mp2t                      (.ts)
Cache-Control: no-cache, no-store, must-revalidate
Access-Control-Allow-Origin: *
```

---

## 🎬 Come Funziona

### Flusso Completo

1. **Client richiesta** → `POST /api/stream/start-auto`
2. **Server ottiene IP** → Da `IpCamScannerService`
3. **FFmpeg avviato** → Comando:
```bash
ffmpeg -rtsp_transport tcp -i rtsp://192.168.1.150:554/ \
  -c:v copy -c:a aac -f hls \
  -hls_time 2 -hls_list_size 5 -hls_flags delete_segments \
  -hls_segment_filename hls-stream/segment%03d.ts \
  hls-stream/stream.m3u8
```
4. **File generati** → `hls-stream/stream.m3u8` + `segment*.ts`
5. **Client richiede** → `GET /api/stream/hls/stream.m3u8`
6. **Player scarica** → Playlist + segmenti video
7. **Riproduzione** → Video visualizzato nel browser

---

## 🖥️ Utilizzo Frontend

### Con hls.js (raccomandato)

```html
<video id="video" controls></video>

<script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
<script>
const video = document.getElementById('video');
const playlistUrl = 'http://localhost:8080/api/stream/hls/stream.m3u8';

if (Hls.isSupported()) {
  const hls = new Hls();
  hls.loadSource(playlistUrl);
  hls.attachMedia(video);
  
  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    video.play();
  });
} else if (video.canPlayType('application/vnd.apple.mpegurl')) {
  // Safari nativo
  video.src = playlistUrl;
  video.play();
}
</script>
```

### Pagina Test Completa

Apri nel browser:
```
http://localhost:8080/ipcam-test.html
```

**Funzionalità pagina test:**
- ✅ Monitoraggio real-time IP cam via SSE
- ✅ Avvio/stop stream con un click
- ✅ Player HLS integrato con hls.js
- ✅ Log eventi in tempo reale
- ✅ Gestione errori automatica
- ✅ Ri-scansione rete manuale

---

## 🔧 Configurazione FFmpeg

### Parametri Principali

```java
// In HlsStreamService.java

"-c:v", "copy"          // Copia codec video (NO re-encoding = veloce)
"-c:a", "aac"           // Converte audio in AAC
"-f", "hls"             // Formato output HLS
"-hls_time", "2"        // Segmenti da 2 secondi
"-hls_list_size", "5"   // Mantieni ultimi 5 segmenti
"-hls_flags", "delete_segments"  // Auto-elimina vecchi segmenti
```

### Ottimizzazioni Possibili

**Bassa latenza:**
```java
"-hls_time", "1"        // Segmenti da 1 secondo
"-hls_list_size", "3"   // Solo 3 segmenti
```

**Alta qualità (con re-encoding):**
```java
"-c:v", "libx264"       // Re-encode H.264
"-preset", "ultrafast"  // Preset veloce
"-crf", "23"            // Qualità (18-28)
"-c:a", "aac"
"-b:a", "128k"          // Bitrate audio
```

**Riduzione dimensione (cam lenta):**
```java
"-s", "640x480"         // Ridimensiona
"-b:v", "500k"          // Bitrate video 500kbps
```

---

## 📂 Struttura File

```
hls-stream/              # Directory output HLS
├── stream.m3u8          # Playlist master
├── segment000.ts        # Segmento video 1
├── segment001.ts        # Segmento video 2
├── segment002.ts        # Segmento video 3
└── ...                  # Altri segmenti (rotazione automatica)
```

**Playlist esempio (stream.m3u8):**
```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:2
#EXT-X-MEDIA-SEQUENCE:0
#EXTINF:2.000000,
segment000.ts
#EXTINF:2.000000,
segment001.ts
#EXTINF:2.000000,
segment002.ts
```

---

## 🔄 Workflow Completo

### Scenario 1: Avvio Automatico

```javascript
// 1. Client avvia stream
const response = await fetch('/api/stream/start-auto', { method: 'POST' });
const result = await response.json();

// 2. Server ottiene IP cam (da scansione automatica)
// 3. FFmpeg converte RTSP → HLS
// 4. Client ottiene playlist URL

// 5. Player carica stream
const hls = new Hls();
hls.loadSource(result.playlistUrl);
hls.attachMedia(videoElement);
```

### Scenario 2: Cam Cambia IP

1. Cam va offline (IP cambiato)
2. `IpCamScannerService` ri-scansiona automaticamente
3. Trova nuovo IP, aggiorna `config/ipcam.json`
4. Stream attuale continua (vecchio IP)
5. Client ferma stream: `POST /api/stream/stop`
6. Client riavvia: `POST /api/stream/start-auto` → Usa nuovo IP

---

## 🐛 Troubleshooting

### Stream non si avvia

**Problema:** `FFmpeg non disponibile`
```bash
# Verifica FFmpeg installato
ffmpeg -version

# Windows: Aggiungi al PATH
setx PATH "%PATH%;C:\ffmpeg\bin"
```

**Problema:** `Timeout: FFmpeg non ha generato il file playlist`
- Verifica RTSP URL corretto
- Controlla che cam sia raggiungibile
- Prova URL RTSP manualmente con VLC

### Video nero/buffering

**Causa:** Bitrate troppo alto per rete
```java
// Riduci qualità in HlsStreamService
"-b:v", "500k",  // Limita bitrate
"-s", "640x480"  // Riduci risoluzione
```

### Browser non riproduce

**Safari:** Usa supporto nativo HLS
**Chrome/Firefox:** Richiede hls.js (già incluso nella pagina test)

### File .m3u8 non trovato

Verifica percorso:
```
http://localhost:8080/api/stream/hls/stream.m3u8
```

Non:
```
http://localhost:8080/hls/stream.m3u8  ❌
```

---

## 🎯 Testing

### Test Manuale API

**1. Verifica IP cam:**
```bash
curl http://localhost:8080/api/ipcam/ip
```

**2. Avvia stream:**
```bash
curl -X POST http://localhost:8080/api/stream/start-auto
```

**3. Controlla playlist:**
```bash
curl http://localhost:8080/api/stream/hls/stream.m3u8
```

**4. Apri player:**
```
http://localhost:8080/ipcam-test.html
```

### Test con VLC

Apri URL:
```
http://localhost:8080/api/stream/hls/stream.m3u8
```

---

## 📊 Performance

### Consumo Risorse

- **CPU:** ~5-10% (con `-c:v copy`, no re-encoding)
- **RAM:** ~50-100 MB
- **Disco:** ~10-20 MB (segmenti HLS temporanei)
- **Rete:** Dipende da bitrate cam (tipicamente 1-5 Mbps)

### Latenza

- **RTSP nativo:** ~200-500ms
- **HLS:** ~4-10 secondi (per via segmentazione)

Per ridurre latenza HLS:
```java
"-hls_time", "1"         // Segmenti 1 sec invece di 2
"-hls_list_size", "3"    // Solo 3 segmenti
```

---

## 🔒 Sicurezza

### Autenticazione RTSP

Se la cam richiede username/password:

```java
// In HlsStreamService.startStream()
String rtspUrl = "rtsp://username:password@192.168.1.150:554/";
```

### CORS

Headers CORS già configurati in `StreamController`:
```java
.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
```

Per produzione, limitare origin:
```java
.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://yourdomain.com")
```

---

## 📝 Note Importanti

1. **FFmpeg deve essere installato** nel sistema e nel PATH
2. **Directory `hls-stream/`** creata automaticamente
3. **Segmenti auto-eliminati** con flag `delete_segments`
4. **Un solo stream** alla volta (multipli richiedono directory separate)
5. **Stream persiste** anche se client disconnette (chiamare `/stop` per fermare)

---

## 🚀 Prossimi Passi

### Miglioramenti Possibili

1. **Stream multipli:** Directory separate per cam diverse
2. **Transcoding dinamico:** Qualità adattiva in base a rete
3. **Recording:** Salvataggio segmenti per replay
4. **Thumbnails:** Generazione preview ogni N secondi
5. **WebRTC:** Per latenza ultra-bassa (~100ms)

---

## 📚 Riferimenti

- [FFmpeg HLS Documentation](https://ffmpeg.org/ffmpeg-formats.html#hls-2)
- [hls.js Library](https://github.com/video-dev/hls.js/)
- [HLS Specification](https://datatracker.ietf.org/doc/html/rfc8216)

