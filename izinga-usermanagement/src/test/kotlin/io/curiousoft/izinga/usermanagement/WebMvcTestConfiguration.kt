package io.curiousoft.izinga.usermanagement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Minimal Spring Boot test application for @WebMvcTest slices in izinga-usermanagement.
 *
 * See izinga-recon's WebMvcTestConfiguration for the design rationale.
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
