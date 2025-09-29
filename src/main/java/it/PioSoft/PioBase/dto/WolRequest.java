/**
 * Data Transfer Object per richieste di controllo PC remoto
 *
 * Rappresenta i dati necessari per le operazioni sui PC remoti:
 * - macAddress: indirizzo MAC del PC target (formato AA:BB:CC:DD:EE:FF)
 * - broadcastAddress: indirizzo di broadcast per Wake-on-LAN (default: 255.255.255.255)
 *
 * Utilizzato per tutte le operazioni di controllo PC:
 * Wake-on-LAN, spegnimento remoto e controllo stato.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase.dto;

public class WolRequest {

    private String macAddress;
    private String broadcastAddress = "255.255.255.255"; // Valore di default
    private String ipAddress; // Aggiunto campo IPAddress
    private String pin; // PIN di accesso Windows

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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
