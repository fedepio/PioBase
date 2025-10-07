/**
 * Servizio di gestione centralizzata per i PC remoti
 *
 * Fornisce funzionalità di alto livello per la gestione dei PC tramite:
 * - Mappatura MAC address -> IP address da configurazione
 * - Integrazione con servizi di stato e controllo PC
 * - Normalizzazione e pulizia degli indirizzi MAC
 *
 * Funge da layer di astrazione tra i controller e i servizi
 * specifici, gestendo la conversione da MAC a IP e coordinando
 * le operazioni sui PC remoti.
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
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
        String ip = pcMappingConfig.getIpByMac(cleanMac);

        return ip;
    }

    public boolean isPcOnlineByMac(String macAddress) {
        String ip = getIpByMac(macAddress);
        return ip != null && pcStatusService.isPcOnline(ip);
    }
}