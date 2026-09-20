package com.richangji.record;

import com.richangji.common.RecordSupport;
import com.richangji.common.RecordSupport.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fragments")
public class FragmentController {
  private final JdbcTemplate jdbc;
  private final ImageFileStore files;

  public FragmentController(JdbcTemplate jdbc, ImageFileStore files) {
    this.jdbc = jdbc;
    this.files = files;
  }

  public record Input(
      @NotBlank @Size(max = 2000) String content,
      @NotBlank String occurredAt,
      List<String> tags,
      List<String> imageIds) {}

  public record View(
      String id,
      String content,
      String occurredAt,
      List<String> tags,
      List<String> imageIds,
      String createdAt,
      String updatedAt) {}

  @GetMapping
  public PageResult<View> list(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String tag,
      @RequestParam(defaultValue = "") String date,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    String where = " WHERE f.owner_id=?";
    List<Object> params = new ArrayList<>();
    params.add(ownerId);
    if (!q.isBlank()) {
      where +=
          " AND (f.content LIKE ? OR EXISTS (SELECT 1 FROM fragment_tag t WHERE t.fragment_id=f.id AND t.tag LIKE ?))";
      params.add("%" + q + "%");
      params.add("%" + q + "%");
    }
    if (!tag.isBlank()) {
      where += " AND EXISTS (SELECT 1 FROM fragment_tag t WHERE t.fragment_id=f.id AND t.tag=?)";
      params.add(tag);
    }
    if (!date.isBlank()) {
      where += " AND f.occurred_at LIKE ?";
      params.add(date + "%");
    }
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM fragment f" + where, Long.class, params.toArray());
    List<Object> paged = new ArrayList<>(params);
    paged.add(size);
    paged.add(page * size);
    List<String> ids =
        jdbc.query(
            "SELECT f.id FROM fragment f"
                + where
                + " ORDER BY f.occurred_at DESC,f.created_at DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> rs.getString(1),
            paged.toArray());
    return new PageResult<>(
        ids.stream().map(id -> get(ownerId, id)).toList(), total == null ? 0 : total, page, size);
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "fragment", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM fragment WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    List<String> tags =
        jdbc.query(
            "SELECT tag FROM fragment_tag WHERE fragment_id=? ORDER BY tag",
            (r, n) -> r.getString(1),
            id);
    List<String> images =
        jdbc.query(
            "SELECT id FROM image_asset WHERE fragment_id=? ORDER BY created_at",
            (r, n) -> r.getString(1),
            id);
    return new View(
        id,
        rs.getString("content"),
        rs.getString("occurred_at"),
        tags,
        images,
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO fragment(id,owner_id,occurred_at,content,created_at,updated_at) VALUES(?,?,?,?,?,?)",
        id,
        ownerId,
        input.occurredAt(),
        input.content().trim(),
        now,
        now);
    replaceRelations(ownerId, id, input);
    return get(ownerId, id);
  }

  @PutMapping("/{id}")
  @Transactional
  public View update(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    RecordSupport.requireOwner(jdbc, "fragment", id, ownerId);
    validate(input);
    jdbc.update(
        "UPDATE fragment SET occurred_at=?,content=?,updated_at=? WHERE id=? AND owner_id=?",
        input.occurredAt(),
        input.content().trim(),
        RecordSupport.now(),
        id,
        ownerId);
    replaceRelations(ownerId, id, input);
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "fragment", id, ownerId);
    List<String> imageIds =
        jdbc.query(
            "SELECT id FROM image_asset WHERE fragment_id=? AND owner_id=?",
            (rs, n) -> rs.getString(1),
            id,
            ownerId);
    jdbc.update("DELETE FROM image_asset WHERE fragment_id=? AND owner_id=?", id, ownerId);
    jdbc.update("DELETE FROM fragment WHERE id=? AND owner_id=?", id, ownerId);
    files.deleteAfterCommit(imageIds);
  }

  @GetMapping("/tags")
  public List<String> tags(@RequestAttribute String ownerId) {
    return jdbc.query(
        "SELECT DISTINCT t.tag FROM fragment_tag t JOIN fragment f ON f.id=t.fragment_id WHERE f.owner_id=? ORDER BY t.tag",
        (rs, rowNum) -> rs.getString(1),
        ownerId);
  }

  private void validate(Input input) {
    try {
      LocalDateTime.parse(input.occurredAt());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期时间格式无效");
    }
    if (input.tags() != null && input.tags().size() > 20)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多 20 个标签");
    if (input.imageIds() != null && input.imageIds().size() > 9)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多 9 张图片");
  }

  private void replaceRelations(String ownerId, String id, Input input) {
    jdbc.update("DELETE FROM fragment_tag WHERE fragment_id=?", id);
    Set<String> tags = new LinkedHashSet<>(input.tags() == null ? List.of() : input.tags());
    for (String tag : tags) {
      if (tag == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签无效");
      String value = tag.trim();
      if (!value.isEmpty()) {
        if (value.length() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签太长");
        jdbc.update("INSERT INTO fragment_tag(fragment_id,tag) VALUES(?,?)", id, value);
      }
    }
    List<String> imageIds = input.imageIds() == null ? List.of() : input.imageIds();
    if (new LinkedHashSet<>(imageIds).size() != imageIds.size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片重复");
    for (String imageId : imageIds) {
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM image_asset WHERE id=? AND owner_id=? AND item_id IS NULL AND garment_id IS NULL AND restaurant_id IS NULL AND place_id IS NULL AND (fragment_id IS NULL OR fragment_id=?)",
              Integer.class,
              imageId,
              ownerId,
              id);
      if (count == null || count == 0)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不存在或无权使用");
    }
    List<String> old =
        jdbc.query(
            "SELECT id FROM image_asset WHERE fragment_id=? AND owner_id=?",
            (rs, n) -> rs.getString(1),
            id,
            ownerId);
    List<String> removed = old.stream().filter(imageId -> !imageIds.contains(imageId)).toList();
    for (String imageId : removed)
      jdbc.update("DELETE FROM image_asset WHERE id=? AND owner_id=?", imageId, ownerId);
    files.deleteAfterCommit(removed);
    for (String imageId : imageIds)
      jdbc.update(
          "UPDATE image_asset SET fragment_id=? WHERE id=? AND owner_id=?", id, imageId, ownerId);
  }
}
