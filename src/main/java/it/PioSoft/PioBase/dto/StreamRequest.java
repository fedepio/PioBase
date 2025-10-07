package it.PioSoft.PioBase.dto;

public class StreamRequest {
    private String rtspUrl;
    private String username;
    private String password;

    public StreamRequest() {
    }

    public StreamRequest(String rtspUrl) {
        this.rtspUrl = rtspUrl;
    }

    public String getRtspUrl() {
        return rtspUrl;
    }

    public void setRtspUrl(String rtspUrl) {
        this.rtspUrl = rtspUrl;
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
}

