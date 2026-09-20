package com.richangji.discover;

import com.richangji.asset.AssetSupport;
import com.richangji.common.RecordSupport;
import com.richangji.common.RecordSupport.PageResult;
import com.richangji.profile.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {
  private final JdbcTemplate jdbc;
  private final AssetSupport assets;
  private final CategoryService categories;

  public RestaurantController(JdbcTemplate jdbc, AssetSupport assets, CategoryService categories) {
    this.jdbc = jdbc;
    this.assets = assets;
    this.categories = categories;
  }

  public record Input(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 60) String cuisine,
      BigDecimal averagePrice,
      String address,
      BigDecimal distanceKm,
      BigDecimal rating,
      String recommendedDishes,
      String avoidedDishes,
      String lastVisitedDate,
      Integer visitCount,
      String note,
      List<String> tags,
      String imageId) {}

  public record View(
      String id,
      String name,
      String cuisine,
      BigDecimal averagePrice,
      String address,
      BigDecimal distanceKm,
      BigDecimal rating,
      String recommendedDishes,
      String avoidedDishes,
      String lastVisitedDate,
      int visitCount,
      String note,
      List<String> tags,
      String imageId,
      String createdAt,
      String updatedAt) {}

  public record Stats(long total, long visited, long neverVisited) {}

  @GetMapping
  public PageResult<View> list(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String cuisine,
      @RequestParam(defaultValue = "ANY") String visited,
      @RequestParam(required = false) BigDecimal maxBudget,
      @RequestParam(required = false) BigDecimal maxDistance,
      @RequestParam(defaultValue = "false") boolean excludeRecent7,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    Query query = query(ownerId, q, cuisine, visited, maxBudget, maxDistance, excludeRecent7);
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM restaurant r" + query.where, Long.class, query.params.toArray());
    List<Object> params = new ArrayList<>(query.params);
    params.add(size);
    params.add(page * size);
    List<View> items =
        jdbc.query(
            "SELECT r.* FROM restaurant r"
                + query.where
                + " ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?",
            this::map,
            params.toArray());
    return new PageResult<>(items, total == null ? 0 : total, page, size);
  }

  @GetMapping("/recommend")
  public View recommend(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String cuisine,
      @RequestParam(defaultValue = "ANY") String visited,
      @RequestParam(required = false) BigDecimal maxBudget,
      @RequestParam(required = false) BigDecimal maxDistance,
      @RequestParam(defaultValue = "false") boolean excludeRecent7) {
    Query query = query(ownerId, "", cuisine, visited, maxBudget, maxDistance, excludeRecent7);
    List<View> values =
        jdbc.query("SELECT r.* FROM restaurant r" + query.where, this::map, query.params.toArray());
    return DiscoverSupport.random(values);
  }

  @GetMapping("/stats")
  public Stats stats(@RequestAttribute String ownerId) {
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM restaurant WHERE owner_id=?", Long.class, ownerId);
    Long visited =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM restaurant WHERE owner_id=? AND visit_count>0",
            Long.class,
            ownerId);
    return new Stats(
        total == null ? 0 : total,
        visited == null ? 0 : visited,
        (total == null ? 0 : total) - (visited == null ? 0 : visited));
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "restaurant", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM restaurant WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(ownerId, input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO restaurant(id,owner_id,name,cuisine,average_price,address,distance_km,rating,recommended_dishes,avoided_dishes,last_visited_date,visit_count,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.name().trim(),
        input.cuisine().trim(),
        input.averagePrice(),
        input.address(),
        input.distanceKm(),
        input.rating(),
        input.recommendedDishes(),
        input.avoidedDishes(),
        AssetSupport.nullable(input.lastVisitedDate()),
        input.visitCount() == null ? 0 : input.visitCount(),
        input.note(),
        now,
        now);
    replaceRelations(ownerId, id, input);
    return get(ownerId, id);
  }

  @PutMapping("/{id}")
  @Transactional
  public View update(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    RecordSupport.requireOwner(jdbc, "restaurant", id, ownerId);
    validate(ownerId, input);
    jdbc.update(
        "UPDATE restaurant SET name=?,cuisine=?,average_price=?,address=?,distance_km=?,rating=?,recommended_dishes=?,avoided_dishes=?,last_visited_date=?,visit_count=?,note=?,updated_at=? WHERE id=? AND owner_id=?",
        input.name().trim(),
        input.cuisine().trim(),
        input.averagePrice(),
        input.address(),
        input.distanceKm(),
        input.rating(),
        input.recommendedDishes(),
        input.avoidedDishes(),
        AssetSupport.nullable(input.lastVisitedDate()),
        input.visitCount() == null ? 0 : input.visitCount(),
        input.note(),
        RecordSupport.now(),
        id,
        ownerId);
    replaceRelations(ownerId, id, input);
    return get(ownerId, id);
  }

  @PostMapping("/{id}/visit")
  public View visit(@RequestAttribute String ownerId, @PathVariable String id) {
    int changed =
        jdbc.update(
            "UPDATE restaurant SET visit_count=visit_count+1,last_visited_date=?,updated_at=? WHERE id=? AND owner_id=?",
            AssetSupport.today().toString(),
            RecordSupport.now(),
            id,
            ownerId);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "餐厅不存在");
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "restaurant", id, ownerId);
    assets.deleteImage(ownerId, "restaurant_id", id);
    jdbc.update("DELETE FROM restaurant WHERE id=? AND owner_id=?", id, ownerId);
  }

  private void replaceRelations(String ownerId, String id, Input input) {
    assets.replaceTags("restaurant_tag", "restaurant_id", id, input.tags());
    assets.replaceImage(ownerId, "restaurant_id", id, input.imageId());
  }

  private void validate(String ownerId, Input input) {
    if (!categories.valid(ownerId, "restaurant", input.cuisine()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "菜系无效");
    AssetSupport.length(input.address(), 240, "地址");
    AssetSupport.length(input.recommendedDishes(), 500, "推荐菜");
    AssetSupport.length(input.avoidedDishes(), 500, "避雷菜");
    AssetSupport.length(input.note(), 1000, "备注");
    DiscoverSupport.nonNegative(input.averagePrice(), "人均价格");
    DiscoverSupport.nonNegative(input.distanceKm(), "距离");
    if (input.rating() != null
        && (input.rating().compareTo(BigDecimal.ZERO) < 0
            || input.rating().compareTo(BigDecimal.valueOf(5)) > 0
            || input.rating().scale() > 1))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分须为 0 到 5，最多一位小数");
    AssetSupport.date(input.lastVisitedDate());
    DiscoverSupport.count(input.visitCount(), input.lastVisitedDate());
  }

  private Query query(
      String ownerId,
      String q,
      String cuisine,
      String visited,
      BigDecimal maxBudget,
      BigDecimal maxDistance,
      boolean excludeRecent7) {
    DiscoverSupport.nonNegative(maxBudget, "预算");
    DiscoverSupport.nonNegative(maxDistance, "距离");
    String where = " WHERE r.owner_id=?";
    List<Object> params = new ArrayList<>();
    params.add(ownerId);
    if (!q.isBlank()) {
      where +=
          " AND (r.name LIKE ? OR r.address LIKE ? OR EXISTS (SELECT 1 FROM restaurant_tag t WHERE t.restaurant_id=r.id AND t.tag LIKE ?))";
      for (int i = 0; i < 3; i++) params.add("%" + q + "%");
    }
    if (!cuisine.isBlank()) {
      where += " AND r.cuisine=?";
      params.add(cuisine);
    }
    where += DiscoverSupport.visitFilter(visited, "r");
    if (maxBudget != null) {
      where += " AND r.average_price IS NOT NULL AND r.average_price<=?";
      params.add(maxBudget);
    }
    if (maxDistance != null) {
      where += " AND r.distance_km IS NOT NULL AND r.distance_km<=?";
      params.add(maxDistance);
    }
    if (excludeRecent7) {
      where += " AND (r.last_visited_date IS NULL OR r.last_visited_date<?)";
      params.add(AssetSupport.today().minusDays(6).toString());
    }
    return new Query(where, params);
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    return new View(
        id,
        rs.getString("name"),
        rs.getString("cuisine"),
        rs.getBigDecimal("average_price"),
        rs.getString("address"),
        rs.getBigDecimal("distance_km"),
        rs.getBigDecimal("rating"),
        rs.getString("recommended_dishes"),
        rs.getString("avoided_dishes"),
        rs.getString("last_visited_date"),
        rs.getInt("visit_count"),
        rs.getString("note"),
        assets.tags("restaurant_tag", "restaurant_id", id),
        assets.image("restaurant_id", id),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }

  private record Query(String where, List<Object> params) {}
}
