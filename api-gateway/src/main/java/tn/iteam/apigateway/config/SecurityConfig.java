package tn.iteam.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})  // important: hook CorsWebFilter
                .authorizeExchange(exchanges -> exchanges
                        //.antMatchers("/api/categories/**").permitAll()
                        .anyExchange().permitAll() // let microservices handle auth
                );

        return http.build();
    }
}

