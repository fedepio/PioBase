/**
 * Servizio per la gestione delle informazioni sulla versione dell'applicazione
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-10-02
 */
package it.PioSoft.PioBase.services;

import it.PioSoft.PioBase.dto.VersionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VersionService {

    @Value("${info.app.version:unknown}")
    private String version;

    @Value("${info.app.name:PioBase}")
    private String name;

    @Value("${info.app.description:Utilities per casa Pio}")
    private String description;

    /**
     * Restituisce le informazioni sulla versione dell'applicazione
     *
     * @return VersionResponse con le informazioni sulla versione
     */
    public VersionResponse getVersionInfo() {
        return new VersionResponse(version, name, description);
    }
}
