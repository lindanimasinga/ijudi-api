package io.curiousoft.izinga.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import java.util.Optional ;
import java.util.TimeZone;

@EnableMongoAuditing
@EnableScheduling
@EnableAsync
@SpringBootApplication(scanBasePackages = {"io.curiousoft.izinga.ordermanagement",
		"io.curiousoft.izinga.commons",
		"io.curiousoft.izinga.recon",
		"io.curiousoft.izinga.messaging",
		"io.curiousoft.izinga.yocopay",
		"io.curiousoft.izinga.usermanagement",
		"io.curiousoft.izinga.qrcodegenerator",
		"io.curiousoft.izinga.documentmanagement"})
@EntityScan({"io.curiousoft.izinga.ordermanagement",
		"io.curiousoft.izinga.commons",
		"io.curiousoft.izinga.recon",
		"io.curiousoft.izinga.messaging",
		"io.curiousoft.izinga.yocopay",
		"io.curiousoft.izinga.usermanagement",
		"io.curiousoft.izinga.qrcodegenerator",
		"io.curiousoft.izinga.documentmanagement"})
@EnableMongoRepositories({"io.curiousoft.izinga.ordermanagement",
		"io.curiousoft.izinga.commons",
		"io.curiousoft.izinga.recon",
		"io.curiousoft.izinga.messaging",
		"io.curiousoft.izinga.yocopay",
		"io.curiousoft.izinga.usermanagement",
		"io.curiousoft.izinga.qrcodegenerator",
		"io.curiousoft.izinga.documentmanagement"})
@ConfigurationPropertiesScan({"io.curiousoft.izinga.ordermanagement",
		"io.curiousoft.izinga.yocopay",
		"io.curiousoft.izinga.messaging",
		"io.curiousoft.izinga.usermanagement",
		"io.curiousoft.izinga.qrcodegenerator",
		"io.curiousoft.izinga.documentmanagement"})
@EnableFeignClients({"io.curiousoft.izinga.ordermanagement",
		"io.curiousoft.izinga.commons",
		"io.curiousoft.izinga.recon",
		"io.curiousoft.izinga.messaging",
		"io.curiousoft.izinga.yocopay",
		"io.curiousoft.izinga.usermanagement",
		"io.curiousoft.izinga.qrcodegenerator",
		"io.curiousoft.izinga.documentmanagement"})
public class IjudiApplication {

	static Optional<ApplicationContext> app = Optional.empty();
	public static void main(String[] args) {
		app = Optional.of(SpringApplication.run(IjudiApplication.class, args));
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

}
