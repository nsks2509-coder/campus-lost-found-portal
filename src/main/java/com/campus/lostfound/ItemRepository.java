package com.campus.lostfound;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ItemRepository extends JpaRepository<Item, Long> {
  @Query("select i from Item i where (:cat = '' or i.category = :cat) and (:type = '' or i.type = :type) and lower(i.title) like lower(concat('%', :q, '%')) and i.status not in ('RETURNED','CLOSED') order by i.id desc")
  List<Item> search(@Param("q") String q, @Param("cat") String cat, @Param("type") String type);
  List<Item> findByOwnerOrderByIdDesc(AppUser o);
  List<Item> findByStatusOrderByIdDesc(String s);
  List<Item> findByStatusAndCreatedAtBefore(String s, java.time.LocalDateTime t);
  List<Item> findByStatusOrderByReturnedAtDesc(String s);
}
