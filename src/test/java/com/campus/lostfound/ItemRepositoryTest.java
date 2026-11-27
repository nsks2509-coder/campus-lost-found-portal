package com.campus.lostfound;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ItemRepositoryTest {
  @Autowired private ItemRepository items;

  @Test
  void searchMatchesTitleDescriptionAndLocationAndExcludesClosedItems() {
    Item titleMatch = item("Blue wallet", "Leather wallet", "Library");
    Item descriptionMatch = item("Black pouch", "Contains a blue student card", "Hostel");
    Item locationMatch = item("Water bottle", "Metal bottle", "Central Library");
    Item closedMatch = item("Old wallet", "Blue leather", "Library");
    closedMatch.status = "CLOSED";

    items.save(titleMatch);
    items.save(descriptionMatch);
    items.save(locationMatch);
    items.save(closedMatch);

    assertThat(items.search("wallet", "", ""))
        .extracting(item -> item.title)
        .containsExactly("Blue wallet");
    assertThat(items.search("student card", "", ""))
        .extracting(item -> item.title)
        .containsExactly("Black pouch");
    assertThat(items.search("library", "", ""))
        .extracting(item -> item.title)
        .contains("Blue wallet", "Water bottle")
        .doesNotContain("Old wallet");
    assertThat(items.search("", "", "LOST"))
        .extracting(item -> item.title)
        .isEmpty();
  }

  private Item item(String title, String description, String location) {
    Item item = new Item();
    item.title = title;
    item.description = description;
    item.category = "Other";
    item.type = "FOUND";
    item.location = location;
    item.eventDate = LocalDate.now();
    item.status = "OPEN";
    return item;
  }
}
