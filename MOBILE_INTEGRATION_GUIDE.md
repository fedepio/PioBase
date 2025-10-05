# Guida Integrazione iOS e Android - IP Cam Streaming

## 📱 Panoramica Mobile

Il sistema HLS è **completamente ottimizzato** per client mobili iOS e Android con:
- ✅ **CORS abilitato** per tutte le origini
- ✅ **Headers HTTP ottimizzati** per streaming mobile
- ✅ **Codec compatibili** (H.264 video + AAC audio)
- ✅ **Sample rate audio standard** (44.1kHz stereo)
- ✅ **Segmentazione ottimale** (2 secondi per bilanciare latenza/stabilità)
- ✅ **Basso consumo batteria** (no re-encoding video)

---

## 🍎 INTEGRAZIONE iOS (Swift)

### 1. Prerequisiti

Nessuna libreria esterna richiesta! iOS supporta HLS nativamente tramite **AVPlayer**.

### 2. Codice Swift Completo

```swift
import SwiftUI
import AVKit
import Combine

// MARK: - Modelli
struct CamStatus: Codable {
    let ip: String?
    let online: Bool
    let rtspUrl: String?
    let type: String?
    let timestamp: Int64
}

struct StreamResponse: Codable {
    let success: Bool
    let message: String
    let playlistUrl: String?
    let fullUrl: String?
    let rtspUrl: String?
    let mobileOptimized: Bool?
}

// MARK: - API Service
class IpCamService: ObservableObject {
    @Published var camStatus: CamStatus?
    @Published var isStreamActive = false
    @Published var errorMessage: String?
    
    private let baseURL = "http://YOUR_SERVER_IP:8080/api"
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Ottieni IP Cam
    func getCamIP() {
        guard let url = URL(string: "\(baseURL)/ipcam/ip") else { return }
        
        URLSession.shared.dataTaskPublisher(for: url)
            .map(\.data)
            .decode(type: [String: String].self, decoder: JSONDecoder())
            .receive(on: DispatchQueue.main)
            .sink { completion in
                if case .failure(let error) = completion {
                    self.errorMessage = "Errore: \(error.localizedDescription)"
                }
            } receiveValue: { response in
                print("IP Cam: \(response["ip"] ?? "Non trovata")")
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Avvia Stream
    func startStream(completion: @escaping (String?) -> Void) {
        guard let url = URL(string: "\(baseURL)/stream/start-auto") else {
            completion(nil)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        
        URLSession.shared.dataTaskPublisher(for: request)
            .map(\.data)
            .decode(type: StreamResponse.self, decoder: JSONDecoder())
            .receive(on: DispatchQueue.main)
            .sink { result in
                if case .failure(let error) = result {
                    self.errorMessage = "Errore avvio stream: \(error.localizedDescription)"
                    completion(nil)
                }
            } receiveValue: { response in
                if response.success, let fullUrl = response.fullUrl {
                    let streamUrl = "\(self.baseURL)/stream\(fullUrl)"
                    self.isStreamActive = true
                    completion(streamUrl)
                } else {
                    self.errorMessage = response.message
                    completion(nil)
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Ferma Stream
    func stopStream() {
        guard let url = URL(string: "\(baseURL)/stream/stop") else { return }
        
        URLSession.shared.dataTaskPublisher(for: url)
            .map(\.data)
            .receive(on: DispatchQueue.main)
            .sink { _ in } receiveValue: { _ in
                self.isStreamActive = false
            }
            .store(in: &cancellables)
    }
}

// MARK: - SwiftUI View
struct IpCamPlayerView: View {
    @StateObject private var service = IpCamService()
    @State private var player: AVPlayer?
    @State private var isLoading = false
    
    var body: some View {
        VStack(spacing: 20) {
            // Video Player
            if let player = player {
                VideoPlayer(player: player)
                    .frame(height: 300)
                    .cornerRadius(12)
            } else {
                ZStack {
                    Rectangle()
                        .fill(Color.black)
                        .frame(height: 300)
                        .cornerRadius(12)
                    
                    if isLoading {
                        ProgressView("Caricamento stream...")
                            .foregroundColor(.white)
                    } else {
                        Text("Stream non attivo")
                            .foregroundColor(.white)
                    }
                }
            }
            
            // Controlli
            HStack(spacing: 15) {
                Button(action: startStreaming) {
                    Label("Avvia Stream", systemImage: "play.fill")
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .disabled(service.isStreamActive || isLoading)
                
                Button(action: stopStreaming) {
                    Label("Ferma Stream", systemImage: "stop.fill")
                        .padding()
                        .background(Color.red)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .disabled(!service.isStreamActive)
            }
            
            // Messaggi errore
            if let error = service.errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .padding()
            }
            
            Spacer()
        }
        .padding()
        .navigationTitle("IP Camera")
        .onAppear {
            service.getCamIP()
        }
    }
    
    private func startStreaming() {
        isLoading = true
        
        service.startStream { streamUrl in
            isLoading = false
            
            guard let urlString = streamUrl,
                  let url = URL(string: urlString) else {
                return
            }
            
            // Crea e configura AVPlayer
            let playerItem = AVPlayerItem(url: url)
            let newPlayer = AVPlayer(playerItem: playerItem)
            
            // Configurazione per live streaming
            newPlayer.automaticallyWaitsToMinimizeStalling = false
            
            // Osserva stato player
            NotificationCenter.default.addObserver(
                forName: .AVPlayerItemFailedToPlayToEndTime,
                object: playerItem,
                queue: .main
            ) { notification in
                print("Errore riproduzione: \(notification)")
                service.errorMessage = "Errore riproduzione stream"
            }
            
            self.player = newPlayer
            newPlayer.play()
        }
    }
    
    private func stopStreaming() {
        player?.pause()
        player = nil
        service.stopStream()
    }
}

// MARK: - UIKit Version (per progetti non SwiftUI)
class IpCamViewController: UIViewController {
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private let service = IpCamService()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        service.getCamIP()
    }
    
    private func setupUI() {
        view.backgroundColor = .systemBackground
        
        // Container per video
        let videoContainer = UIView()
        videoContainer.backgroundColor = .black
        videoContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(videoContainer)
        
        // Pulsanti
        let startButton = UIButton(type: .system)
        startButton.setTitle("Avvia Stream", for: .normal)
        startButton.addTarget(self, action: #selector(startStream), for: .touchUpInside)
        startButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(startButton)
        
        let stopButton = UIButton(type: .system)
        stopButton.setTitle("Ferma Stream", for: .normal)
        stopButton.addTarget(self, action: #selector(stopStream), for: .touchUpInside)
        stopButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stopButton)
        
        // Layout
        NSLayoutConstraint.activate([
            videoContainer.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            videoContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            videoContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            videoContainer.heightAnchor.constraint(equalToConstant: 250),
            
            startButton.topAnchor.constraint(equalTo: videoContainer.bottomAnchor, constant: 20),
            startButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            
            stopButton.topAnchor.constraint(equalTo: videoContainer.bottomAnchor, constant: 20),
            stopButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
        ])
    }
    
    @objc private func startStream() {
        service.startStream { [weak self] streamUrl in
            guard let self = self,
                  let urlString = streamUrl,
                  let url = URL(string: urlString) else {
                return
            }
            
            DispatchQueue.main.async {
                let player = AVPlayer(url: url)
                player.automaticallyWaitsToMinimizeStalling = false
                
                let playerLayer = AVPlayerLayer(player: player)
                playerLayer.frame = self.view.bounds
                playerLayer.videoGravity = .resizeAspect
                
                self.view.layer.addSublayer(playerLayer)
                
                self.player = player
                self.playerLayer = playerLayer
                
                player.play()
            }
        }
    }
    
    @objc private func stopStream() {
        player?.pause()
        playerLayer?.removeFromSuperlayer()
        player = nil
        playerLayer = nil
        service.stopStream()
    }
}
```

