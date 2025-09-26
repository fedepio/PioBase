package it.PioSoft.PioBase.services;

import it.PioSoft.PioBase.configs.PcMappingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PcManagementService {

    @Autowired
    private PcMappingConfig pcMappingConfig;

    @Autowired
    private PcStatusService pcStatusService;

    public String getIpByMac(String macAddress) {
        String cleanMac = macAddress.toLowerCase().replace(":", "").replace("-", "");
        return pcMappingConfig.getIpByMac(cleanMac);
    }

    public boolean isPcOnlineByMac(String macAddress) {
        String ip = getIpByMac(macAddress);
        return ip != null && pcStatusService.isPcOnline(ip);
    }
}