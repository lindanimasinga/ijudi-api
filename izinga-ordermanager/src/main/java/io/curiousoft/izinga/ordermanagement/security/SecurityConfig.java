package io.curiousoft.izinga.ordermanagement.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.http.HttpMethod.GET;

@Configuration
public class SecurityConfig {

    private final ApiVersionRewriteFilter apiVersionRewriteFilter = new ApiVersionRewriteFilter();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .authorizeHttpRequests()
                .regexMatchers(GET, "(.*\\/v2\\/promotion.*) ", "(.*\\/v2\\/store.*)").permitAll()
                .regexMatchers("^/v2/.*").authenticated()
                .anyRequest().permitAll()
                .and()
                .oauth2ResourceServer().jwt();
                //.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
                //.addFilterAfter(apiVersionRewriteFilter, SecurityFilterChain.class);
        return http.build();
    }

/*    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "https://pingembo-barcode-scanner-app.web.app",
                "https://login-474a9.web.app/",
                "https://api.izinga.co.za",
                "https://pay.izinga.co.za",
                "https://shop.izinga.co.za",
                "https://izinga.store",
                "https://admin.izinga.co.za",
                "https://onboard.izinga.co.za",
                "http://localhost:4200"
        ));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }*/

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedHeaders("*")
                        .allowedMethods("*");
            }
        };
    }

    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.getDecodedUrlBlocklist().remove("//");
        return firewall;
    }

/*    @Bean
    static RoleHierarchy roleHierarchy() {
        return new RoleHierarchyImpl.builder()
                .role("ADMIN").implies("STAFF")
                .role("STAFF").implies("USER")
                .role("USER").implies("GUEST")
                .build();
    }*/

    @Configuration
    public class OpenApiConfig {
        @Bean
        public OpenAPI customOpenAPI() {
            final String securitySchemeName = "bearerAuth";
            return new OpenAPI()
                    .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                    .components(new Components().addSecuritySchemes(securitySchemeName,
                            new SecurityScheme()
                                    .name(securitySchemeName)
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT")))
                    .info(new Info().title("My API").version("1.0"));
        }
    }
}