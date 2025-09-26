package it.PioSoft.PioBase.services;

import org.springframework.stereotype.Service;
import java.net.InetAddress;

@Service
public class PcStatusService {

    public boolean isPcOnline(String ipAddress) {
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            // Timeout di 3 secondi
            return inet.isReachable(3000);
        } catch (Exception e) {
            return false;
        }
    }

    public String getPcStatus(String ipAddress) {
        return isPcOnline(ipAddress) ? "online" : "offline";
    }
}