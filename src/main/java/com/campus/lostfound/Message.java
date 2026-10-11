package com.campus.lostfound;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Message {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne public Claim claim;
  @ManyToOne public AppUser sender;
  @Column(length = 1000) public String text;
  public LocalDateTime createdAt = LocalDateTime.now();
}
