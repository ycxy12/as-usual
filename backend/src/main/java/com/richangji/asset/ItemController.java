package com.richangji.asset;

import com.richangji.common.RecordSupport;
import com.richangji.common.RecordSupport.PageResult;
import com.richangji.profile.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
  private static final Set<String> STATUSES = Set.of("在用", "闲置", "已转让", "已损坏");
  private final JdbcTemplate jdbc;
  private final AssetSupport assets;
  private final CategoryService categories;

  public ItemController(JdbcTemplate jdbc, AssetSupport assets, CategoryService categories) {
    this.jdbc = jdbc;
    this.assets = assets;
    this.categories = categories;
  }

  public record Input(
      @NotBlank @Size(max = 120) String name,
      @NotBlank String category,
      String brand,
      String model,
      String purchaseDate,
      BigDecimal purchasePrice,
      BigDecimal currentValue,
      String warrantyUntil,
      @NotBlank String status,
      String storageLocation,
      String note,
      List<String> tags,
      String imageId) {}

  public record View(
      String id,
      String name,
      String category,
      String brand,
      String model,
      String purchaseDate,
      BigDecimal purchasePrice,
      BigDecimal currentValue,
      String warrantyUntil,
      String status,
      String storageLocation,
      String note,
      List<String> tags,
      String imageId,
      Long heldDays,
      BigDecimal depreciation,
      BigDecimal dailyCost,
      Boolean warrantyActive,
      String createdAt,
      String updatedAt) {}

  public record Stats(
      long total,
      BigDecimal purchaseTotal,
      BigDecimal currentTotal,
      Map<String, Long> categories) {}

  @GetMapping
  public PageResult<View> list(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    List<Object> params = new ArrayList<>();
    params.add(ownerId);
    String where = " WHERE owner_id=?";
    if (!q.isBlank()) {
      where +=
          " AND (name LIKE ? OR brand LIKE ? OR model LIKE ? OR EXISTS (SELECT 1 FROM personal_item_tag t WHERE t.item_id=personal_item.id AND t.tag LIKE ?))";
      for (int i = 0; i < 4; i++) params.add("%" + q + "%");
    }
    if (!category.isBlank()) {
      where += " AND category=?";
      params.add(category);
    }
    if (!status.isBlank()) {
      where += " AND status=?";
      params.add(status);
    }
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM personal_item" + where, Long.class, params.toArray());
    params.add(size);
    params.add(page * size);
    List<View> values =
        jdbc.query(
            "SELECT * FROM personal_item"
                + where
                + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
            this::map,
            params.toArray());
    return new PageResult<>(values, total == null ? 0 : total, page, size);
  }

  @GetMapping("/stats")
  public Stats stats(@RequestAttribute String ownerId) {
    List<View> values =
        jdbc.query("SELECT * FROM personal_item WHERE owner_id=?", this::map, ownerId);
    Map<String, Long> categories = new LinkedHashMap<>();
    BigDecimal purchase = BigDecimal.ZERO;
    BigDecimal current = BigDecimal.ZERO;
    for (View v : values) {
      categories.merge(v.category(), 1L, Long::sum);
      if (v.purchasePrice() != null) purchase = purchase.add(v.purchasePrice());
      if (v.currentValue() != null) current = current.add(v.currentValue());
    }
    return new Stats(values.size(), purchase, current, categories);
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "personal_item", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM personal_item WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(ownerId, input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO personal_item(id,owner_id,name,category,brand,model,purchase_date,purchase_price,current_value,warranty_until,status,storage_location,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.name().trim(),
        input.category(),
        input.brand(),
        input.model(),
        AssetSupport.nullable(input.purchaseDate()),
        input.purchasePrice(),
        input.currentValue(),
        AssetSupport.nullable(input.warrantyUntil()),
        input.status(),
        input.storageLocation(),
        input.note(),
        now,
        now);
    assets.replaceTags("personal_item_tag", "item_id", id, input.tags());
    assets.replaceImage(ownerId, "item_id", id, input.imageId());
    return get(ownerId, id);
  }

  @PutMapping("/{id}")
  @Transactional
  public View update(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    RecordSupport.requireOwner(jdbc, "personal_item", id, ownerId);
    validate(ownerId, input);
    jdbc.update(
        "UPDATE personal_item SET name=?,category=?,brand=?,model=?,purchase_date=?,purchase_price=?,current_value=?,warranty_until=?,status=?,storage_location=?,note=?,updated_at=? WHERE id=? AND owner_id=?",
        input.name().trim(),
        input.category(),
        input.brand(),
        input.model(),
        AssetSupport.nullable(input.purchaseDate()),
        input.purchasePrice(),
        input.currentValue(),
        AssetSupport.nullable(input.warrantyUntil()),
        input.status(),
        input.storageLocation(),
        input.note(),
        RecordSupport.now(),
        id,
        ownerId);
    assets.replaceTags("personal_item_tag", "item_id", id, input.tags());
    assets.replaceImage(ownerId, "item_id", id, input.imageId());
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "personal_item", id, ownerId);
    assets.deleteImage(ownerId, "item_id", id);
    jdbc.update("DELETE FROM personal_item WHERE id=? AND owner_id=?", id, ownerId);
  }

  private void validate(String ownerId, Input input) {
    if (!categories.valid(ownerId, "item", input.category()) || !STATUSES.contains(input.status()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类或状态无效");
    AssetSupport.length(input.brand(), 100, "品牌");
    AssetSupport.length(input.model(), 100, "型号");
    AssetSupport.length(input.storageLocation(), 200, "存放位置");
    AssetSupport.length(input.note(), 1000, "备注");
    AssetSupport.date(input.purchaseDate());
    AssetSupport.futureDate(input.warrantyUntil());
    AssetSupport.money(input.purchasePrice());
    AssetSupport.money(input.currentValue());
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    String date = rs.getString("purchase_date");
    Long days = AssetSupport.heldDays(date);
    BigDecimal price = rs.getBigDecimal("purchase_price");
    BigDecimal value = rs.getBigDecimal("current_value");
    BigDecimal depreciation =
        price == null || value == null ? null : price.subtract(value).max(BigDecimal.ZERO);
    String warranty = rs.getString("warranty_until");
    return new View(
        id,
        rs.getString("name"),
        rs.getString("category"),
        rs.getString("brand"),
        rs.getString("model"),
        date,
        price,
        value,
        warranty,
        rs.getString("status"),
        rs.getString("storage_location"),
        rs.getString("note"),
        assets.tags("personal_item_tag", "item_id", id),
        assets.image("item_id", id),
        days,
        depreciation,
        days == null ? null : AssetSupport.divided(depreciation, days),
        warranty == null ? null : !LocalDate.parse(warranty).isBefore(AssetSupport.today()),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }
}
