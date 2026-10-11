package com.campus.lostfound;
import jakarta.persistence.*;
import java.time.*;
@Entity
public class Item {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String title;
  @Column(length = 1000) public String description;
  public String category, type, location, imagePath, heldAt;
  public LocalDate eventDate;
  public String status = "OPEN"; // OPEN, CLAIM_PENDING, VERIFIED, RETURNED, CLOSED
  @ManyToOne public AppUser owner;
  public LocalDateTime createdAt = LocalDateTime.now();
  public LocalDateTime returnedAt;
  public String statusLabel() {
    return switch (status) { case "CLAIM_PENDING" -> "Claim pending"; case "VERIFIED" -> "Ready for handover"; case "RETURNED" -> "Returned"; case "CLOSED" -> "Closed"; default -> "Open"; };
  }
  public String typeLabel() { return "LOST".equals(type) ? "Lost" : "Found"; }
  public int step() { return switch (status) { case "CLAIM_PENDING" -> 2; case "VERIFIED" -> 3; case "RETURNED" -> 4; default -> 1; }; }
}
