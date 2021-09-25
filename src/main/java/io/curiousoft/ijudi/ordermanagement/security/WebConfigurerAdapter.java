package io.curiousoft.ijudi.ordermanagement.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
public class WebConfigurerAdapter extends WebMvcConfigurerAdapter {
	@Autowired
	AppVersionChecker appVersionChecker;
	
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appVersionChecker);
    }
}
