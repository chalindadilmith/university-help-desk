package com.helpdesk.config;

import com.helpdesk.security.CustomUserDetailsService;
import com.helpdesk.security.LoginAttemptService;
import com.helpdesk.security.LoginRateLimitFilter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
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
    private final LoginAttemptService loginAttemptService;

    public SecurityConfig(CustomUserDetailsService userDetailsService, LoginAttemptService loginAttemptService) {
        this.userDetailsService = userDetailsService;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Content-Security-Policy: only this site's own files (Font Awesome is served locally too).
     * 'unsafe-inline' is still needed for scripts/styles because the templates use inline
     * onclick handlers and style attributes; moving those into app.js/style.css would let
     * this be tightened further.
     */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline'",
            "font-src 'self' data:",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** BCrypt - the only acceptable password storage mechanism for this project. Never store plain text. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Knows every live session - used to show staff and admins who is logged in right now. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when a session ends (logout or timeout) so "online" stays accurate. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
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
                .requestMatchers("/", "/faq", "/login", "/register", "/forgot-password", "/reset-password", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/vendor/**", "/error/**").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/staff/**").hasRole("STAFF")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/reports", "/reports/**").hasAnyRole("STAFF", "ADMIN")
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
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                // X-Frame-Options: DENY, X-Content-Type-Options: nosniff and no-cache for pages are Spring Security defaults
            )
            // track sessions (unlimited per user) so we can list who is online
            .sessionManagement(session -> session
                .sessionConcurrency(concurrency -> concurrency
                    .maximumSessions(-1)
                    .sessionRegistry(sessionRegistry())))
            // refuse login attempts from an email/address that has failed too many times
            .addFilterBefore(new LoginRateLimitFilter(loginAttemptService), UsernamePasswordAuthenticationFilter.class)
            .userDetailsService(userDetailsService);

        return http.build();
    }
}
