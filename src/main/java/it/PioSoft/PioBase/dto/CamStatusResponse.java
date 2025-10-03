package it.PioSoft.PioBase.dto;

/**
 * DTO per risposta stato IP cam
 */
public class CamStatusResponse {
    private boolean configured;
    private boolean available;
    private String ip;
    private int port;
    private String message;

    public CamStatusResponse() {
    }

    public CamStatusResponse(boolean configured, boolean available, String ip, int port, String message) {
        this.configured = configured;
        this.available = available;
        this.ip = ip;
        this.port = port;
        this.message = message;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

