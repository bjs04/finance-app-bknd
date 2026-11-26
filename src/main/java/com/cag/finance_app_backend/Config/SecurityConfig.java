package com.cag.finance_app_backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.cag.finance_app_backend.Security.GoogleTokenFilter;
import com.cag.finance_app_backend.Services.AuthService;
import com.cag.finance_app_backend.Services.TokenBlacklistService;


import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    private final AuthService authService;
    private final TokenBlacklistService blacklistService;

    public SecurityConfig(AuthService authService, TokenBlacklistService blacklistService) {
        this.authService = authService;
        this.blacklistService = blacklistService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Enable CORS using the corsConfigurationSource bean defined below
            .cors(Customizer.withDefaults())
            // 2. Disable CSRF for stateless JWT/token APIs
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Allow all preflight OPTIONS requests across the entire API
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Allow verification endpoints and internal error fallbacks without authentication
                .requestMatchers("/verifyToken", "/verifyToken/**", "/api/verifyToken", "/api/verifyToken/**", "/error").permitAll()
                // Require authentication for everything else
                .anyRequest().authenticated()
            )
            .addFilterBefore(new GoogleTokenFilter(authService, blacklistService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow React/Vite frontend development server
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://192.168.10.34.nip.io:5173", "http://10.0.0.149.nip.io:5173"));
        // Allow all standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allow all headers (Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));
        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this CORS configuration to every endpoint in the backend
        source.registerCorsConfiguration("/**", config); 
        
        return source;
    }
}