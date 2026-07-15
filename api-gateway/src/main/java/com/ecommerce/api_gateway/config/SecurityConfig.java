package com.ecommerce.api_gateway.config;

import com.ecommerce.api_gateway.enums.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity serverHttpSecurity) {

        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorizeExchangeSpec -> authorizeExchangeSpec

                        .pathMatchers("/eureka/**").permitAll()

                        .pathMatchers(HttpMethod.GET,"/api/products/**").permitAll()
                        .pathMatchers(HttpMethod.GET,"/api/inventory/**").permitAll()
                        .pathMatchers("/api/products/**").hasRole(Role.ADMIN.name())
                        .pathMatchers("/api/inventory/**").hasRole(Role.ADMIN.name())

                        .pathMatchers(HttpMethod.POST, "/api/order").hasRole(Role.USER.name())

                        .pathMatchers(HttpMethod.GET, "/api/order/**").hasAnyRole(Role.ADMIN.name(), Role.USER.name())
                        .pathMatchers(HttpMethod.DELETE, "/api/order/**").hasRole(Role.ADMIN.name())
                        .pathMatchers(HttpMethod.PUT,"/api/order/**").hasRole(Role.ADMIN.name())


                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Utilizamos el convertidor
                        .jwt(jwt-> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverterAdapter())));

        return serverHttpSecurity.build();

    }

    private ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverterAdapter() {
        // Creamos el convertidor
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");

            // Validamos si es vacio
            if(realmAccess==null || realmAccess.isEmpty()) {
                return Collections.emptyList();
            }

            Collection<String> roles = (Collection<String>) realmAccess.get("roles");

            // Transformamos a ROLE_ADMIN - ROLE_USER, ...
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

        });

        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }


}
