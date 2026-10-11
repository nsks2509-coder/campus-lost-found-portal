package com.campus.lostfound;
import jakarta.persistence.*;
@Entity @Table(name = "users")
public class AppUser {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String name;
  @Column(unique = true) public String email;
  public String password;
  public String role = "STUDENT";
}
