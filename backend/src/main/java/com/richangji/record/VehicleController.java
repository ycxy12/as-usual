package com.richangji.record;

import com.richangji.common.RecordSupport;
import com.richangji.common.RecordSupport.PageResult;
import com.richangji.profile.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {
  private final JdbcTemplate jdbc;
  private final CategoryService categories;

  public VehicleController(JdbcTemplate jdbc, CategoryService categories) {
    this.jdbc = jdbc;
    this.categories = categories;
  }

  public record Input(
      @NotBlank String occurredAt,
      @NotBlank String category,
      @NotNull BigDecimal amount,
      BigDecimal odometer,
      BigDecimal quantity,
      BigDecimal unitPrice,
      String location,
      String note) {}

  public record View(
      String id,
      String occurredAt,
      String category,
      BigDecimal amount,
      BigDecimal odometer,
      BigDecimal quantity,
      BigDecimal unitPrice,
      String location,
      String note,
      String createdAt,
      String updatedAt)
      implements ExpenseStatistics.Expense {}

  public record Stats(
      ExpenseStatistics.Summary expenses,
      BigDecimal distance,
      BigDecimal costPerKm,
      List<MileagePoint> mileage) {}

  public record MileagePoint(String occurredAt, BigDecimal odometer) {}

  @GetMapping
  public PageResult<View> list(
      @RequestAttribute String ownerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page = RecordSupport.page(page);
    size = RecordSupport.size(size);
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM vehicle_record WHERE owner_id=?", Long.class, ownerId);
    List<View> items =
        jdbc.query(
            "SELECT * FROM vehicle_record WHERE owner_id=? ORDER BY occurred_at DESC,created_at DESC LIMIT ? OFFSET ?",
            this::map,
            ownerId,
            size,
            page * size);
    return new PageResult<>(items, total == null ? 0 : total, page, size);
  }

  @GetMapping("/stats")
  public Stats stats(@RequestAttribute String ownerId) {
    List<View> values =
        jdbc.query("SELECT * FROM vehicle_record WHERE owner_id=?", this::map, ownerId);
    ExpenseStatistics.Summary expenses = ExpenseStatistics.calculate(values);
    List<MileagePoint> mileage =
        values.stream()
            .filter(v -> v.odometer() != null)
            .sorted((a, b) -> a.occurredAt().compareTo(b.occurredAt()))
            .map(v -> new MileagePoint(v.occurredAt(), v.odometer()))
            .toList();
    BigDecimal distance =
        mileage.size() < 2
            ? BigDecimal.ZERO
            : mileage
                .get(mileage.size() - 1)
                .odometer()
                .subtract(mileage.get(0).odometer())
                .max(BigDecimal.ZERO);
    BigDecimal trackedCost =
        mileage.size() < 2
            ? BigDecimal.ZERO
            : values.stream()
                .filter(
                    v ->
                        v.occurredAt().compareTo(mileage.get(0).occurredAt()) >= 0
                            && v.occurredAt()
                                    .compareTo(mileage.get(mileage.size() - 1).occurredAt())
                                <= 0)
                .map(View::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal perKm =
        distance.signum() == 0
            ? BigDecimal.ZERO
            : trackedCost.divide(distance, 2, RoundingMode.HALF_UP);
    return new Stats(expenses, distance, perKm, mileage);
  }

  @GetMapping("/{id}")
  public View get(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "vehicle_record", id, ownerId);
    return jdbc.queryForObject(
        "SELECT * FROM vehicle_record WHERE id=? AND owner_id=?", this::map, id, ownerId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public View create(@RequestAttribute String ownerId, @Valid @RequestBody Input input) {
    validate(ownerId, input);
    String id = RecordSupport.id();
    LocalDateTime now = RecordSupport.now();
    jdbc.update(
        "INSERT INTO vehicle_record(id,owner_id,occurred_at,category,amount,odometer,quantity,unit_price,location,note,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ownerId,
        input.occurredAt(),
        input.category(),
        input.amount(),
        input.odometer(),
        input.quantity(),
        input.unitPrice(),
        input.location(),
        input.note(),
        now,
        now);
    return get(ownerId, id);
  }

  @PutMapping("/{id}")
  public View update(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    RecordSupport.requireOwner(jdbc, "vehicle_record", id, ownerId);
    validate(ownerId, input);
    jdbc.update(
        "UPDATE vehicle_record SET occurred_at=?,category=?,amount=?,odometer=?,quantity=?,unit_price=?,location=?,note=?,updated_at=? WHERE id=? AND owner_id=?",
        input.occurredAt(),
        input.category(),
        input.amount(),
        input.odometer(),
        input.quantity(),
        input.unitPrice(),
        input.location(),
        input.note(),
        RecordSupport.now(),
        id,
        ownerId);
    return get(ownerId, id);
  }

  @DeleteMapping("/{id}")
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    RecordSupport.requireOwner(jdbc, "vehicle_record", id, ownerId);
    jdbc.update("DELETE FROM vehicle_record WHERE id=? AND owner_id=?", id, ownerId);
  }

  private void validate(String ownerId, Input input) {
    try {
      LocalDateTime.parse(input.occurredAt());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期时间格式无效");
    }
    if (!categories.valid(ownerId, "vehicle", input.category()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用车类型无效");
    RecordSupport.nonNegative(input.amount());
    if (input.odometer() != null && input.odometer().signum() < 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "里程不能小于 0");
    if (input.quantity() != null && input.quantity().signum() <= 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数量须大于 0");
    if (input.unitPrice() != null) RecordSupport.nonNegative(input.unitPrice());
    if (input.location() != null && input.location().length() > 200)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地点太长");
    if (input.note() != null && input.note().length() > 1000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备注太长");
  }

  private View map(ResultSet rs, int rowNum) throws SQLException {
    return new View(
        rs.getString("id"),
        rs.getString("occurred_at"),
        rs.getString("category"),
        rs.getBigDecimal("amount"),
        rs.getBigDecimal("odometer"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("unit_price"),
        rs.getString("location"),
        rs.getString("note"),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }
}
