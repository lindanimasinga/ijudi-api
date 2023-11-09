package io.curiousoft.izinga.ordermanagement.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Autowired
    AppVersionChecker appVersionChecker;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appVersionChecker);
    }
}