package com.campus.lostfound;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EventRepository extends JpaRepository<ItemEvent, Long> { List<ItemEvent> findByItemOrderByIdAsc(Item i); }
