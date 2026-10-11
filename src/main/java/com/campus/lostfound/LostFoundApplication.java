package com.campus.lostfound;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;

@SpringBootApplication
public class LostFoundApplication implements WebMvcConfigurer {
  public static void main(String[] a) { SpringApplication.run(LostFoundApplication.class, a); }
  public void addResourceHandlers(ResourceHandlerRegistry r) {
    r.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");
  }
  @Bean CommandLineRunner seed(UserRepository u, PasswordEncoder e) {
    return x -> { if (u.findByEmail("admin@campus.edu").isEmpty()) {
      AppUser a = new AppUser(); a.name = "Admin"; a.email = "admin@campus.edu";
      a.password = e.encode("anju@0603"); a.role = "ADMIN"; u.save(a); } };
  }
}
