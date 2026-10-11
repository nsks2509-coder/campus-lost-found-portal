package com.campus.lostfound;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.time.*;
import java.util.*;

@Controller
public class WebController {
  static final List<String> CATS = List.of("Electronics", "ID/Cards", "Books", "Clothing", "Keys", "Bags", "Other");
  private final UserRepository users; private final ItemRepository items; private final ClaimRepository claims;
  private final EventRepository events; private final MessageRepository messages; private final PasswordEncoder enc;
  @Value("${app.expiry-days:30}") private int expiryDays;
  public WebController(UserRepository u, ItemRepository i, ClaimRepository c, EventRepository ev, MessageRepository ms, PasswordEncoder e) {
    users = u; items = i; claims = c; events = ev; messages = ms; enc = e; }

  private AppUser me(Principal p) { return users.findByEmail(p.getName()).orElseThrow(); }
  private void log(Item i, String t) { ItemEvent e = new ItemEvent(); e.item = i; e.text = t; events.save(e); }
  private boolean isAdmin(AppUser u) { return "ADMIN".equals(u.role); }
  private boolean atOffice(Item i) { return i.heldAt != null && i.heldAt.startsWith("Handed to"); }
  private boolean isParty(AppUser u, Claim c) { return isAdmin(u) || u.id.equals(c.claimant.id) || u.id.equals(c.item.owner.id); }
  // Whoever physically holds the item checks the proof: the finder, or the admin if it is at an office.
  private boolean canDecide(AppUser u, Claim c) { return isAdmin(u) || (!atOffice(c.item) && u.id.equals(c.item.owner.id)); }
  // Only the rightful owner (or admin at an office) confirms the item is back.
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

