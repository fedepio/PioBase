package it.PioSoft.PioBase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PioBaseApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(PioBaseApplication.class);
		app.setDefaultProperties(java.util.Collections.singletonMap("server.address", "0.0.0.0"));
		app.run(args);
	}

}
