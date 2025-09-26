package it.PioSoft.PioBase.dto;

public class WolRequest {

    private String macAddress;
    private String broadcastAddress = "255.255.255.255"; // Valore di default

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getBroadcastAddress() {
        return broadcastAddress;
    }

    public void setBroadcastAddress(String broadcastAddress) {
        this.broadcastAddress = broadcastAddress;
    }
}
