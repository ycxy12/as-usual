package com.richangji.profile;

import com.richangji.common.RecordSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/profile/tags")
public class TagController {
  private static final List<TagBinding> BINDINGS =
      List.of(
          new TagBinding("fragment_tag", "fragment_id", "fragment"),
          new TagBinding("personal_item_tag", "item_id", "personal_item"),
          new TagBinding("garment_tag", "garment_id", "garment"),
          new TagBinding("restaurant_tag", "restaurant_id", "restaurant"),
          new TagBinding("leisure_place_tag", "place_id", "leisure_place"));
  private final JdbcTemplate jdbc;

  public TagController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Input(@NotBlank String name) {}

  public record RenameInput(@NotBlank String oldName, @NotBlank String newName) {}

  public record TagView(String name, long usageCount) {}

  private record TagBinding(String relationTable, String foreignKey, String ownerTable) {}

  @GetMapping
  public List<TagView> list(
      @RequestAttribute String ownerId, @RequestParam(defaultValue = "") String q) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    names.addAll(
        jdbc.query(
            "SELECT name FROM user_tag WHERE owner_id=?", (rs, n) -> rs.getString(1), ownerId));
    for (TagBinding binding : BINDINGS)
      names.addAll(
          jdbc.query(
              "SELECT DISTINCT t.tag FROM "
                  + binding.relationTable
                  + " t JOIN "
                  + binding.ownerTable
                  + " o ON o.id=t."
                  + binding.foreignKey
                  + " WHERE o.owner_id=?",
              (rs, n) -> rs.getString(1),
              ownerId));
    String keyword = q.trim();
    return names.stream()
        .filter(name -> keyword.isEmpty() || name.contains(keyword))
        .map(name -> new TagView(name, usage(ownerId, name)))
        .sorted(Comparator.comparing(TagView::name))
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public TagView create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    String name = clean(input.name());
    if (exists(ownerId, name)) throw new ResponseStatusException(HttpStatus.CONFLICT, "标签已存在");
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO user_tag(id,owner_id,name,created_at,updated_at) VALUES(?,?,?,?,?)",
        RecordSupport.id(),
        ownerId,
        name,
        now,
        now);
    return new TagView(name, 0);
  }

  @PostMapping("/rename")
  @Transactional
  public TagView rename(@RequestAttribute String ownerId, @Valid @RequestBody RenameInput input) {
    String oldName = clean(input.oldName());
    String newName = clean(input.newName());
    if (!exists(ownerId, oldName)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在");
    if (oldName.equals(newName)) return new TagView(newName, usage(ownerId, newName));
    for (TagBinding binding : BINDINGS) renameBinding(ownerId, binding, oldName, newName);
    jdbc.update(
        "DELETE FROM user_tag WHERE owner_id=? AND name IN (?,?)", ownerId, oldName, newName);
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO user_tag(id,owner_id,name,created_at,updated_at) VALUES(?,?,?,?,?)",
        RecordSupport.id(),
        ownerId,
        newName,
        now,
        now);
    return new TagView(newName, usage(ownerId, newName));
  }

  @DeleteMapping
  @Transactional
  public void delete(@RequestAttribute String ownerId, @RequestParam String name) {
    String value = clean(name);
    if (!exists(ownerId, value)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在");
    for (TagBinding binding : BINDINGS) {
      List<String> ids = ownerIds(ownerId, binding, value);
      for (String id : ids)
        jdbc.update(
            "DELETE FROM "
                + binding.relationTable
                + " WHERE "
                + binding.foreignKey
                + "=? AND tag=?",
            id,
            value);
    }
    jdbc.update("DELETE FROM user_tag WHERE owner_id=? AND name=?", ownerId, value);
  }

  private void renameBinding(String ownerId, TagBinding binding, String oldName, String newName) {
    List<String> ids = ownerIds(ownerId, binding, oldName);
    for (String id : ids) {
      Integer target =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM "
                  + binding.relationTable
                  + " WHERE "
                  + binding.foreignKey
                  + "=? AND tag=?",
              Integer.class,
              id,
              newName);
      if (target == null || target == 0)
        jdbc.update(
            "INSERT INTO " + binding.relationTable + "(" + binding.foreignKey + ",tag) VALUES(?,?)",
            id,
            newName);
      jdbc.update(
          "DELETE FROM " + binding.relationTable + " WHERE " + binding.foreignKey + "=? AND tag=?",
          id,
          oldName);
    }
  }

  private List<String> ownerIds(String ownerId, TagBinding binding, String tag) {
    return jdbc.query(
        "SELECT t."
            + binding.foreignKey
            + " FROM "
            + binding.relationTable
            + " t JOIN "
            + binding.ownerTable
            + " o ON o.id=t."
            + binding.foreignKey
            + " WHERE o.owner_id=? AND t.tag=?",
        (rs, n) -> rs.getString(1),
        ownerId,
        tag);
  }

  private boolean exists(String ownerId, String name) {
    if (usage(ownerId, name) > 0) return true;
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_tag WHERE owner_id=? AND name=?",
            Integer.class,
            ownerId,
            name);
    return count != null && count > 0;
  }

  private long usage(String ownerId, String name) {
    long total = 0;
    for (TagBinding binding : BINDINGS) {
      Long count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM "
                  + binding.relationTable
                  + " t JOIN "
                  + binding.ownerTable
                  + " o ON o.id=t."
                  + binding.foreignKey
                  + " WHERE o.owner_id=? AND t.tag=?",
              Long.class,
              ownerId,
              name);
      total += count == null ? 0 : count;
    }
    return total;
  }

  private String clean(String value) {
    if (value == null || value.isBlank() || value.trim().length() > 64)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签名称须为 1 到 64 个字");
    return value.trim();
  }
}
