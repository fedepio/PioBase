# Thumbnails & WebRTC - Documentazione Completa

## 📸 SISTEMA THUMBNAILS

### Panoramica

Genera automaticamente snapshot JPEG dalla IP cam ogni N secondi per:
- Preview rapide dell'ultima immagine
- Timeline storica (ultimi 60 secondi)
- Notifiche push con immagine
- Gallery app mobile

### Caratteristiche

- ✅ **Generazione automatica** ogni 5 secondi
- ✅ **Risoluzione ottimizzata** 320x240 px
- ✅ **Qualità JPEG** configurabile
- ✅ **Auto-cleanup** mantiene ultimi 12 thumbnails
- ✅ **API RESTful** per integrazione mobile

### API Endpoints

#### Avvia Generazione
```http
POST /api/thumbnails/start

Risposta:
{
  "success": true,
  "message": "Generazione thumbnails avviata",
  "interval": 5,
  "maxThumbnails": 12
}
```

#### Ferma Generazione
```http
GET /api/thumbnails/stop

Risposta:
{
  "success": true,
  "message": "Generazione thumbnails fermata"
}
```

#### Lista Thumbnails
```http
GET /api/thumbnails/list

Risposta:
[
  {
    "filename": "thumb_1728165432000.jpg",
    "url": "/thumbnails/thumb_1728165432000.jpg",
    "timestamp": 1728165432000,
    "size": 15234
  },
  ...
]
```

#### Ultimo Thumbnail
```http
GET /api/thumbnails/latest

Risposta:
{
  "filename": "thumb_1728165437000.jpg",
  "url": "/thumbnails/thumb_1728165437000.jpg",
  "timestamp": 1728165437000,
  "size": 15456
}
```

#### Scarica Thumbnail
```http
GET /api/thumbnails/thumb_1728165432000.jpg

Headers:
Content-Type: image/jpeg
Cache-Control: max-age=3600
```

#### Stato Servizio
```http
GET /api/thumbnails/status

Risposta:
{
  "isGenerating": true,
  "interval": 5,
  "maxThumbnails": 12,
  "currentCount": 8
}
```

### Integrazione iOS

```swift
import SwiftUI

struct ThumbnailGalleryView: View {
    @State private var thumbnails: [Thumbnail] = []
    
    func loadThumbnails() async {
        let url = URL(string: "http://192.168.1.100:8080/api/thumbnails/list")!
        let (data, _) = try await URLSession.shared.data(from: url)
        thumbnails = try JSONDecoder().decode([Thumbnail].self, from: data)
    }
    
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))]) {
                ForEach(thumbnails) { thumb in
                    AsyncImage(url: URL(string: "http://192.168.1.100:8080/api\(thumb.url)")) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        ProgressView()
                    }
                    .frame(width: 100, height: 75)
                    .cornerRadius(8)
                }
            }
        }
        .task { await loadThumbnails() }
    }
}

struct Thumbnail: Codable, Identifiable {
    let id = UUID()
    let filename: String
    let url: String
    let timestamp: Int64
    let size: Int
}
```

### Integrazione Android

```kotlin
class ThumbnailGalleryActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private val thumbnails = mutableListOf<Thumbnail>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            loadThumbnails()
        }
    }
    
    private suspend fun loadThumbnails() {
        val response = RetrofitClient.api.getThumbnailList()
        thumbnails.clear()
        thumbnails.addAll(response)
        
        recyclerView.adapter = ThumbnailAdapter(thumbnails) { thumb ->
            // Apri thumbnail fullscreen
            val url = "$BASE_URL${thumb.url}"
            openFullscreen(url)
        }
    }
}

data class Thumbnail(
    val filename: String,
    val url: String,
    val timestamp: Long,
    val size: Int
)

class ThumbnailAdapter(
    private val thumbnails: List<Thumbnail>,
    private val onClick: (Thumbnail) -> Unit
) : RecyclerView.Adapter<ThumbnailViewHolder>() {
    
    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        val thumb = thumbnails[position]
        val url = "$BASE_URL${thumb.url}"
        
        Glide.with(holder.itemView)
            .load(url)
            .into(holder.imageView)
        
        holder.itemView.setOnClickListener { onClick(thumb) }
    }
}
```

### Configurazione Personalizzata

Modifica in `ThumbnailService.java`:

```java
// Intervallo generazione (secondi)
private static final int THUMBNAIL_INTERVAL_SECONDS = 5;

// Numero massimo thumbnails da mantenere
private static final int MAX_THUMBNAILS = 12;

// Risoluzione thumbnail
"-s", "320x240"  // Cambia risoluzione

// Qualità JPEG (1-31, minore = migliore)
"-q:v", "2"
```

---

## 🚀 SISTEMA WEBRTC

### Panoramica

WebRTC offre streaming in tempo reale con latenza ultra-bassa (~100-200ms) vs ~4-10 secondi di HLS.

