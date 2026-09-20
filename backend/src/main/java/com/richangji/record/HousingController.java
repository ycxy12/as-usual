package com.richangji.record;

import com.richangji.common.RecordSupport;
import com.richangji.common.RecordSupport.PageResult;
import com.richangji.profile.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/housing")
public class HousingController {
  private final JdbcTemplate jdbc;
  private final CategoryService categories;

  public HousingController(JdbcTemplate jdbc, CategoryService categories) {
    this.jdbc = jdbc;
    this.categories = categories;
  }

  public record Input(
      @NotBlank String occurredAt,
      @NotBlank String category,
      @NotNull BigDecimal amount,
      @NotBlank String billingMonth,
      String note) {}

  public record View(
      String id,
      String occurredAt,
      String category,
      BigDecimal amount,
      String billingMonth,
      String note,
      String createdAt,
      String updatedAt)
      implements ExpenseStatistics.Expense {}

  private record BilledExpense(String occurredAt, String category, BigDecimal amount)
      implements ExpenseStatistics.Expense {}

  public record Stats(ExpenseStatistics.Summary expenses, BigDecimal dailyAverage) {}

  @GetMapping
  public PageResult<View> list(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM housing_record WHERE owner_id=?", Long.class, ownerId);
    return new PageResult<>(
        jdbc.query(
            "SELECT * FROM housing_record WHERE owner_id=? ORDER BY occurred_at DESC,created_at DESC LIMIT ? OFFSET ?",
            this::map,
            ownerId,
            size,
            page * size),
        total == null ? 0 : total,
        page,
        size);
  }

  @GetMapping("/stats")
  public Stats stats(@RequestAttribute String ownerId) {
    List<View> values =
        jdbc.query("SELECT * FROM housing_record WHERE owner_id=?", this::map, ownerId);
    List<BilledExpense> billed =
        values.stream()
            .map(
                v -> new BilledExpense(v.billingMonth() + "-01T12:00:00", v.category(), v.amount()))
            .toList();
    ExpenseStatistics.Summary summary = ExpenseStatistics.calculate(billed);
    return new Stats(summary, ExpenseStatistics.daily(billed, summary.total()));
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "housing_record", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM housing_record WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(ownerId, input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO housing_record(id,owner_id,occurred_at,category,amount,billing_month,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.occurredAt(),
        input.category(),
        input.amount(),
        input.billingMonth(),
        input.note(),
        now,
        now);
    return get(ownerId, id);
  }

  @PutMapping("/{id}")
  public View update(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    RecordSupport.requireOwner(jdbc, "housing_record", id, ownerId);
    validate(ownerId, input);
    jdbc.update(
        "UPDATE housing_record SET occurred_at=?,category=?,amount=?,billing_month=?,note=?,updated_at=? WHERE id=? AND owner_id=?",
        input.occurredAt(),
        input.category(),
        input.amount(),
        input.billingMonth(),
        input.note(),
        RecordSupport.now(),
        id,
        ownerId);
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "housing_record", id, ownerId);
    jdbc.update("DELETE FROM housing_record WHERE id=? AND owner_id=?", id, ownerId);
  }

  private void validate(String ownerId, Input input) {
    try {
      LocalDateTime.parse(input.occurredAt());
      YearMonth.parse(input.billingMonth());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期或账期格式无效");
    }
    if (!categories.valid(ownerId, "housing", input.category()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "居住费用类型无效");
    RecordSupport.nonNegative(input.amount());
    if (input.note() != null && input.note().length() > 1000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备注太长");
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    return new View(
        rs.getString("id"),
        rs.getString("occurred_at"),
        rs.getString("category"),
        rs.getBigDecimal("amount"),
        rs.getString("billing_month"),
        rs.getString("note"),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }
}
