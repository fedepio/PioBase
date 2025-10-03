package it.PioSoft.PioBase.dto;

/**
 * DTO per richiesta scansione rete IP cam
 */
public class CamScanRequest {
    private String subnet; // Es: "192.168.1"

    public CamScanRequest() {
    }

    public CamScanRequest(String subnet) {
        this.subnet = subnet;
    }

    public String getSubnet() {
        return subnet;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }
}