### Caratteristiche

- ⚡ **Latenza ~100-200ms** (fino a 50x più veloce di HLS)
- 📱 **Supporto nativo iOS/Android**
- 🌐 **Standard web** (no plugin)
- 🔐 **Sicuro** (encryption nativa)
- 📊 **Adaptive bitrate** automatico

### Architettura

```
IP Cam (RTSP) → MediaMTX → WebRTC (WHEP) → Client
  tony:747         Bridge     ~100ms      iOS/Android
```

**MediaMTX** agisce da bridge RTSP→WebRTC usando protocollo WHEP standard.

### Installazione MediaMTX

#### Windows
```bash
# 1. Scarica ultima release
https://github.com/bluenviron/mediamtx/releases

# 2. Estrai mediamtx.exe nella cartella progetto
C:\Users\feder\Desktop\Progetti\PioBase\mediamtx.exe

# 3. Verifica
mediamtx --version
```

#### Linux/Mac
```bash
# Download
wget https://github.com/bluenviron/mediamtx/releases/download/v1.5.0/mediamtx_v1.5.0_linux_amd64.tar.gz

# Estrai
tar -xzf mediamtx_v1.5.0_linux_amd64.tar.gz

# Sposta in PATH
sudo mv mediamtx /usr/local/bin/

# Verifica
mediamtx --version
```

### API Endpoints

#### Avvia Server WebRTC
```http
POST /api/webrtc/start

Risposta Success:
{
  "success": true,
  "message": "Server WebRTC avviato con successo",
  "webrtcUrl": "http://localhost:8889/cam/",
  "whepUrl": "http://localhost:8889/cam/whep",
  "rtspProxy": "rtsp://localhost:8554/cam",
  "camIp": "192.168.1.150",
  "latency": "~100-200ms"
}

Risposta Error:
{
  "success": false,
  "message": "MediaMTX non installato",
  "suggestion": "Scaricare MediaMTX da: https://github.com/bluenviron/mediamtx/releases",
  "installGuide": "Estrarre mediamtx.exe nella directory del progetto"
}
```

#### Ferma Server
```http
GET /api/webrtc/stop

Risposta:
{
  "success": true,
  "message": "Server WebRTC fermato"
}
```

#### Stato Server
```http
GET /api/webrtc/status

Risposta:
{
  "isRunning": true,
  "camIp": "192.168.1.150",
  "processAlive": true,
  "webrtcUrl": "http://localhost:8889/cam/",
  "whepUrl": "http://localhost:8889/cam/whep",
  "latency": "~100-200ms"
}
```

#### Pagina Test
```http
GET /api/webrtc/test-page

Risposta: HTML page per test immediato
```

### Integrazione iOS (Swift)

```swift
import WebRTC

class WebRtcCamPlayer: NSObject {
    private var peerConnectionFactory: RTCPeerConnectionFactory!
    private var peerConnection: RTCPeerConnection?
    private var videoTrack: RTCVideoTrack?
    
    override init() {
        super.init()
        
        // Inizializza WebRTC
        RTCInitializeSSL()
        let encoderFactory = RTCDefaultVideoEncoderFactory()
        let decoderFactory = RTCDefaultVideoDecoderFactory()
        peerConnectionFactory = RTCPeerConnectionFactory(
            encoderFactory: encoderFactory,
            decoderFactory: decoderFactory
        )
    }
    
    func connectToCamera(whepUrl: String, videoView: RTCEAGLVideoView) async throws {
        // Configurazione
        let config = RTCConfiguration()
        config.sdpSemantics = .unifiedPlan
        
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: nil
        )
        
        // Crea peer connection
        peerConnection = peerConnectionFactory.peerConnection(
            with: config,
            constraints: constraints,
            delegate: self
        )
        
        // Aggiungi transceiver per ricevere video/audio
        peerConnection?.addTransceiver(of: .video, init: RTCRtpTransceiverInit())
        peerConnection?.addTransceiver(of: .audio, init: RTCRtpTransceiverInit())
        
        // Crea offer
        let offer = try await peerConnection?.offer(for: constraints)
        try await peerConnection?.setLocalDescription(offer!)
        
        // Invia offer a server WHEP
        var request = URLRequest(url: URL(string: whepUrl)!)
        request.httpMethod = "POST"
        request.setValue("application/sdp", forHTTPHeaderField: "Content-Type")
        request.httpBody = offer!.sdp.data(using: .utf8)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 201 else {
            throw WebRtcError.connectionFailed
        }
        
        // Ricevi answer
        let answerSdp = String(data: data, encoding: .utf8)!
        let answer = RTCSessionDescription(type: .answer, sdp: answerSdp)
        try await peerConnection?.setRemoteDescription(answer)
        
        print("✅ WebRTC connesso! Latenza ~100ms")
    }
    
    deinit {
        peerConnection?.close()
        RTCCleanupSSL()
    }
}

extension WebRtcCamPlayer: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        if let videoTrack = stream.videoTracks.first {
            self.videoTrack = videoTrack
            // Attacca a videoView
        }
    }
    
    // ... altri delegate methods
}

enum WebRtcError: Error {
    case connectionFailed
}
```

