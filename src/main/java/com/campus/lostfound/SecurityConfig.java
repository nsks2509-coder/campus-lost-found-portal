package com.campus.lostfound;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
  @Bean UserDetailsService uds(UserRepository r) {
    return e -> r.findByEmail(e).map(u -> User.withUsername(u.email).password(u.password).roles(u.role).build())
        .orElseThrow(() -> new UsernameNotFoundException(e));
  }
  @Bean SecurityFilterChain chain(HttpSecurity h) throws Exception {
    h.authorizeHttpRequests(a -> a
        .requestMatchers("/", "/login", "/register", "/history", "/uploads/**", "/items/{id:\\d+}").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())
     .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
     .logout(l -> l.logoutSuccessUrl("/"));
    return h.build();
  }
}
