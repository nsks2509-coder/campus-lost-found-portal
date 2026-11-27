package com.campus.lostfound;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
  @Query("""
      select i from Item i
      where (:cat = '' or i.category = :cat)
        and (:type = '' or i.type = :type)
        and (
          :q = ''
          or lower(i.title) like lower(concat('%', :q, '%'))
          or lower(i.description) like lower(concat('%', :q, '%'))
          or lower(i.location) like lower(concat('%', :q, '%'))
        )
        and i.status not in ('RETURNED', 'CLOSED')
      order by i.id desc
      """)
  List<Item> search(@Param("q") String q, @Param("cat") String cat, @Param("type") String type);

  List<Item> findByOwnerOrderByIdDesc(AppUser owner);
  List<Item> findByStatusOrderByIdDesc(String status);
  List<Item> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);
  List<Item> findByStatusOrderByReturnedAtDesc(String status);
}
