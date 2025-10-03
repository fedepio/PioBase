package it.PioSoft.PioBase.dto;

/**
 * DTO per configurazione IP cam
 */
public class CamConfigRequest {
    private String ip;
    private int port;
    private String username;
    private String password;
    private String streamPath; // Es: "stream1" o "video.mjpg"

    public CamConfigRequest() {
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStreamPath() {
        return streamPath;
    }

    public void setStreamPath(String streamPath) {
        this.streamPath = streamPath;
    }
}