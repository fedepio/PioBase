/**
 * PioBase - Sistema di controllo remoto per PC tramite API REST
 *
 * Classe principale dell'applicazione Spring Boot che fornisce API per:
 * - Wake-on-LAN per accendere PC da remoto
 * - Spegnimento remoto tramite SSH
 * - Controllo dello stato dei PC (online/offline)
 *
 * @author Federico
 * @email feder@piosoft.it
 * @license MIT License
 * @version 1.0
 * @since 2024-09-26
 */
package it.PioSoft.PioBase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PioBaseApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(PioBaseApplication.class);
		app.setDefaultProperties(java.util.Collections.singletonMap("server.address", "0.0.0.0"));
		app.run(args);
	}

}
