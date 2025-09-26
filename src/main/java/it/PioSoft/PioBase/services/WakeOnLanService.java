/**
 * Servizio per l'invio di pacchetti Wake-on-LAN
 *
 * Implementa la funzionalità di accensione remota dei PC tramite:
 * - Creazione e invio di Magic Packet UDP
 * - Supporto per diversi formati di indirizzo MAC
 * - Configurazione dell'indirizzo di broadcast
 *
 * Il Magic Packet è composto da 6 byte 0xFF seguiti da 16 ripetizioni
 * dell'indirizzo MAC del PC target sulla porta UDP 9.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase.services;

import org.springframework.stereotype.Service;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Service
public class WakeOnLanService {

    public void sendWakeOnLan(String macAddress, String broadcastAddress) throws Exception {
        // Rimuovi separatori dal MAC address
        String cleanMac = macAddress.replaceAll("[:-]", "");

        // Crea il magic packet
        byte[] macBytes = hexStringToByteArray(cleanMac);
        byte[] magicPacket = new byte[6 + 16 * macBytes.length];

        // Riempi con 6 byte FF
        for (int i = 0; i < 6; i++) {
            magicPacket[i] = (byte) 0xFF;
        }

        // Ripeti il MAC address 16 volte
        for (int i = 6; i < magicPacket.length; i += macBytes.length) {
            System.arraycopy(macBytes, 0, magicPacket, i, macBytes.length);
        }

        // Invia il pacchetto
        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(
                magicPacket,
                magicPacket.length,
                InetAddress.getByName(broadcastAddress),
                9
        );
        socket.send(packet);
        socket.close();
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
