package com.campus.lostfound;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;

import java.util.Locale;

@SpringBootApplication
public class LostFoundApplication implements WebMvcConfigurer {
  public static void main(String[] a) { SpringApplication.run(LostFoundApplication.class, a); }

  public void addResourceHandlers(ResourceHandlerRegistry r) {
    r.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");
  }

  @Bean
  CommandLineRunner seed(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${app.admin.email:}") String adminEmail,
      @Value("${app.admin.password:}") String adminPassword) {
    return args -> {
      if (adminEmail.isBlank() && adminPassword.isBlank()) return;
      if (adminEmail.isBlank() || adminPassword.isBlank()) {
        throw new IllegalStateException("Configure both ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap an administrator.");
      }
      if (adminPassword.length() < 12) {
        throw new IllegalStateException("ADMIN_PASSWORD must contain at least 12 characters.");
      }

      String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
      AppUser admin = users.findByEmail(normalizedEmail).orElseGet(AppUser::new);
      if (admin.name == null || admin.name.isBlank()) admin.name = "Admin";
      admin.email = normalizedEmail;
      admin.password = encoder.encode(adminPassword);
      admin.role = "ADMIN";
      users.save(admin);
    };
  }
}
