package com.printproof;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Security {
  @Bean
  UserDetailsService users(@Value("${app.admin.password}") String password) {
    if (password.length() < 16)
      throw new IllegalArgumentException("ADMIN_PASSWORD must contain at least 16 characters");
    return new InMemoryUserDetailsManager(
        User.withUsername("admin")
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
            .roles("ADMIN")
            .build());
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity h) throws Exception {
    return h.authorizeHttpRequests(
            a -> a.requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().permitAll())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/login")
                    .successHandler((q, r, a) -> r.setStatus(204))
                    .failureHandler((q, r, e) -> r.setStatus(401)))
        .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.sendError(401)))
        .headers(
            x ->
                x.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; img-src 'self' blob:; style-src 'self'"
                                + " 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'")))
        .build();
  }
}