### 3. Info.plist Configuration

Aggiungi per permettere connessioni HTTP (solo per sviluppo locale):

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
    <!-- O meglio, solo per il tuo server: -->
    <key>NSExceptionDomains</key>
    <dict>
        <key>192.168.1.100</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>
```

### 4. Background Playback (Opzionale)

Per permettere riproduzione in background:

**Capabilities → Background Modes → Audio**

```swift
// In AppDelegate o SceneDelegate
import AVFoundation

func setupAudioSession() {
    do {
        try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try AVAudioSession.sharedInstance().setActive(true)
    } catch {
        print("Errore audio session: \(error)")
    }
}
```

---

## 🤖 INTEGRAZIONE ANDROID (Kotlin)

### 1. Dipendenze (build.gradle)

```gradle
dependencies {
    // ExoPlayer per HLS streaming
    implementation 'com.google.android.exoplayer:exoplayer:2.19.1'
    implementation 'com.google.android.exoplayer:exoplayer-hls:2.19.1'
    
    // Retrofit per API calls
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 2. Permessi (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Per connessioni HTTP non-SSL (solo sviluppo) -->
<application
    android:usesCleartextTraffic="true"
    ...>
```

### 3. Codice Kotlin Completo

```kotlin
// MARK: - Data Models
data class CamStatus(
    val ip: String?,
    val online: Boolean,
    val rtspUrl: String?,
    val type: String?,
    val timestamp: Long
)

data class StreamResponse(
    val success: Boolean,
    val message: String,
    val playlistUrl: String?,
    val fullUrl: String?,
    val rtspUrl: String?,
    val mobileOptimized: Boolean?
)

data class CamIpResponse(
    val ip: String,
    val rtspUrl: String,
    val message: String
)

// MARK: - Retrofit API Interface
interface IpCamApi {
    @GET("/api/ipcam/ip")
    suspend fun getCamIp(): CamIpResponse
    
    @POST("/api/stream/start-auto")
    suspend fun startStream(): StreamResponse
    
    @GET("/api/stream/stop")
    suspend fun stopStream(): Map<String, Any>
    
    @GET("/api/stream/status")
    suspend fun getStreamStatus(): Map<String, Any>
}

// MARK: - Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "http://YOUR_SERVER_IP:8080"
    
    val api: IpCamApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpCamApi::class.java)
    }
}

