package com.campus.lostfound;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class ItemEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne public Item item;
  public String text;
  public LocalDateTime createdAt = LocalDateTime.now();
}
