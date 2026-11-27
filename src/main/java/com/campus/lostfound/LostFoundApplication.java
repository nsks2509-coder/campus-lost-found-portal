package com.campus.lostfound;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

@SpringBootApplication
public class LostFoundApplication implements WebMvcConfigurer {
  public static void main(String[] args) {
    SpringApplication.run(LostFoundApplication.class, args);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");
  }

  @Bean
  CommandLineRunner seedAdmin(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${app.admin.email:}") String adminEmail,
      @Value("${app.admin.password:}") String adminPassword,
      @Value("${app.admin.name:Campus Administrator}") String adminName,
      @Value("${app.admin.legacy-email:admin@campus.edu}") String legacyAdminEmail) {
    return args -> {
      String cleanEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
      String cleanPassword = adminPassword == null ? "" : adminPassword;
      if (cleanEmail.isEmpty() || cleanPassword.isEmpty()) {
        // Disable the old sample administrator account on existing databases.
        // Configure ADMIN_EMAIL and ADMIN_PASSWORD to provision a real admin.
        users.findByEmail(legacyAdminEmail).filter(user -> "ADMIN".equals(user.role)).ifPresent(user -> {
          user.password = encoder.encode(UUID.randomUUID().toString());
          user.role = "STUDENT";
          users.save(user);
        });
        return;
      }
      if (cleanPassword.length() < 12 || cleanPassword.length() > 72) {
        throw new IllegalStateException("ADMIN_PASSWORD must contain between 12 and 72 characters.");
      }
      AppUser admin = users.findByEmail(cleanEmail).orElseGet(AppUser::new);
      admin.name = adminName == null || adminName.isBlank() ? "Campus Administrator" : adminName.trim();
      admin.email = cleanEmail;
      admin.password = encoder.encode(cleanPassword);
      admin.role = "ADMIN";
      users.save(admin);
    };
  }
}
