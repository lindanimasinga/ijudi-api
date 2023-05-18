package io.curiousoft.izinga.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@EnableSwagger2
@EnableMongoAuditing
@EnableScheduling
@EnableAsync
@SpringBootApplication(scanBasePackages = {"io.curiousoft.izinga.ordermanagement", "io.curiousoft.izinga.commons", "io.curiousoft.izinga.recon", "io.curiousoft.izinga.messaging"})
@EntityScan({"io.curiousoft.izinga.ordermanagement", "io.curiousoft.izinga.commons", "io.curiousoft.izinga.recon", "io.curiousoft.izinga.messaging"})
@EnableMongoRepositories({"io.curiousoft.izinga.ordermanagement", "io.curiousoft.izinga.commons", "io.curiousoft.izinga.recon", "io.curiousoft.izinga.messaging"})
public class IjudiApplication {

	public static void main(String[] args) {
		SpringApplication.run(IjudiApplication.class, args);
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

}