### Integrazione Android (Kotlin)

```kotlin
import org.webrtc.*

class WebRtcCamPlayer(private val context: Context) {
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceViewRenderer: SurfaceViewRenderer? = null
    
    init {
        // Inizializza WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }
    
    fun connectToCamera(whepUrl: String, surfaceView: SurfaceViewRenderer) {
        this.surfaceViewRenderer = surfaceView
        
        // Inizializza surface view
        surfaceView.init(EglBase.create().eglBaseContext, null)
        surfaceView.setMirror(false)
        
        // Configurazione
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        
        // Crea peer connection
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onAddStream(stream: MediaStream) {
                    stream.videoTracks.firstOrNull()?.addSink(surfaceView)
                }
                
                override fun onIceCandidate(candidate: IceCandidate) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                // ... altri callbacks
            }
        )
        
        // Aggiungi transceiver
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        
        // Crea offer
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                
                // Invia a server WHEP
                sendOfferToWhep(whepUrl, desc.description)
            }
            
            override fun onCreateFailure(error: String) {
                Log.e("WebRTC", "Errore create offer: $error")
            }
            
            // ... altri callbacks
        }, MediaConstraints())
    }
    
    private fun sendOfferToWhep(whepUrl: String, sdp: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(whepUrl)
            .post(sdp.toRequestBody("application/sdp".toMediaType()))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.code == 201) {
                    val answerSdp = response.body?.string() ?: return
                    val answer = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        answerSdp
                    )
                    peerConnection?.setRemoteDescription(SimpleSdpObserver(), answer)
                    Log.i("WebRTC", "✅ Connesso! Latenza ~100ms")
                }
            }
            
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WebRTC", "Errore connessione: ${e.message}")
            }
        })
    }
    
    fun disconnect() {
        peerConnection?.close()
        surfaceViewRenderer?.release()
    }
}

class SimpleSdpObserver : SdpObserver {
    override fun onSetSuccess() {}
    override fun onSetFailure(error: String) {}
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onCreateFailure(error: String) {}
}
```

### Dipendenze Android

```gradle
dependencies {
    // WebRTC
    implementation 'org.webrtc:google-webrtc:1.0.32006'
    
    // OkHttp per chiamate WHEP
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
}
```

### Dipendenze iOS

```swift
// Podfile
pod 'GoogleWebRTC'
```

### Test Rapido Browser

```
http://localhost:8080/api/webrtc/test-page
```

Apri la pagina generata e clicca "Avvia Stream" per test immediato!

### Configurazione Avanzata

Modifica `mediamtx.yml` (auto-generato):

```yaml
# Abilita ICE servers per connessioni esterne
webrtcICEServers2:
  - urls: [stun:stun.l.google.com:19302]
  
# Per HTTPS (produzione)
webrtcServerCert: /path/to/cert.crt
webrtcServerKey: /path/to/cert.key

# Ottimizzazioni latenza
paths:
  cam:
    source: rtsp://tony:747@192.168.1.150:554/
    runOnDemand: no  # Sempre attivo
```

---

## 📊 CONFRONTO TECNOLOGIE

| Feature | HLS | WebRTC | RTSP |
|---------|-----|--------|------|
| **Latenza** | 4-10 sec | 100-200ms | 200-500ms |
| **Mobile** | ✅ Nativo | ✅ Nativo | ❌ Plugin |
| **Browser** | ✅ Tutti | ✅ Moderni | ❌ No |
| **CPU** | Basso | Medio | Basso |
| **Batteria** | Ottimo | Buono | Ottimo |
| **Setup** | Facile | Medio | Facile |
| **Firewall** | ✅ HTTP | ⚠️ UDP | ❌ Blocchi |

---

## 🎯 QUANDO USARE COSA

### Usa **HLS** quando:
- Necessità compatibilità universale
- Latenza non critica (VOD, replay)
- Risorse server limitate
- Implementazione semplice richiesta

### Usa **WebRTC** quando:
- Latenza critica (live, interattività)
- Video conferencing
- Streaming gaming
- Controllo remoto real-time

### Usa **Thumbnails** quando:
- Preview rapide necessarie
- Notifiche push con immagine
- Timeline storica
- Riduzione traffico dati

---

## 📚 Riferimenti

- [MediaMTX Documentation](https://github.com/bluenviron/mediamtx)
- [WebRTC WHEP Specification](https://datatracker.ietf.org/doc/draft-ietf-wish-whep/)
- [Google WebRTC](https://webrtc.org/)

