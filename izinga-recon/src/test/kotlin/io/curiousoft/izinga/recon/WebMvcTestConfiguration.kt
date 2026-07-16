package io.curiousoft.izinga.recon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Minimal Spring Boot test application for @WebMvcTest slices in izinga-recon.
 *
 * Uses @SpringBootApplication (which includes @ComponentScan) rather than
 * @SpringBootConfiguration alone, because @WebMvcTest requires @EnableAutoConfiguration
 * to activate MockMvcAutoConfiguration and its web-layer test slice. Without it, the
 * controller's request mappings are not registered and all paths return 404.
 *
 * The component scan is not a pollution risk here: @WebMvcTest's TypeExcludeFilter
 * restricts the scan to @Controller/@ControllerAdvice beans only; non-web beans
 * (services, repositories) are filtered out by the framework before loading.
 *
 * @EnableMethodSecurity is required so that @PreAuthorize annotations are enforced.
 * UserDetailsServiceAutoConfiguration is excluded to prevent Spring Security from
 * looking for a UserDetailsService bean that doesn't exist in the test context.
 */
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableMethodSecurity
class WebMvcTestConfiguration {

    @Bean
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth -> auth.anyRequest().authenticated() }
            .csrf { it.disable() }
        return http.build()
    }
}
