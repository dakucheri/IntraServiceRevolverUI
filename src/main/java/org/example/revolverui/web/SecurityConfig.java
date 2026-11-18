package org.example.revolverui.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${APP_UI_USERNAME:admin}")
    private String legacyUsername;

    @Value("${APP_UI_PASSWORD:}")
    private String legacyPassword;

    @Value("${APP_UI_USERS:}")
    private String usersList; // формат: login:password:role;login:password:role

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();

        if (!usersList.isBlank()) {
            String[] parts = usersList.split(";");
            for (String p : parts) {
                String[] u = p.trim().split(":", 3);
                if (u.length == 3) {
                    String login = u[0].trim();
                    String pass = u[1].trim();
                    String role = u[2].trim().toUpperCase();

                    String springRole = switch (role) {
                        case "LINE1" -> "LINE1";
                        case "LINE2" -> "LINE2";
                        case "SUPERADMIN" -> "SUPERADMIN";
                        default -> "ADMIN";
                    };

                    users.add(User.builder()
                            .username(login)
                            .password(encoder.encode(pass))
                            .roles(springRole)
                            .build());
                }
            }
        } else {
            String rawPassword = (legacyPassword == null || legacyPassword.isBlank())
                    ? "changeme"
                    : legacyPassword;
            users.add(User.builder()
                    .username(legacyUsername)
                    .password(encoder.encode(rawPassword))
                    .roles("ADMIN")
                    .build());
        }

        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/l2/**").hasAnyRole("LINE2", "SUPERADMIN") // только LINE2 и SUPERADMIN
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}