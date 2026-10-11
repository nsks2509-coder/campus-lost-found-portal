package com.campus.lostfound;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Claim {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne public Item item;
  @ManyToOne public AppUser claimant;
  @Column(length = 1000) public String message;
  public String status = "PENDING"; // PENDING / APPROVED / REJECTED
  public LocalDateTime createdAt = LocalDateTime.now();
  public String adminNote;
  public LocalDateTime decidedAt;
}
