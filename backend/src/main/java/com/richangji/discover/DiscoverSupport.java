package com.richangji.discover;

import com.richangji.asset.AssetSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DiscoverSupport {
  private DiscoverSupport() {}

  public static void nonNegative(BigDecimal value, String field) {
    AssetSupport.money(value);
    if (value != null && value.scale() > 2)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "最多两位小数");
  }

  public static void count(Integer value, String lastDate) {
    if (value != null && value < 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "到访次数不能小于 0");
    if (value != null && value > 0 && (lastDate == null || lastDate.isBlank()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "有到访记录时需填写最近到访日期");
  }

  public static <T> T random(List<T> values) {
    if (values.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有符合条件的候选，换个条件试试");
    return values.get(ThreadLocalRandom.current().nextInt(values.size()));
  }

  public static String visitFilter(String value, String alias) {
    return switch (value) {
      case "VISITED" -> " AND " + alias + ".visit_count>0";
      case "NEVER" -> " AND " + alias + ".visit_count=0";
      case "ANY", "" -> "";
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "到访筛选无效");
    };
  }
}
