/**
 * DTO per la risposta della versione del servizio
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-10-02
 */
package it.PioSoft.PioBase.dto;

public class VersionResponse {
    private String version;
    private String name;
    private String description;
    private long timestamp;

    public VersionResponse() {}

    public VersionResponse(String version, String name, String description) {
        this.version = version;
        this.name = name;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
