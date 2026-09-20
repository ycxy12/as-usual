package com.richangji.record;

import com.richangji.common.RecordSupport;
import com.richangji.common.RecordSupport.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class SocialController {
  private final JdbcTemplate jdbc;

  public SocialController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record ContactInput(
      @NotBlank @Size(max = 80) String name,
      @Size(max = 80) String relation,
      @Size(max = 1000) String note) {}

  public record ContactView(
      String id,
      String name,
      String relation,
      String note,
      BigDecimal sent,
      BigDecimal received,
      String createdAt,
      String updatedAt) {}

  public record GiftInput(
      @NotBlank String occurredAt,
      @NotBlank @Size(max = 120) String event,
      @NotBlank String direction,
      @NotBlank String kind,
      @NotNull BigDecimal amount,
      @Size(max = 120) String giftName,
      @Size(max = 1000) String note) {}

  public record GiftView(
      String id,
      String contactId,
      String contactName,
      String occurredAt,
      String event,
      String direction,
      String kind,
      BigDecimal amount,
      String giftName,
      String note,
      String createdAt,
      String updatedAt) {}

  @GetMapping("/contacts")
  public PageResult<ContactView> contacts(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    String like = "%" + q + "%";
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM contact WHERE owner_id=? AND name LIKE ?",
            Long.class,
            ownerId,
            like);
    List<ContactView> items =
        jdbc.query(
            "SELECT * FROM contact WHERE owner_id=? AND name LIKE ? ORDER BY updated_at DESC LIMIT ? OFFSET ?",
            this::contactMap,
            ownerId,
            like,
            size,
            page * size);
    return new PageResult<>(items, total == null ? 0 : total, page, size);
  }

  @GetMapping("/contacts/{id}")
  public ContactView contact(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "contact", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM contact WHERE id=? AND owner_id=?", this::contactMap, id, ownerId);
  }

  @PostMapping("/contacts")
  @ResponseStatus(HttpStatus.CREATED)
  public ContactView createContact(
      @RequestAttribute String ownerId, @Valid @RequestBody ContactInput input) {
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO contact(id,owner_id,name,relation_name,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.name().trim(),
        input.relation(),
        input.note(),
        now,
        now);
    return contact(ownerId, id);
  }

  @PutMapping("/contacts/{id}")
  public ContactView updateContact(
      @RequestAttribute String ownerId,
      @PathVariable String id,
      @Valid @RequestBody ContactInput input) {
    RecordSupport.requireOwner(jdbc, "contact", id, ownerId);
    jdbc.update(
        "UPDATE contact SET name=?,relation_name=?,note=?,updated_at=? WHERE id=? AND owner_id=?",
        input.name().trim(),
        input.relation(),
        input.note(),
        RecordSupport.now(),
        id,
        ownerId);
    return contact(ownerId, id);
  }

  @DeleteMapping("/contacts/{id}")
  @Transactional
  public void deleteContact(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "contact", id, ownerId);
    jdbc.update("DELETE FROM contact WHERE id=? AND owner_id=?", id, ownerId);
  }

  @GetMapping("/contacts/{contactId}/gifts")
  public PageResult<GiftView> gifts(
      @RequestAttribute String ownerId,
      @PathVariable String contactId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    RecordSupport.requireOwner(jdbc, "contact", contactId, ownerId);
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM gift_record WHERE owner_id=? AND contact_id=?",
            Long.class,
            ownerId,
            contactId);
    return new PageResult<>(
        jdbc.query(
            "SELECT g.*,c.name AS contact_name FROM gift_record g JOIN contact c ON c.id=g.contact_id WHERE g.owner_id=? AND g.contact_id=? ORDER BY g.occurred_at DESC,g.created_at DESC LIMIT ? OFFSET ?",
            this::giftMap,
            ownerId,
            contactId,
            size,
            page * size),
        total == null ? 0 : total,
        page,
        size);
  }

  @GetMapping("/gifts/recent")
  public List<GiftView> recent(
      @RequestAttribute String ownerId, @RequestParam(defaultValue = "3") int size) {
    return jdbc.query(
        "SELECT g.*,c.name AS contact_name FROM gift_record g JOIN contact c ON c.id=g.contact_id WHERE g.owner_id=? ORDER BY g.occurred_at DESC,g.created_at DESC LIMIT ?",
        this::giftMap,
        ownerId,
        Math.max(1, Math.min(size, 10)));
  }

  @GetMapping("/contacts/{contactId}/gifts/{id}")
  public GiftView gift(
      @RequestAttribute String ownerId, @PathVariable String contactId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "contact", contactId, ownerId);
    List<GiftView> result =
        jdbc.query(
            "SELECT g.*,c.name AS contact_name FROM gift_record g JOIN contact c ON c.id=g.contact_id WHERE g.id=? AND g.contact_id=? AND g.owner_id=?",
            this::giftMap,
            id,
            contactId,
            ownerId);
    if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "往来记录不存在");
    return result.get(0);
  }

  @PostMapping("/contacts/{contactId}/gifts")
  @ResponseStatus(HttpStatus.CREATED)
  public GiftView createGift(
      @RequestAttribute String ownerId,
      @PathVariable String contactId,
      @Valid @RequestBody GiftInput input) {
    RecordSupport.requireOwner(jdbc, "contact", contactId, ownerId);
    validate(input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO gift_record(id,owner_id,contact_id,occurred_at,event_name,direction,gift_kind,amount,gift_name,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        contactId,
        input.occurredAt(),
        input.event().trim(),
        input.direction(),
        input.kind(),
        input.amount(),
        input.giftName(),
        input.note(),
        now,
        now);
    return gift(ownerId, contactId, id);
  }

  @PutMapping("/contacts/{contactId}/gifts/{id}")
  public GiftView updateGift(
      @RequestAttribute String ownerId,
      @PathVariable String contactId,
      @PathVariable String id,
      @Valid @RequestBody GiftInput input) {
    gift(ownerId, contactId, id);
    validate(input);
    jdbc.update(
        "UPDATE gift_record SET occurred_at=?,event_name=?,direction=?,gift_kind=?,amount=?,gift_name=?,note=?,updated_at=? WHERE id=? AND contact_id=? AND owner_id=?",
        input.occurredAt(),
        input.event().trim(),
        input.direction(),
        input.kind(),
        input.amount(),
        input.giftName(),
        input.note(),
        RecordSupport.now(),
        id,
        contactId,
        ownerId);
    return gift(ownerId, contactId, id);
  }

  @DeleteMapping("/contacts/{contactId}/gifts/{id}")
  public void deleteGift(
      @RequestAttribute String ownerId, @PathVariable String contactId, @PathVariable String id) {
    gift(ownerId, contactId, id);
    jdbc.update(
        "DELETE FROM gift_record WHERE id=? AND contact_id=? AND owner_id=?",
        id,
        contactId,
        ownerId);
  }

  private void validate(GiftInput input) {
    try {
      LocalDateTime.parse(input.occurredAt());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期时间格式无效");
    }
    if (!List.of("送出", "收到").contains(input.direction()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来方向无效");
    if (!List.of("现金", "礼物").contains(input.kind()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来类型无效");
    if (input.kind().equals("礼物") && (input.giftName() == null || input.giftName().isBlank()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写礼物名称");
    RecordSupport.nonNegative(input.amount());
  }

  private ContactView contactMap(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    BigDecimal sent =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount),0) FROM gift_record WHERE contact_id=? AND direction='送出'",
            BigDecimal.class,
            id);
    BigDecimal received =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount),0) FROM gift_record WHERE contact_id=? AND direction='收到'",
            BigDecimal.class,
            id);
    return new ContactView(
        id,
        rs.getString("name"),
        rs.getString("relation_name"),
        rs.getString("note"),
        sent,
        received,
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }

  private GiftView giftMap(ResultSet rs, int rowNum) throws SQLException {
    return new GiftView(
        rs.getString("id"),
        rs.getString("contact_id"),
        rs.getString("contact_name"),
        rs.getString("occurred_at"),
        rs.getString("event_name"),
        rs.getString("direction"),
        rs.getString("gift_kind"),
        rs.getBigDecimal("amount"),
        rs.getString("gift_name"),
        rs.getString("note"),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }
}
