package com.campus.lostfound;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class WebController {
  static final List<String> CATS = List.of("Electronics", "ID/Cards", "Books", "Clothing", "Keys", "Bags", "Other");
  private static final Set<String> HELD_AT_OPTIONS = Set.of(
      "With me, the finder", "Handed to the Security Office", "Handed to the department office");
  private final UserRepository users;
  private final ItemRepository items;
  private final ClaimRepository claims;
  private final EventRepository events;
  private final MessageRepository messages;
  private final PasswordEncoder enc;

  @Value("${app.expiry-days:30}") private int expiryDays;
  @Value("${app.upload.max-bytes:5242880}") private long maxUploadBytes;

  public WebController(UserRepository u, ItemRepository i, ClaimRepository c, EventRepository ev,
                       MessageRepository ms, PasswordEncoder e) {
    users = u; items = i; claims = c; events = ev; messages = ms; enc = e;
  }

  private AppUser me(Principal p) { return users.findByEmail(p.getName()).orElseThrow(); }
  private void log(Item i, String t) { ItemEvent e = new ItemEvent(); e.item = i; e.text = t; events.save(e); }
  private boolean isAdmin(AppUser u) { return "ADMIN".equals(u.role); }
  private boolean atOffice(Item i) { return i.heldAt != null && i.heldAt.startsWith("Handed to"); }
  private boolean isParty(AppUser u, Claim c) { return isAdmin(u) || u.id.equals(c.claimant.id) || u.id.equals(c.item.owner.id); }
  private boolean canDecide(AppUser u, Claim c) { return isAdmin(u) || (!atOffice(c.item) && u.id.equals(c.item.owner.id)); }

  private boolean canReceive(AppUser u, Item i) {
    if (!"VERIFIED".equals(i.status)) return false;
    if (isAdmin(u)) return true;
    Optional<Claim> a = claims.findByItemAndStatus(i, "APPROVED").stream().findFirst();
    if (a.isEmpty()) return false;
    return "FOUND".equals(i.type) ? u.id.equals(a.get().claimant.id) : u.id.equals(i.owner.id);
  }

  private Map<Long, Claim> approvedFor(List<Item> list) {
    Map<Long, Claim> m = new HashMap<>();
    for (Item i : list) claims.findByItemAndStatus(i, "APPROVED").stream().findFirst().ifPresent(c -> m.put(i.id, c));
    return m;
  }

  private String requiredText(String value, String field, int maxLength) {
    String clean = value == null ? "" : value.trim();
    if (clean.isEmpty() || clean.length() > maxLength) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          field + " is required and must be at most " + maxLength + " characters.");
    }
    return clean;
  }

  private String normalizeEmail(String email) {
    String clean = requiredText(email, "Email", 160).toLowerCase(Locale.ROOT);
    if (!clean.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
    }
    return clean;
  }

  private String saveVerifiedImage(MultipartFile photo) throws IOException {
    if (photo.getSize() > maxUploadBytes) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 5 MB or smaller.");
    }

    try (ImageInputStream input = ImageIO.createImageInputStream(photo.getInputStream())) {
      if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is not a valid image.");
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or GIF images are supported.");

      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String extension = switch (format) {
          case "jpeg", "jpg" -> "jpg";
          case "png" -> "png";
          case "gif" -> "gif";
          default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or GIF images are supported.");
        };

        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > 6000 || height > 6000 || (long) width * height > 20_000_000L) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image dimensions are too large.");
        }

        BufferedImage decoded = reader.read(0);
        if (decoded == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image could not be decoded.");

        // Re-encode rather than serving untrusted uploaded bytes. This strips
        // metadata and prevents a renamed non-image payload from being stored.
        BufferedImage safeImage = decoded;
        if ("jpg".equals(extension) && decoded.getType() != BufferedImage.TYPE_INT_RGB) {
          safeImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
          Graphics2D graphics = safeImage.createGraphics();
          try { graphics.drawImage(decoded, 0, 0, null); } finally { graphics.dispose(); }
        }

        Path uploadDir = Path.of("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        String filename = UUID.randomUUID() + "." + extension;
        Path destination = uploadDir.resolve(filename).normalize();
        if (!destination.startsWith(uploadDir)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload destination.");
        }
        if (!ImageIO.write(safeImage, extension.equals("jpg") ? "jpeg" : extension, destination.toFile())) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image could not be saved.");
        }
        return "/uploads/" + filename;
      } finally {
        reader.dispose();
      }
    }
  }

  @GetMapping("/")
  String home(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String category,
              @RequestParam(defaultValue = "") String type, Model m) {
    m.addAttribute("items", items.search(q, category, type)); m.addAttribute("cats", CATS);
    m.addAttribute("q", q); m.addAttribute("category", category); m.addAttribute("type", type);
    return "index";
  }

  @GetMapping("/login") String login() { return "login"; }
  @GetMapping("/register") String reg() { return "register"; }

  @PostMapping("/register")
  String doReg(@RequestParam String name, @RequestParam String email, @RequestParam String password, Model m) {
    String cleanName = requiredText(name, "Name", 80);
    if (cleanName.length() < 2) {
      m.addAttribute("error", "Name must contain at least 2 characters.");
      return "register";
    }
    String cleanEmail = normalizeEmail(email);
    if (password == null || password.length() < 10 || password.length() > 72) {
      m.addAttribute("error", "Password must contain between 10 and 72 characters.");
      return "register";
    }
    if (users.findByEmail(cleanEmail).isPresent()) {
      m.addAttribute("error", "This email is already registered. Log in instead.");
      return "register";
    }
    AppUser u = new AppUser(); u.name = cleanName; u.email = cleanEmail; u.password = enc.encode(password); users.save(u);
    return "redirect:/login";
  }

  @GetMapping("/history")
  String history(Model m) {
    m.addAttribute("items", items.findByStatusOrderByReturnedAtDesc("RETURNED"));
    return "history";
  }

  @GetMapping("/items/new")
  String newItem(Model m) { m.addAttribute("cats", CATS); return "new"; }

  @PostMapping("/items")
  String save(@RequestParam String title, @RequestParam String description, @RequestParam String category,
              @RequestParam String type, @RequestParam String location,
              @RequestParam(defaultValue = "") String heldAt,
              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
              @RequestParam(required = false) MultipartFile photo, Principal p) throws IOException {
    String cleanTitle = requiredText(title, "Title", 120);
    String cleanDescription = requiredText(description, "Description", 1000);
    String cleanCategory = requiredText(category, "Category", 40);
    String cleanType = requiredText(type, "Type", 8).toUpperCase(Locale.ROOT);
    String cleanLocation = requiredText(location, "Location", 120);
    if (!CATS.contains(cleanCategory)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid category.");
    if (!Set.of("LOST", "FOUND").contains(cleanType)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type must be LOST or FOUND.");
    String cleanHeldAt = "";
    if ("FOUND".equals(cleanType)) {
      cleanHeldAt = requiredText(heldAt, "Current item location", 80);
      if (!HELD_AT_OPTIONS.contains(cleanHeldAt)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid item handover location.");
      }
    }
    if (eventDate == null || eventDate.isAfter(LocalDate.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event date cannot be in the future.");
    }

    Item i = new Item(); i.title = cleanTitle; i.description = cleanDescription; i.category = cleanCategory;
    i.type = cleanType; i.location = cleanLocation; i.eventDate = eventDate; i.owner = me(p);
    i.heldAt = "FOUND".equals(cleanType) ? cleanHeldAt : null;
    if (photo != null && !photo.isEmpty()) i.imagePath = saveVerifiedImage(photo);
    items.save(i);
    log(i, ("LOST".equals(cleanType) ? "Lost report posted by " : "Found report posted by ")
        + i.owner.name + ("FOUND".equals(cleanType) ? ". Item is: " + i.heldAt : ""));
    return "redirect:/items/" + i.id;
  }

  @GetMapping("/items/{id}")
  String detail(@PathVariable Long id, Principal p, Model m) {
    Item i = items.findById(id).orElseThrow(); m.addAttribute("item", i); m.addAttribute("events", events.findByItemOrderByIdAsc(i));
    boolean mine = false, can = false, rec = false;
    if (p != null) {
      AppUser u = me(p); mine = u.id.equals(i.owner.id); rec = canReceive(u, i);
      can = !mine && ("OPEN".equals(i.status) || "CLAIM_PENDING".equals(i.status)) && !claims.existsByItemAndClaimant(i, u);
    }
    if (mine && "FOUND".equals(i.type)) m.addAttribute("matches", items.search("", i.category, "LOST").stream().limit(3).toList());
    m.addAttribute("mine", mine); m.addAttribute("canClaim", can); m.addAttribute("canReceive", rec);
    return "detail";
  }

  @PostMapping("/items/{id}/claim")
  String claim(@PathVariable Long id, @RequestParam String message, Principal p) {
    Item i = items.findById(id).orElseThrow(); AppUser u = me(p);
    String proof = requiredText(message, "Proof message", 1000);
    if (proof.length() < 20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof message must contain at least 20 characters.");
    boolean open = "OPEN".equals(i.status) || "CLAIM_PENDING".equals(i.status);
    if (open && !i.owner.id.equals(u.id) && !claims.existsByItemAndClaimant(i, u)) {
      Claim c = new Claim(); c.item = i; c.claimant = u; c.message = proof; claims.save(c);
      i.status = "CLAIM_PENDING"; items.save(i);
      log(i, u.name + ("FOUND".equals(i.type) ? " says this item is theirs and sent proof" : " says they found this item"));
      return "redirect:/claims/" + c.id;
    }
    return "redirect:/my";
  }

  @PostMapping("/items/{id}/location")
  String location(@PathVariable Long id, @RequestParam String heldAt, Principal p) {
    Item i = items.findById(id).orElseThrow();
    String cleanHeldAt = requiredText(heldAt, "Current item location", 80);
    if ("FOUND".equals(i.type) && i.owner.id.equals(me(p).id) && !Set.of("RETURNED", "CLOSED").contains(i.status)) {
      if (HELD_AT_OPTIONS.contains(cleanHeldAt)) {
        i.heldAt = cleanHeldAt; items.save(i); log(i, "Finder updated where the item is: " + cleanHeldAt);
      }
    }
    return "redirect:/items/" + id;
  }

  @PostMapping("/items/{id}/close")
  String close(@PathVariable Long id, Principal p) {
    Item i = items.findById(id).orElseThrow();
    if (i.owner.id.equals(me(p).id) && "OPEN".equals(i.status)) {
      i.status = "CLOSED"; items.save(i); log(i, "Report closed by " + i.owner.name);
    }
    return "redirect:/my";
  }

  @GetMapping("/claims/{id}")
  String claimPage(@PathVariable Long id, Principal p, Model m) {
    AppUser u = me(p); Claim c = claims.findById(id).orElseThrow();
    if (!isParty(u, c)) return "redirect:/my";
    m.addAttribute("claim", c); m.addAttribute("messages", messages.findByClaimOrderByIdAsc(c));
    m.addAttribute("canDecide", canDecide(u, c) && "PENDING".equals(c.status));
    m.addAttribute("canReceive", "APPROVED".equals(c.status) && canReceive(u, c.item));
    return "claim";
  }

  @PostMapping("/claims/{id}/chat")
  String chat(@PathVariable Long id, @RequestParam String text, Principal p) {
    AppUser u = me(p); Claim c = claims.findById(id).orElseThrow();
    String cleanText = requiredText(text, "Message", 2000);
    if (isParty(u, c)) { Message x = new Message(); x.claim = c; x.sender = u; x.text = cleanText; messages.save(x); }
    return "redirect:/claims/" + id;
  }

  @PostMapping("/claims/{id}/{action}")
  String decide(@PathVariable Long id, @PathVariable String action,
                @RequestParam(defaultValue = "") String note, Principal p) {
    if (!Set.of("approve", "reject").contains(action)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown claim action.");
    }
    AppUser u = me(p); Claim c = claims.findById(id).orElseThrow(); Item i = c.item;
    if (!canDecide(u, c) || !"PENDING".equals(c.status)) return "redirect:/my";
    String who = isAdmin(u) ? "Admin" : u.name; String n = note.isBlank() ? "" : " Note: " + note;
    c.adminNote = note.isBlank() ? null : note.trim().substring(0, Math.min(note.trim().length(), 500));
    c.decidedAt = LocalDateTime.now();
    if ("approve".equals(action)) {
      c.status = "APPROVED"; i.status = "VERIFIED"; claims.save(c);
      for (Claim o : claims.findByItemAndStatus(i, "PENDING")) {
        o.status = "REJECTED"; o.adminNote = "Another claim was verified for this item."; claims.save(o);
      }
      log(i, who + " verified the proof from " + c.claimant.name + ". They can now arrange the handover in chat." + n);
    } else {
      c.status = "REJECTED"; claims.save(c); log(i, who + " rejected a claim from " + c.claimant.name + "." + n);
      if (claims.findByItemAndStatus(i, "PENDING").isEmpty() && "CLAIM_PENDING".equals(i.status)) i.status = "OPEN";
    }
    items.save(i); return "redirect:/claims/" + id;
  }

  @PostMapping("/items/{id}/received")
  String received(@PathVariable Long id, Principal p) {
    AppUser u = me(p); Item i = items.findById(id).orElseThrow();
    if (canReceive(u, i)) {
      i.status = "RETURNED"; i.returnedAt = LocalDateTime.now(); items.save(i);
      log(i, (isAdmin(u) ? "Admin recorded the handover." : "Owner confirmed they got the item back.") + " Case closed.");
    }
    return isAdmin(u) ? "redirect:/admin" : "redirect:/my";
  }

  @GetMapping("/my")
  String my(Principal p, Model m) {
    AppUser u = me(p); List<Item> mine = items.findByOwnerOrderByIdDesc(u);
    m.addAttribute("items", mine); m.addAttribute("approved", approvedFor(mine)); m.addAttribute("claims", claims.findByClaimantOrderByIdDesc(u));
    m.addAttribute("incoming", claims.findByItem_OwnerAndStatusOrderByIdDesc(u, "PENDING").stream().filter(c -> !atOffice(c.item)).toList());
    return "my";
  }

  @GetMapping("/admin")
  String admin(Model m) {
    List<Item> ready = items.findByStatusOrderByIdDesc("VERIFIED").stream().filter(this::atOffice).toList();
    m.addAttribute("claims", claims.findByStatusOrderByIdDesc("PENDING").stream().filter(c -> atOffice(c.item)).toList());
    m.addAttribute("ready", ready); m.addAttribute("approved", approvedFor(ready));
    m.addAttribute("stale", items.findByStatusAndCreatedAtBefore("OPEN", LocalDateTime.now().minusDays(expiryDays)));
    m.addAttribute("days", expiryDays);
    return "admin";
  }

  @PostMapping("/admin/items/{id}/expire")
  String expire(@PathVariable Long id, Principal p) {
    if (isAdmin(me(p))) {
      Item i = items.findById(id).orElseThrow(); i.status = "CLOSED"; items.save(i);
      log(i, "Admin closed this report as unclaimed after " + expiryDays + " days.");
    }
    return "redirect:/admin";
  }
}
