package com.richangji.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

public final class RecordSupport {
  private RecordSupport() {}

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
  }

  public static void requireOwner(JdbcTemplate jdbc, String table, String id, String owner) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE id=? AND owner_id=?",
            Integer.class,
            id,
            owner);
    if (count == null || count == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
  }

  public static BigDecimal nonNegative(BigDecimal value) {
    if (value == null || value.signum() < 0 || value.scale() > 2)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额须为非负数，最多两位小数");
    return value;
  }

  public static int page(int page) {
    return Math.max(0, page);
  }

  public static int size(int size) {
    return Math.max(1, Math.min(size, 50));
  }

  public record PageResult<T>(List<T> items, long total, int page, int size) {}
}
