package io.curiousoft.izinga.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.HandlerExceptionResolver;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.TimeZone;

@EnableSwagger2
@EnableMongoAuditing
@EnableScheduling
@EnableAsync
@SpringBootApplication(scanBasePackages = {"io.curiousoft.izinga.ordermanagement", "io.curiousoft.izinga.commons", "io.curiousoft.izinga.recon", "io.curiousoft.izinga.messaging"})
@EntityScan({"io.curiousoft.izinga.ordermanagement", "io.curiousoft.izinga.commons", "io.curiousoft.izinga.recon", "io.curiousoft.izinga.messaging"})
@EnableMongoRepositories({"io.curiousoft.izinga.ordermanagement", "io.curiousoft.izinga.commons", "io.curiousoft.izinga.recon", "io.curiousoft.izinga.messaging"})
public class IjudiApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(IjudiApplication.class, args);
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}


	/*
	 * optimization - avoids creating default exception resolvers; not required as the serverless container handles
	 * all exceptions
	 *
	 * By default, an ExceptionHandlerExceptionResolver is created which creates many dependent object, including
	 * an expensive ObjectMapper instance.
	 *
	 * To enable custom @ControllerAdvice classes remove this bean.
	 */
	@Bean
	public HandlerExceptionResolver overrideHandlerExceptionResolver() {
		return (HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) -> null;
	}

}
