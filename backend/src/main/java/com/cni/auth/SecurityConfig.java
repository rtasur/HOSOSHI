package com.cni.auth;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import org.springframework.security.core.GrantedAuthority;
import java.util.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
    @Bean JwtDecoder jwtDecoder(JwtService s){return s.decoder();}
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder)throws Exception{
        http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/v1/auth/**","/api/v1/health","/actuator/health","/swagger-ui/**","/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }
    private JwtAuthenticationConverter jwtAuthenticationConverter(){
        var c=new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt->{
            var roles=jwt.getClaimAsStringList("roles");
            if(roles==null) return List.<GrantedAuthority>of();
            return roles.stream().map(r -> (GrantedAuthority)new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+r)).toList();
        });
        return c;
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin:http://localhost:5173}") String origin){
        var c=new CorsConfiguration(); c.setAllowedOrigins(List.of(origin)); c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("Authorization","Content-Type","Accept")); c.setAllowCredentials(true);
        var s=new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**",c); return s;
    }
}
