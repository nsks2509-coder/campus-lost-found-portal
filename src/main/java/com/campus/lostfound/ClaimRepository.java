package com.campus.lostfound;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ClaimRepository extends JpaRepository<Claim, Long> {
  List<Claim> findByStatusOrderByIdDesc(String s);
  List<Claim> findByClaimantOrderByIdDesc(AppUser u);
  List<Claim> findByItemAndStatus(Item i, String s);
  List<Claim> findByItem_OwnerAndStatusOrderByIdDesc(AppUser o, String s);
  boolean existsByItemAndClaimant(Item i, AppUser u);
}
