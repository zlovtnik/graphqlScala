package com.example.ssf.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Value("${security.password.bcrypt.strength:10}")
    private int bcryptStrength;

    @PostConstruct
    public void validateBcryptStrength() {
        if (bcryptStrength < 4 || bcryptStrength > 31) {
            throw new IllegalStateException(
                    "security.password.bcrypt.strength must be between 4 and 31, got: " + bcryptStrength);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }
}
