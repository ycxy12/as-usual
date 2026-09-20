package com.richangji.asset;

import com.richangji.record.ImageFileStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AssetSupport {
  private final JdbcTemplate jdbc;
  private final ImageFileStore files;

  public AssetSupport(JdbcTemplate jdbc, ImageFileStore files) {
    this.jdbc = jdbc;
    this.files = files;
  }

  public static String nullable(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }

  public static LocalDate date(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      LocalDate date = LocalDate.parse(value);
      if (!date.toString().equals(value) || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
        throw new IllegalArgumentException();
      return date;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期格式无效或晚于今天");
    }
  }

  public static LocalDate futureDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      LocalDate date = LocalDate.parse(value);
      if (!date.toString().equals(value)) throw new IllegalArgumentException();
      return date;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期格式无效");
    }
  }

  public static void length(String value, int max, String field) {
    if (value != null && value.length() > max)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "太长");
  }

  public static BigDecimal money(BigDecimal value) {
    if (value != null && (value.signum() < 0 || value.scale() > 2))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额须为非负数，最多两位小数");
    return value;
  }

  public static Long heldDays(String purchaseDate) {
    LocalDate date = purchaseDate == null ? null : LocalDate.parse(purchaseDate);
    return date == null
        ? null
        : Math.max(1, ChronoUnit.DAYS.between(date, LocalDate.now(ZoneId.of("Asia/Shanghai"))) + 1);
  }

  public static BigDecimal divided(BigDecimal numerator, long denominator) {
    return numerator == null || denominator <= 0
        ? null
        : numerator.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
  }

  public List<String> tags(String table, String foreignKey, String id) {
    return jdbc.query(
        "SELECT tag FROM " + table + " WHERE " + foreignKey + "=? ORDER BY tag",
        (rs, n) -> rs.getString(1),
        id);
  }

  public void replaceTags(String table, String foreignKey, String id, List<String> values) {
    if (values != null && values.size() > 20)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多 20 个标签");
    Set<String> tags = new LinkedHashSet<>();
    for (String tag : values == null ? List.<String>of() : values) {
      if (tag == null || tag.isBlank()) continue;
      length(tag.trim(), 64, "标签");
      tags.add(tag.trim());
    }
    jdbc.update("DELETE FROM " + table + " WHERE " + foreignKey + "=?", id);
    for (String tag : tags)
      jdbc.update("INSERT INTO " + table + "(" + foreignKey + ",tag) VALUES(?,?)", id, tag);
  }

  public String image(String column, String id) {
    List<String> images =
        jdbc.query(
            "SELECT id FROM image_asset WHERE " + column + "=?", (rs, n) -> rs.getString(1), id);
    return images.isEmpty() ? null : images.get(0);
  }

  public void replaceImage(String ownerId, String column, String id, String imageId) {
    String old = image(column, id);
    if (imageId != null && !imageId.isBlank()) {
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM image_asset WHERE id=? AND owner_id=?"
                  + " AND fragment_id IS NULL"
                  + associationCondition(column, "item_id")
                  + associationCondition(column, "garment_id")
                  + associationCondition(column, "restaurant_id")
                  + associationCondition(column, "place_id"),
              Integer.class,
              imageId,
              ownerId,
              id);
      if (count == null || count == 0)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不存在或无权使用");
    }
    if (old != null && !old.equals(imageId)) {
      jdbc.update("DELETE FROM image_asset WHERE id=? AND owner_id=?", old, ownerId);
      files.deleteAfterCommit(List.of(old));
    }
    if (imageId != null && !imageId.isBlank())
      jdbc.update(
          "UPDATE image_asset SET " + column + "=? WHERE id=? AND owner_id=?",
          id,
          imageId,
          ownerId);
  }

  private String associationCondition(String selected, String candidate) {
    return selected.equals(candidate)
        ? " AND (" + candidate + " IS NULL OR " + candidate + "=?)"
        : " AND " + candidate + " IS NULL";
  }

  public void deleteImage(String ownerId, String column, String id) {
    String image = image(column, id);
    if (image == null) return;
    jdbc.update("DELETE FROM image_asset WHERE id=? AND owner_id=?", image, ownerId);
    files.deleteAfterCommit(List.of(image));
  }
}
