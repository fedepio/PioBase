# API per Gestione IP Camera

Questo documento descrive come utilizzare le API REST per gestire una IP camera sulla rete locale.

## Endpoint Disponibili

### 1. Scansione Automatica della Rete
**POST** `/api/cam/scan`

Scansiona la rete locale per trovare dispositivi con porte aperte tipiche delle IP cam (80, 8080, 554, 8554).

**Richiesta:**
```json
{
  "subnet": "192.168.1"
}
```

**Risposta:**
```json
{
  "found": 2,
  "devices": ["192.168.1.100:80", "192.168.1.105:8080"],
  "message": "Scansione completata. IP cam configurata automaticamente.",
  "selectedCam": "192.168.1.100:80"
}
```

**Esempio con curl:**
```bash
curl -X POST http://localhost:8080/api/cam/scan \
  -H "Content-Type: application/json" \
  -d "{\"subnet\":\"192.168.1\"}"
```

---

### 2. Configurazione Manuale
**POST** `/api/cam/config`

Configura manualmente l'IP e la porta della camera.

**Richiesta:**
```json
{
  "ip": "192.168.1.100",
  "port": 80,
  "username": "admin",
  "password": "password123",
  "streamPath": "video.mjpg"
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

**Esempio con curl:**
```bash
curl -X POST http://localhost:8080/api/cam/config \
  -H "Content-Type: application/json" \
  -d "{\"ip\":\"192.168.1.100\",\"port\":80}"
```

---

### 3. Verifica Stato Camera
**GET** `/api/cam/status`

Verifica se la camera è configurata e raggiungibile.

**Risposta:**
```json
{
  "configured": true,
  "available": true,
  "ip": "192.168.1.100",
  "port": 80,
  "message": "IP cam operativa: 192.168.1.100:80"
}
```

**Esempio con curl:**
```bash
curl http://localhost:8080/api/cam/status
```

---

### 4. Stream Video (MJPEG/HTTP)
**GET** `/api/cam/stream?path=video.mjpg`

Ottiene lo stream video dalla camera. Il parametro `path` dipende dalla tua camera.

**Parametri Query:**
- `path` (opzionale, default: "video.mjpg"): Il percorso dello stream sulla camera

**Esempi comuni di percorsi stream:**
- `video.mjpg` (molte IP cam generiche)
- `videostream.cgi` (alcune TP-Link, Foscam)
- `cam/realmonitor?channel=1&subtype=0` (Dahua)
- `h264_stream` (alcune Axis)

**Utilizzo nel browser:**
```
http://localhost:8080/api/cam/stream?path=video.mjpg
```

**Esempio con curl (salva in file):**
```bash
curl http://localhost:8080/api/cam/stream?path=video.mjpg > stream.mjpg
```

**Utilizzo in HTML:**
```html
<img src="http://localhost:8080/api/cam/stream?path=video.mjpg" alt="Camera Stream">
```

---

### 5. Ottieni URL RTSP
**GET** `/api/cam/rtsp-url?username=admin&password=pass&path=stream1`

Genera l'URL RTSP per utilizzare con player esterni come VLC.

**Parametri Query:**
- `username` (opzionale): Username per autenticazione
- `password` (opzionale): Password per autenticazione
- `path` (opzionale, default: "stream1"): Percorso stream RTSP

**Risposta:**
```json
{
  "rtspUrl": "rtsp://admin:pass@192.168.1.100:554/stream1",
  "message": "Utilizzare questo URL con un player RTSP (es. VLC)"
}
```

**Esempio con curl:**
```bash
curl "http://localhost:8080/api/cam/rtsp-url?username=admin&password=mypass&path=stream1"
```

---

## Workflow di Utilizzo

### Metodo 1: Scansione Automatica
1. Esegui una scansione della rete:
   ```bash
   curl -X POST http://localhost:8080/api/cam/scan \
     -H "Content-Type: application/json" \
     -d "{\"subnet\":\"192.168.1\"}"
   ```

2. Verifica lo stato:
   ```bash
   curl http://localhost:8080/api/cam/status
   ```

3. Accedi allo stream:
   ```
   http://localhost:8080/api/cam/stream?path=video.mjpg
   ```

### Metodo 2: Configurazione Manuale
1. Configura l'IP manualmente:
   ```bash
   curl -X POST http://localhost:8080/api/cam/config \
     -H "Content-Type: application/json" \
     -d "{\"ip\":\"192.168.1.100\",\"port\":80}"
   ```

2. Accedi allo stream:
   ```
   http://localhost:8080/api/cam/stream?path=video.mjpg
   ```

---

## Note per Raspberry Pi

### Installazione Java
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

### Esecuzione dell'applicazione
```bash
# Compila il progetto
./mvnw clean package

# Esegui l'applicazione
java -jar target/PioBase-0.6.1.jar
```

### Avvio automatico al boot
Crea un servizio systemd in `/etc/systemd/system/piobase.service`:

```ini
[Unit]
Description=PioBase Camera Service
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/PioBase
ExecStart=/usr/bin/java -jar /home/pi/PioBase/target/PioBase-0.6.1.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Abilita e avvia il servizio:
```bash
sudo systemctl enable piobase
sudo systemctl start piobase
```

### Verifica problemi di rete
Se la scansione è lenta, verifica che il firewall non blocchi le connessioni:
```bash
sudo ufw allow 8080/tcp
```

---

## Troubleshooting

### La camera non viene trovata
- Verifica che la camera sia sulla stessa subnet
- Controlla che la camera abbia una porta HTTP o RTSP aperta
- Prova a configurare manualmente l'IP con `/api/cam/config`

### Lo stream non funziona
- Verifica il percorso dello stream della tua camera (consulta il manuale)
- Alcune camere richiedono autenticazione nell'URL
- Prova con VLC usando l'URL RTSP da `/api/cam/rtsp-url`

### Percorsi stream comuni per marca
- **Generic MJPEG**: `video.mjpg` o `videostream.cgi`
- **Hikvision**: `ISAPI/Streaming/channels/101`
- **Dahua**: `cam/realmonitor?channel=1&subtype=0`
- **Foscam**: `videostream.cgi?user=admin&pwd=password`
- **Axis**: `axis-cgi/mjpg/video.cgi`
- **TP-Link**: `stream/video/mjpeg`

---

## Supporto
Per problemi o domande, contatta: feder@piosoft.it