  @GetMapping("/") String home(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String category,
                               @RequestParam(defaultValue = "") String type, Model m) {
    m.addAttribute("items", items.search(q, category, type)); m.addAttribute("cats", CATS);
    m.addAttribute("q", q); m.addAttribute("category", category); m.addAttribute("type", type);
    return "index";
  }
  @GetMapping("/login") String login() { return "login"; }
  @GetMapping("/register") String reg() { return "register"; }
  @PostMapping("/register") String doReg(@RequestParam String name, @RequestParam String email, @RequestParam String password, Model m) {
    if (users.findByEmail(email).isPresent()) { m.addAttribute("error", "This email is already registered. Log in instead."); return "register"; }
    AppUser u = new AppUser(); u.name = name; u.email = email; u.password = enc.encode(password); users.save(u);
    return "redirect:/login";
  }
  @GetMapping("/history") String history(Model m) { m.addAttribute("items", items.findByStatusOrderByReturnedAtDesc("RETURNED")); return "history"; }
  @GetMapping("/items/new") String newItem(Model m) { m.addAttribute("cats", CATS); return "new"; }
  @PostMapping("/items") String save(@RequestParam String title, @RequestParam String description, @RequestParam String category,
      @RequestParam String type, @RequestParam String location, @RequestParam(defaultValue = "") String heldAt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
      @RequestParam(required = false) MultipartFile photo, Principal p) throws IOException {
    Item i = new Item(); i.title = title; i.description = description; i.category = category; i.type = type;
    i.location = location; i.eventDate = eventDate; i.owner = me(p);
    i.heldAt = "FOUND".equals(type) ? (heldAt.isBlank() ? "With the finder" : heldAt) : null;
    if (photo != null && !photo.isEmpty() && photo.getContentType() != null && photo.getContentType().startsWith("image/")) {
      String n = photo.getOriginalFilename(); String ext = n != null && n.contains(".") ? n.substring(n.lastIndexOf('.')).replaceAll("[^.a-zA-Z0-9]", "") : ".jpg";
      String f = UUID.randomUUID() + ext; Files.createDirectories(Path.of("uploads"));
      photo.transferTo(Path.of("uploads").resolve(f).toAbsolutePath()); i.imagePath = "/uploads/" + f;
    }
    items.save(i);
    log(i, ("LOST".equals(type) ? "Lost report posted by " : "Found report posted by ") + i.owner.name + ("FOUND".equals(type) ? ". Item is: " + i.heldAt : ""));
    return "redirect:/items/" + i.id;
  }
  @GetMapping("/items/{id}") String detail(@PathVariable Long id, Principal p, Model m) {
    Item i = items.findById(id).orElseThrow(); m.addAttribute("item", i); m.addAttribute("events", events.findByItemOrderByIdAsc(i));
    boolean mine = false, can = false, rec = false;
    if (p != null) { AppUser u = me(p); mine = u.id.equals(i.owner.id); rec = canReceive(u, i);
      can = !mine && ("OPEN".equals(i.status) || "CLAIM_PENDING".equals(i.status)) && !claims.existsByItemAndClaimant(i, u); }
    if (mine && "FOUND".equals(i.type)) m.addAttribute("matches", items.search("", i.category, "LOST").stream().limit(3).toList());
    m.addAttribute("mine", mine); m.addAttribute("canClaim", can); m.addAttribute("canReceive", rec); return "detail";
  }
  @PostMapping("/items/{id}/claim") String claim(@PathVariable Long id, @RequestParam String message, Principal p) {
    Item i = items.findById(id).orElseThrow(); AppUser u = me(p);
    boolean open = "OPEN".equals(i.status) || "CLAIM_PENDING".equals(i.status);
    if (open && !i.owner.id.equals(u.id) && !claims.existsByItemAndClaimant(i, u)) {
      Claim c = new Claim(); c.item = i; c.claimant = u; c.message = message; claims.save(c);
      i.status = "CLAIM_PENDING"; items.save(i);
      log(i, u.name + ("FOUND".equals(i.type) ? " says this item is theirs and sent proof" : " says they found this item"));
      return "redirect:/claims/" + c.id;
    }
    return "redirect:/my";
  }
  @PostMapping("/items/{id}/location") String location(@PathVariable Long id, @RequestParam String heldAt, Principal p) {
    Item i = items.findById(id).orElseThrow();
    if ("FOUND".equals(i.type) && i.owner.id.equals(me(p).id) && !"RETURNED".equals(i.status) && !"CLOSED".equals(i.status)) {
      i.heldAt = heldAt; items.save(i); log(i, "Finder updated where the item is: " + heldAt);
    }
    return "redirect:/items/" + id;
  }
  @PostMapping("/items/{id}/close") String close(@PathVariable Long id, Principal p) {
    Item i = items.findById(id).orElseThrow();
    if (i.owner.id.equals(me(p).id) && "OPEN".equals(i.status)) { i.status = "CLOSED"; items.save(i); log(i, "Report closed by " + i.owner.name); }
    return "redirect:/my";
  }
  @GetMapping("/claims/{id}") String claimPage(@PathVariable Long id, Principal p, Model m) {
    AppUser u = me(p); Claim c = claims.findById(id).orElseThrow();
    if (!isParty(u, c)) return "redirect:/my";
    m.addAttribute("claim", c); m.addAttribute("messages", messages.findByClaimOrderByIdAsc(c));
    m.addAttribute("canDecide", canDecide(u, c) && "PENDING".equals(c.status));
    m.addAttribute("canReceive", "APPROVED".equals(c.status) && canReceive(u, c.item)); return "claim";
  }
  @PostMapping("/claims/{id}/chat") String chat(@PathVariable Long id, @RequestParam String text, Principal p) {
    AppUser u = me(p); Claim c = claims.findById(id).orElseThrow();
    if (isParty(u, c) && !text.isBlank()) { Message x = new Message(); x.claim = c; x.sender = u; x.text = text; messages.save(x); }
    return "redirect:/claims/" + id;
  }
  @PostMapping("/claims/{id}/{action}") String decide(@PathVariable Long id, @PathVariable String action, @RequestParam(defaultValue = "") String note, Principal p) {
    AppUser u = me(p); Claim c = claims.findById(id).orElseThrow(); Item i = c.item;
    if (!canDecide(u, c) || !"PENDING".equals(c.status)) return "redirect:/my";
    String who = isAdmin(u) ? "Admin" : u.name; String n = note.isBlank() ? "" : " Note: " + note;
    c.adminNote = note; c.decidedAt = LocalDateTime.now();
    if ("approve".equals(action)) {
      c.status = "APPROVED"; i.status = "VERIFIED"; claims.save(c);
      for (Claim o : claims.findByItemAndStatus(i, "PENDING")) { o.status = "REJECTED"; o.adminNote = "Another claim was verified for this item."; claims.save(o); }
      log(i, who + " verified the proof from " + c.claimant.name + ". They can now arrange the handover in chat." + n);
    } else {
      c.status = "REJECTED"; claims.save(c); log(i, who + " rejected a claim from " + c.claimant.name + "." + n);
      if (claims.findByItemAndStatus(i, "PENDING").isEmpty() && "CLAIM_PENDING".equals(i.status)) i.status = "OPEN";
    }
    items.save(i); return "redirect:/claims/" + id;
  }
  @PostMapping("/items/{id}/received") String received(@PathVariable Long id, Principal p) {
    AppUser u = me(p); Item i = items.findById(id).orElseThrow();
    if (canReceive(u, i)) {
      i.status = "RETURNED"; i.returnedAt = LocalDateTime.now(); items.save(i);
      log(i, (isAdmin(u) ? "Admin recorded the handover." : "Owner confirmed they got the item back.") + " Case closed.");
    }
    return isAdmin(u) ? "redirect:/admin" : "redirect:/my";
  }
  @GetMapping("/my") String my(Principal p, Model m) {
    AppUser u = me(p); List<Item> mine = items.findByOwnerOrderByIdDesc(u);
    m.addAttribute("items", mine); m.addAttribute("approved", approvedFor(mine)); m.addAttribute("claims", claims.findByClaimantOrderByIdDesc(u));
    m.addAttribute("incoming", claims.findByItem_OwnerAndStatusOrderByIdDesc(u, "PENDING").stream().filter(c -> !atOffice(c.item)).toList()); return "my";
  }
  @GetMapping("/admin") String admin(Model m) {
    List<Item> ready = items.findByStatusOrderByIdDesc("VERIFIED").stream().filter(this::atOffice).toList();
    m.addAttribute("claims", claims.findByStatusOrderByIdDesc("PENDING").stream().filter(c -> atOffice(c.item)).toList());
    m.addAttribute("ready", ready); m.addAttribute("approved", approvedFor(ready));
    m.addAttribute("stale", items.findByStatusAndCreatedAtBefore("OPEN", LocalDateTime.now().minusDays(expiryDays))); m.addAttribute("days", expiryDays); return "admin";
  }
  @PostMapping("/admin/items/{id}/expire") String expire(@PathVariable Long id, Principal p) {
    if (isAdmin(me(p))) { Item i = items.findById(id).orElseThrow(); i.status = "CLOSED"; items.save(i); log(i, "Admin closed this report as unclaimed after " + expiryDays + " days."); }
    return "redirect:/admin";
  }
}
