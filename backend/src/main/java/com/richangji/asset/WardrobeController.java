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
import java.time.temporal.ChronoUnit;
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
@RequestMapping("/api/v1/wardrobe")
public class WardrobeController {
  private static final Set<String> SEASONS = Set.of("", "春", "夏", "秋", "冬", "四季");
  private static final Set<String> STATUSES = Set.of("在穿", "闲置", "已处理");
  private final JdbcTemplate jdbc;
  private final AssetSupport assets;
  private final CategoryService categories;

  public WardrobeController(JdbcTemplate jdbc, AssetSupport assets, CategoryService categories) {
    this.jdbc = jdbc;
    this.assets = assets;
    this.categories = categories;
  }

  public record Input(
      @NotBlank @Size(max = 120) String name,
      @NotBlank String category,
      String brand,
      String color,
      String size,
      String season,
      String purchaseDate,
      BigDecimal purchasePrice,
      Integer wearCount,
      String lastWornDate,
      @NotBlank String status,
      List<String> tags,
      String imageId) {}

  public record View(
      String id,
      String name,
      String category,
      String brand,
      String color,
      String size,
      String season,
      String purchaseDate,
      BigDecimal purchasePrice,
      int wearCount,
      String lastWornDate,
      String status,
      List<String> tags,
      String imageId,
      BigDecimal costPerWear,
      boolean longUnworn,
      String createdAt,
      String updatedAt) {}

  public record Rank(String id, String name, int wearCount) {}

  public record Stats(
      long total, Map<String, Long> categories, long longUnworn, List<Rank> wearRanking) {}

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
          " AND (name LIKE ? OR brand LIKE ? OR EXISTS (SELECT 1 FROM garment_tag t WHERE t.garment_id=garment.id AND t.tag LIKE ?))";
      for (int i = 0; i < 3; i++) params.add("%" + q + "%");
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
        jdbc.queryForObject("SELECT COUNT(*) FROM garment" + where, Long.class, params.toArray());
    params.add(size);
    params.add(page * size);
    List<View> values =
        jdbc.query(
            "SELECT * FROM garment" + where + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
            this::map,
            params.toArray());
    return new PageResult<>(values, total == null ? 0 : total, page, size);
  }

  @GetMapping("/stats")
  public Stats stats(@RequestAttribute String ownerId) {
    List<View> values = jdbc.query("SELECT * FROM garment WHERE owner_id=?", this::map, ownerId);
    Map<String, Long> categories = new LinkedHashMap<>();
    for (View v : values) categories.merge(v.category(), 1L, Long::sum);
    List<Rank> rank =
        values.stream()
            .filter(v -> v.wearCount() > 0)
            .sorted((a, b) -> Integer.compare(b.wearCount(), a.wearCount()))
            .limit(5)
            .map(v -> new Rank(v.id(), v.name(), v.wearCount()))
            .toList();
    return new Stats(
        values.size(), categories, values.stream().filter(View::longUnworn).count(), rank);
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "garment", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM garment WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(ownerId, input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO garment(id,owner_id,name,category,brand,color,size_label,season,purchase_date,purchase_price,wear_count,last_worn_date,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.name().trim(),
        input.category(),
        input.brand(),
        input.color(),
        input.size(),
        input.season(),
        AssetSupport.nullable(input.purchaseDate()),
        input.purchasePrice(),
        input.wearCount() == null ? 0 : input.wearCount(),
        AssetSupport.nullable(input.lastWornDate()),
        input.status(),
        now,
        now);
    assets.replaceTags("garment_tag", "garment_id", id, input.tags());
    assets.replaceImage(ownerId, "garment_id", id, input.imageId());
    return get(ownerId, id);
  }

  @PutMapping("/{id}")
  @Transactional
  public View update(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    RecordSupport.requireOwner(jdbc, "garment", id, ownerId);
    validate(ownerId, input);
    jdbc.update(
        "UPDATE garment SET name=?,category=?,brand=?,color=?,size_label=?,season=?,purchase_date=?,purchase_price=?,wear_count=?,last_worn_date=?,status=?,updated_at=? WHERE id=? AND owner_id=?",
        input.name().trim(),
        input.category(),
        input.brand(),
        input.color(),
        input.size(),
        input.season(),
        AssetSupport.nullable(input.purchaseDate()),
        input.purchasePrice(),
        input.wearCount() == null ? 0 : input.wearCount(),
        AssetSupport.nullable(input.lastWornDate()),
        input.status(),
        RecordSupport.now(),
        id,
        ownerId);
    assets.replaceTags("garment_tag", "garment_id", id, input.tags());
    assets.replaceImage(ownerId, "garment_id", id, input.imageId());
    return get(ownerId, id);
  }

  @PostMapping("/{id}/wear")
  public View wear(@RequestAttribute String ownerId, @PathVariable String id) {
    int changed =
        jdbc.update(
            "UPDATE garment SET wear_count=wear_count+1,last_worn_date=?,updated_at=? WHERE id=? AND owner_id=?",
            AssetSupport.today().toString(),
            RecordSupport.now(),
            id,
            ownerId);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "衣物不存在");
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "garment", id, ownerId);
    assets.deleteImage(ownerId, "garment_id", id);
    jdbc.update("DELETE FROM garment WHERE id=? AND owner_id=?", id, ownerId);
  }

  private void validate(String ownerId, Input input) {
    if (!categories.valid(ownerId, "wardrobe", input.category())
        || !STATUSES.contains(input.status())
        || !SEASONS.contains(input.season() == null ? "" : input.season()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类、季节或状态无效");
    AssetSupport.length(input.brand(), 100, "品牌");
    AssetSupport.length(input.color(), 60, "颜色");
    AssetSupport.length(input.size(), 60, "尺码");
    AssetSupport.date(input.purchaseDate());
    AssetSupport.date(input.lastWornDate());
    AssetSupport.money(input.purchasePrice());
    if (input.wearCount() != null && input.wearCount() < 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "穿着次数不能小于 0");
    if (input.wearCount() != null
        && input.wearCount() > 0
        && (input.lastWornDate() == null || input.lastWornDate().isBlank()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已穿着时需填写最近穿着日期");
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    int count = rs.getInt("wear_count");
    String last = rs.getString("last_worn_date");
    String date = rs.getString("purchase_date");
    String base = last == null ? date : last;
    boolean longUnworn =
        base != null && ChronoUnit.DAYS.between(LocalDate.parse(base), AssetSupport.today()) >= 90;
    return new View(
        id,
        rs.getString("name"),
        rs.getString("category"),
        rs.getString("brand"),
        rs.getString("color"),
        rs.getString("size_label"),
        rs.getString("season"),
        date,
        rs.getBigDecimal("purchase_price"),
        count,
        last,
        rs.getString("status"),
        assets.tags("garment_tag", "garment_id", id),
        assets.image("garment_id", id),
        AssetSupport.divided(rs.getBigDecimal("purchase_price"), count),
        longUnworn,
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }
}
