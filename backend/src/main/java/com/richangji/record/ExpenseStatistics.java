package com.richangji.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ExpenseStatistics {
  private ExpenseStatistics() {}

  public interface Expense {
    String occurredAt();

    String category();

    BigDecimal amount();
  }

  public record Breakdown(String label, BigDecimal amount) {}

  public record Summary(
      BigDecimal month,
      BigDecimal year,
      BigDecimal total,
      BigDecimal monthlyAverage,
      List<Breakdown> categories,
      List<Breakdown> months) {}

  public static Summary calculate(List<? extends Expense> values) {
    YearMonth current = YearMonth.now(ZoneId.of("Asia/Shanghai"));
    BigDecimal month = BigDecimal.ZERO, year = BigDecimal.ZERO, total = BigDecimal.ZERO;
    Map<String, BigDecimal> categories = new TreeMap<>(), months = new TreeMap<>();
    YearMonth earliest = current;
    for (Expense value : values) {
      YearMonth recordMonth = YearMonth.parse(value.occurredAt().substring(0, 7));
      if (recordMonth.isBefore(earliest)) earliest = recordMonth;
      total = total.add(value.amount());
      if (recordMonth.equals(current)) month = month.add(value.amount());
      if (recordMonth.getYear() == current.getYear()) year = year.add(value.amount());
      categories.merge(value.category(), value.amount(), BigDecimal::add);
      months.merge(recordMonth.toString(), value.amount(), BigDecimal::add);
    }
    long span = values.isEmpty() ? 0 : ChronoUnit.MONTHS.between(earliest, current) + 1;
    BigDecimal average =
        span == 0
            ? BigDecimal.ZERO
            : total.divide(BigDecimal.valueOf(span), 2, RoundingMode.HALF_UP);
    return new Summary(
        month,
        year,
        total,
        average,
        categories.entrySet().stream().map(e -> new Breakdown(e.getKey(), e.getValue())).toList(),
        months.entrySet().stream().map(e -> new Breakdown(e.getKey(), e.getValue())).toList());
  }

  public static BigDecimal daily(List<? extends Expense> values, BigDecimal total) {
    if (values.isEmpty()) return BigDecimal.ZERO;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate earliest =
        values.stream()
            .map(v -> LocalDate.parse(v.occurredAt().substring(0, 10)))
            .min(LocalDate::compareTo)
            .orElse(today);
    long days = Math.max(1, ChronoUnit.DAYS.between(earliest, today) + 1);
    return total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
  }
}
