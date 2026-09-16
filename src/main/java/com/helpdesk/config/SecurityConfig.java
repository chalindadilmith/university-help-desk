package com.helpdesk.config;

import com.helpdesk.security.CustomUserDetailsService;
import com.helpdesk.security.RoleBasedAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Central security configuration.
 *
 * Session-based authentication (Spring Security's default) is used, NOT
 * JWT, per the project's mandated architecture. Spring Boot auto-builds a
 * DaoAuthenticationProvider for us because exactly one UserDetailsService
 * bean (CustomUserDetailsService) and one PasswordEncoder bean (below)
 * exist in the application context - we do not need to wire that by hand.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize on service/controller methods from Phase 5 onward
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /** BCrypt - the only acceptable password storage mechanism for this project. Never store plain text. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new RoleBasedAuthenticationSuccessHandler();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection stays ON (Spring Security default). Every Thymeleaf
            // <form th:action="@{...}"> automatically gets a hidden CSRF token
            // because thymeleaf-extras-springsecurity6 is on the classpath.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/images/**", "/error/**").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/staff/**").hasRole("STAFF")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/reports").hasAnyRole("STAFF", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")   // our login field is "email", not the default "username"
                .passwordParameter("password")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")          // must be a POST from a <form>, per Spring Security defaults
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .userDetailsService(userDetailsService);

        return http.build();
    }
}