// MARK: - Activity con ExoPlayer
class IpCamActivity : AppCompatActivity() {
    
    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ipcam)
        
        setupViews()
        setupPlayer()
        getCamInfo()
    }
    
    private fun setupViews() {
        playerView = findViewById(R.id.playerView)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        
        btnStart.setOnClickListener { startStream() }
        btnStop.setOnClickListener { stopStream() }
        btnStop.isEnabled = false
    }
    
    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .build()
            .also { player ->
                playerView.player = player
                
                // Configurazione per live streaming
                player.playWhenReady = true
                
                // Listener per errori
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            this@IpCamActivity,
                            "Errore: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                tvStatus.text = "Buffering..."
                            }
                            Player.STATE_READY -> {
                                tvStatus.text = "Stream attivo"
                            }
                            Player.STATE_ENDED -> {
                                tvStatus.text = "Stream terminato"
                            }
                        }
                    }
                })
            }
    }
    
    private fun getCamInfo() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getCamIp()
                tvStatus.text = "IP Cam: ${response.ip}"
            } catch (e: Exception) {
                tvStatus.text = "Errore: ${e.message}"
            }
        }
    }
    
    private fun startStream() {
        btnStart.isEnabled = false
        tvStatus.text = "Avvio stream..."
        
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.startStream()
                
                if (response.success && response.fullUrl != null) {
                    val streamUrl = "${RetrofitClient.BASE_URL}/api/stream${response.fullUrl}"
                    playStream(streamUrl)
                    
                    btnStop.isEnabled = true
                    tvStatus.text = "Stream attivo"
                } else {
                    Toast.makeText(
                        this@IpCamActivity,
                        response.message,
                        Toast.LENGTH_LONG
                    ).show()
                    btnStart.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@IpCamActivity,
                    "Errore: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                btnStart.isEnabled = true
            }
        }
    }
    
    private fun playStream(url: String) {
        // Crea MediaItem per HLS
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        
        // Configura data source factory
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        
        // Crea media source per HLS
        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
        
        // Carica e riproduci
        exoPlayer?.apply {
            setMediaSource(hlsMediaSource)
            prepare()
            play()
        }
    }
    
    private fun stopStream() {
        lifecycleScope.launch {
            try {
                RetrofitClient.api.stopStream()
                
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                tvStatus.text = "Stream fermato"
            } catch (e: Exception) {
                Toast.makeText(
                    this@IpCamActivity,
                    "Errore: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }
    
    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
    }
}
```

### 4. Layout XML (activity_ipcam.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Stato: Disconnesso"
        android:textSize="16sp"
        android:padding="8dp"
        android:layout_marginBottom="16dp"/>
    
    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="250dp"
        android:layout_marginBottom="16dp"
        android:background="@android:color/black"/>
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">
        
        <Button
            android:id="@+id/btnStart"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Avvia Stream"
            android:layout_marginEnd="8dp"/>
        
        <Button
            android:id="@+id/btnStop"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Ferma Stream"
            android:backgroundTint="@android:color/holo_red_dark"/>
    </LinearLayout>
    
</LinearLayout>
```

### 5. Jetpack Compose Version

```kotlin
@Composable
fun IpCamScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Disconnesso") }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Stato: $statusText",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    // Call API to start stream
                    // Then: streamUrl = result.fullUrl
                    // exoPlayer.setMediaSource(...)
                    isStreaming = true
                },
                modifier = Modifier.weight(1f),
                enabled = !isStreaming
            ) {
                Text("Avvia Stream")
            }
            
            Button(
                onClick = {
                    exoPlayer.stop()
                    // Call API to stop stream
                    isStreaming = false
                },
                modifier = Modifier.weight(1f),
                enabled = isStreaming,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red
                )
            ) {
                Text("Ferma Stream")
            }
        }
    }
}
```

---

## 🔧 Configurazione Server

Assicurati che il server Spring Boot sia accessibile dalla rete locale:

**application.properties:**
```properties
server.address=0.0.0.0
server.port=8080
```

Trova l'IP del server:
```bash
# Windows
ipconfig

# Linux/Mac
ifconfig
```

Usa questo IP nelle app mobile (es: `http://192.168.1.100:8080`)

---

## 📊 API Endpoints per Mobile

### 1. Ottieni IP Cam
```http
GET http://192.168.1.100:8080/api/ipcam/ip
```

### 2. Avvia Stream Auto
```http
POST http://192.168.1.100:8080/api/stream/start-auto
```

### 3. URL Stream HLS
```
http://192.168.1.100:8080/api/stream/hls/stream.m3u8
```

### 4. Ferma Stream
```http
GET http://192.168.1.100:8080/api/stream/stop
```

---

## ⚡ Ottimizzazioni Mobile

### Risparmio Batteria
- ✅ Video codec `copy` (no re-encoding)
- ✅ Segmenti 2 secondi (bilanciati)
- ✅ Buffer 10 secondi (5 segmenti)

### Compatibilità
- ✅ H.264 video (supportato da tutti i device)
- ✅ AAC audio 44.1kHz stereo
- ✅ HLS standard (iOS nativo + ExoPlayer Android)

### Performance Rete
- ✅ TCP transport RTSP (più stabile)
- ✅ Headers no-cache per live
- ✅ Keep-alive connections
- ✅ CORS configurato

---

## 🐛 Troubleshooting Mobile

### iOS: "Cannot play stream"
- Verifica Info.plist NSAppTransportSecurity
- Controlla URL stream completo
- Prova su rete WiFi locale

### Android: "Source error"
- Verifica `usesCleartextTraffic="true"`
- Controlla permesso INTERNET
- Verifica URL accessibile

### Entrambi: Buffering continuo
- Riduci qualità stream (se re-encoding attivo)
- Verifica connessione WiFi stabile
- Controlla latenza rete

---

## 🚀 Deploy Produzione

Per produzione, abilita HTTPS:

1. **Configura SSL** su Spring Boot
2. **Aggiorna URLs** nelle app (https://)
3. **Rimuovi** `usesCleartextTraffic` / `NSAllowsArbitraryLoads`

---

## 📝 Note Finali

- **Latenza HLS**: ~4-10 secondi (normale)
- **Consumo dati**: Dipende da bitrate cam (~1-5 Mbps)
- **Batteria**: Ottimizzato ma streaming video consuma sempre
- **Background**: iOS supporta, Android richiede Foreground Service

Il sistema è **production-ready** per mobile! 🎉

