package com.richangji.profile;

import com.richangji.common.RecordSupport;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {
  private static final Map<String, List<String>> DEFAULTS = defaults();
  private static final Map<String, Target> TARGETS = targets();
  private final JdbcTemplate jdbc;

  public CategoryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Category(String id, String module, String name, int sortOrder) {}

  private record Target(String table, String column) {}

  public List<Category> list(String ownerId, String module) {
    requireModule(module);
    ensureDefaults(ownerId, module);
    return jdbc.query(
        "SELECT id,module_name,name,sort_order FROM user_category WHERE owner_id=? AND module_name=? ORDER BY sort_order,name",
        (rs, n) ->
            new Category(
                rs.getString("id"),
                rs.getString("module_name"),
                rs.getString("name"),
                rs.getInt("sort_order")),
        ownerId,
        module);
  }

  public boolean valid(String ownerId, String module, String name) {
    if (name == null || name.isBlank()) return false;
    ensureDefaults(ownerId, module);
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_category WHERE owner_id=? AND module_name=? AND name=?",
            Integer.class,
            ownerId,
            module,
            name);
    return count != null && count > 0;
  }

  public Category create(String ownerId, String module, String name) {
    requireModule(module);
    String value = clean(name);
    ensureDefaults(ownerId, module);
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_category WHERE owner_id=? AND module_name=? AND name=?",
            Integer.class,
            ownerId,
            module,
            value);
    if (exists != null && exists > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "分类已存在");
    Integer max =
        jdbc.queryForObject(
            "SELECT MAX(sort_order) FROM user_category WHERE owner_id=? AND module_name=?",
            Integer.class,
            ownerId,
            module);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO user_category(id,owner_id,module_name,name,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
        id,
        ownerId,
        module,
        value,
        max == null ? 0 : max + 1,
        now,
        now);
    return get(ownerId, id);
  }

  public Category rename(String ownerId, String id, String name) {
    Category current = get(ownerId, id);
    String value = clean(name);
    Integer duplicate =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_category WHERE owner_id=? AND module_name=? AND name=? AND id<>?",
            Integer.class,
            ownerId,
            current.module(),
            value,
            id);
    if (duplicate != null && duplicate > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "分类已存在");
    Target target = TARGETS.get(current.module());
    jdbc.update(
        "UPDATE "
            + target.table
            + " SET "
            + target.column
            + "=? WHERE owner_id=? AND "
            + target.column
            + "=?",
        value,
        ownerId,
        current.name());
    jdbc.update(
        "UPDATE user_category SET name=?,updated_at=? WHERE id=? AND owner_id=?",
        value,
        RecordSupport.now(),
        id,
        ownerId);
    return get(ownerId, id);
  }

  public void delete(String ownerId, String id) {
    Category current = get(ownerId, id);
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_category WHERE owner_id=? AND module_name=?",
            Long.class,
            ownerId,
            current.module());
    if (total == null || total <= 1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每个模块至少保留一个分类");
    Target target = TARGETS.get(current.module());
    Long used =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM "
                + target.table
                + " WHERE owner_id=? AND "
                + target.column
                + "=?",
            Long.class,
            ownerId,
            current.name());
    if (used != null && used > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该分类仍有数据，不能删除");
    jdbc.update("DELETE FROM user_category WHERE id=? AND owner_id=?", id, ownerId);
  }

  private Category get(String ownerId, String id) {
    List<Category> values =
        jdbc.query(
            "SELECT id,module_name,name,sort_order FROM user_category WHERE id=? AND owner_id=?",
            (rs, n) ->
                new Category(
                    rs.getString("id"),
                    rs.getString("module_name"),
                    rs.getString("name"),
                    rs.getInt("sort_order")),
            id,
            ownerId);
    if (values.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分类不存在");
    return values.get(0);
  }

  private synchronized void ensureDefaults(String ownerId, String module) {
    requireModule(module);
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_category WHERE owner_id=? AND module_name=?",
            Integer.class,
            ownerId,
            module);
    if (count != null && count > 0) return;
    LocalDateTime now = RecordSupport.now();
    List<String> values = DEFAULTS.get(module);
    for (int i = 0; i < values.size(); i++)
      jdbc.update(
          "INSERT INTO user_category(id,owner_id,module_name,name,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
          RecordSupport.id(),
          ownerId,
          module,
          values.get(i),
          i,
          now,
          now);
  }

  private void requireModule(String module) {
    if (!DEFAULTS.containsKey(module))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类模块无效");
  }

  private String clean(String name) {
    if (name == null || name.isBlank() || name.trim().length() > 64)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类名称须为 1 到 64 个字");
    return name.trim();
  }

  private static Map<String, List<String>> defaults() {
    Map<String, List<String>> values = new LinkedHashMap<>();
    values.put("item", List.of("数码产品", "摄影器材", "家电", "家具", "工具", "收藏品", "其他"));
    values.put("wardrobe", List.of("上衣", "裤装", "外套", "鞋", "包", "配饰"));
    values.put("vehicle", List.of("加油", "充电", "停车", "洗车", "保养", "维修", "保险", "违章", "其他费用"));
    values.put(
        "housing", List.of("房租 / 房贷", "物业", "水费", "电费", "燃气", "宽带", "停车费", "家具", "家电", "维修", "其他"));
    values.put("restaurant", List.of("中餐", "火锅", "烧烤", "面食", "日料", "西餐", "咖啡甜品", "其他"));
    values.put(
        "place", List.of("景点", "公园", "商场", "咖啡店", "展览", "古镇", "徒步", "自驾", "亲子", "摄影地点", "其他"));
    return Map.copyOf(values);
  }

  private static Map<String, Target> targets() {
    return Map.of(
        "item", new Target("personal_item", "category"),
        "wardrobe", new Target("garment", "category"),
        "vehicle", new Target("vehicle_record", "category"),
        "housing", new Target("housing_record", "category"),
        "restaurant", new Target("restaurant", "cuisine"),
        "place", new Target("leisure_place", "category"));
  }
}
