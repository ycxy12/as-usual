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
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {
  private static final Set<String> STATUSES = Set.of("去过", "想去");
  private final JdbcTemplate jdbc;
  private final AssetSupport assets;
  private final CategoryService categories;

  public PlaceController(JdbcTemplate jdbc, AssetSupport assets, CategoryService categories) {
    this.jdbc = jdbc;
    this.assets = assets;
    this.categories = categories;
  }

  public record Input(
      @NotBlank @Size(max = 120) String name,
      @NotBlank String category,
      String address,
      BigDecimal distanceKm,
      BigDecimal averageCost,
      Integer recommendation,
      @NotBlank String visitStatus,
      String lastVisitedDate,
      Integer visitCount,
      String note,
      List<String> tags,
      String imageId) {}

  public record View(
      String id,
      String name,
      String category,
      String address,
      BigDecimal distanceKm,
      BigDecimal averageCost,
      Integer recommendation,
      String visitStatus,
      String lastVisitedDate,
      int visitCount,
      String note,
      List<String> tags,
      String imageId,
      String createdAt,
      String updatedAt) {}

  public record Stats(long total, long visited, long wishList) {}

  @GetMapping
  public PageResult<View> list(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "ANY") String visited,
      @RequestParam(required = false) BigDecimal maxBudget,
      @RequestParam(required = false) BigDecimal maxDistance,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    Query query = query(ownerId, q, category, visited, maxBudget, maxDistance);
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM leisure_place p" + query.where,
            Long.class,
            query.params.toArray());
    List<Object> params = new ArrayList<>(query.params);
    params.add(size);
    params.add(page * size);
    List<View> items =
        jdbc.query(
            "SELECT p.* FROM leisure_place p"
                + query.where
                + " ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?",
            this::map,
            params.toArray());
    return new PageResult<>(items, total == null ? 0 : total, page, size);
  }

  @GetMapping("/recommend")
  public View recommend(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "ANY") String visited,
      @RequestParam(required = false) BigDecimal maxBudget,
      @RequestParam(required = false) BigDecimal maxDistance) {
    Query query = query(ownerId, "", category, visited, maxBudget, maxDistance);
    return DiscoverSupport.random(
        jdbc.query(
            "SELECT p.* FROM leisure_place p" + query.where, this::map, query.params.toArray()));
  }

  @GetMapping("/stats")
  public Stats stats(@RequestAttribute String ownerId) {
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM leisure_place WHERE owner_id=?", Long.class, ownerId);
    Long visited =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM leisure_place WHERE owner_id=? AND visit_count>0",
            Long.class,
            ownerId);
    Long wish =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM leisure_place WHERE owner_id=? AND visit_status='想去'",
            Long.class,
            ownerId);
    return new Stats(
        total == null ? 0 : total, visited == null ? 0 : visited, wish == null ? 0 : wish);
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "leisure_place", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM leisure_place WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(ownerId, input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO leisure_place(id,owner_id,name,category,address,distance_km,average_cost,recommendation,visit_status,last_visited_date,visit_count,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.name().trim(),
        input.category(),
        input.address(),
        input.distanceKm(),
        input.averageCost(),
        input.recommendation(),
        input.visitStatus(),
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
    RecordSupport.requireOwner(jdbc, "leisure_place", id, ownerId);
    validate(ownerId, input);
    jdbc.update(
        "UPDATE leisure_place SET name=?,category=?,address=?,distance_km=?,average_cost=?,recommendation=?,visit_status=?,last_visited_date=?,visit_count=?,note=?,updated_at=? WHERE id=? AND owner_id=?",
        input.name().trim(),
        input.category(),
        input.address(),
        input.distanceKm(),
        input.averageCost(),
        input.recommendation(),
        input.visitStatus(),
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
            "UPDATE leisure_place SET visit_count=visit_count+1,visit_status='去过',last_visited_date=?,updated_at=? WHERE id=? AND owner_id=?",
            AssetSupport.today().toString(),
            RecordSupport.now(),
            id,
            ownerId);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地点不存在");
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "leisure_place", id, ownerId);
    assets.deleteImage(ownerId, "place_id", id);
    jdbc.update("DELETE FROM leisure_place WHERE id=? AND owner_id=?", id, ownerId);
  }

  private void replaceRelations(String ownerId, String id, Input input) {
    assets.replaceTags("leisure_place_tag", "place_id", id, input.tags());
    assets.replaceImage(ownerId, "place_id", id, input.imageId());
  }

  private void validate(String ownerId, Input input) {
    if (!categories.valid(ownerId, "place", input.category())
        || !STATUSES.contains(input.visitStatus()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类或到访状态无效");
    AssetSupport.length(input.address(), 240, "地址");
    AssetSupport.length(input.note(), 1000, "备注");
    DiscoverSupport.nonNegative(input.distanceKm(), "距离");
    DiscoverSupport.nonNegative(input.averageCost(), "人均消费");
    if (input.recommendation() != null
        && (input.recommendation() < 1 || input.recommendation() > 5))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "推荐指数须为 1 到 5");
    AssetSupport.date(input.lastVisitedDate());
    DiscoverSupport.count(input.visitCount(), input.lastVisitedDate());
    if ((input.visitCount() == null || input.visitCount() == 0) && input.visitStatus().equals("去过"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "去过的地点至少应有一次到访");
  }

  private Query query(
      String ownerId,
      String q,
      String category,
      String visited,
      BigDecimal maxBudget,
      BigDecimal maxDistance) {
    DiscoverSupport.nonNegative(maxBudget, "预算");
    DiscoverSupport.nonNegative(maxDistance, "距离");
    String where = " WHERE p.owner_id=?";
    List<Object> params = new ArrayList<>();
    params.add(ownerId);
    if (!q.isBlank()) {
      where +=
          " AND (p.name LIKE ? OR p.address LIKE ? OR EXISTS (SELECT 1 FROM leisure_place_tag t WHERE t.place_id=p.id AND t.tag LIKE ?))";
      for (int i = 0; i < 3; i++) params.add("%" + q + "%");
    }
    if (!category.isBlank()) {
      where += " AND p.category=?";
      params.add(category);
    }
    where += DiscoverSupport.visitFilter(visited, "p");
    if (maxBudget != null) {
      where += " AND p.average_cost IS NOT NULL AND p.average_cost<=?";
      params.add(maxBudget);
    }
    if (maxDistance != null) {
      where += " AND p.distance_km IS NOT NULL AND p.distance_km<=?";
      params.add(maxDistance);
    }
    return new Query(where, params);
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    Integer recommendation = (Integer) rs.getObject("recommendation");
    return new View(
        id,
        rs.getString("name"),
        rs.getString("category"),
        rs.getString("address"),
        rs.getBigDecimal("distance_km"),
        rs.getBigDecimal("average_cost"),
        recommendation,
        rs.getString("visit_status"),
        rs.getString("last_visited_date"),
        rs.getInt("visit_count"),
        rs.getString("note"),
        assets.tags("leisure_place_tag", "place_id", id),
        assets.image("place_id", id),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }

  private record Query(String where, List<Object> params) {}
}
