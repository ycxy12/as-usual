package com.richangji.profile;

import com.richangji.asset.AssetSupport;
import com.richangji.record.ImageFileStore;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
  private final JdbcTemplate jdbc;
  private final ImageFileStore files;

  public ProfileController(JdbcTemplate jdbc, ImageFileStore files) {
    this.jdbc = jdbc;
    this.files = files;
  }

  public record Overview(
      long monthRecords,
      long items,
      long garments,
      BigDecimal monthVehicleCost,
      BigDecimal monthHousingCost,
      long visitedRestaurants,
      long visitedPlaces) {}

  public record DataStats(
      long fragments,
      long vehicles,
      long housing,
      long contacts,
      long gifts,
      long items,
      long garments,
      long restaurants,
      long places,
      long images) {}

  @GetMapping("/overview")
  public Overview overview(@RequestAttribute String ownerId) {
    String month = AssetSupport.today().toString().substring(0, 7);
    long records =
        count("fragment", "owner_id=? AND occurred_at LIKE ?", ownerId, month + "%")
            + count("vehicle_record", "owner_id=? AND occurred_at LIKE ?", ownerId, month + "%")
            + count("housing_record", "owner_id=? AND occurred_at LIKE ?", ownerId, month + "%")
            + count("gift_record", "owner_id=? AND occurred_at LIKE ?", ownerId, month + "%");
    return new Overview(
        records,
        count("personal_item", "owner_id=?", ownerId),
        count("garment", "owner_id=?", ownerId),
        sum("vehicle_record", "amount", "owner_id=? AND occurred_at LIKE ?", ownerId, month + "%"),
        sum("housing_record", "amount", "owner_id=? AND billing_month=?", ownerId, month),
        count("restaurant", "owner_id=? AND visit_count>0", ownerId),
        count("leisure_place", "owner_id=? AND visit_count>0", ownerId));
  }

  @GetMapping("/data-stats")
  public DataStats dataStats(@RequestAttribute String ownerId) {
    return new DataStats(
        count("fragment", "owner_id=?", ownerId),
        count("vehicle_record", "owner_id=?", ownerId),
        count("housing_record", "owner_id=?", ownerId),
        count("contact", "owner_id=?", ownerId),
        count("gift_record", "owner_id=?", ownerId),
        count("personal_item", "owner_id=?", ownerId),
        count("garment", "owner_id=?", ownerId),
        count("restaurant", "owner_id=?", ownerId),
        count("leisure_place", "owner_id=?", ownerId),
        count("image_asset", "owner_id=?", ownerId));
  }

  @DeleteMapping("/data")
  @Transactional
  public void clear(@RequestAttribute String ownerId) {
    List<String> images =
        jdbc.query(
            "SELECT id FROM image_asset WHERE owner_id=?", (rs, n) -> rs.getString(1), ownerId);
    jdbc.update("DELETE FROM gift_record WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM contact WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM image_asset WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM fragment WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM vehicle_record WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM housing_record WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM personal_item WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM garment WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM restaurant WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM leisure_place WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM user_category WHERE owner_id=?", ownerId);
    jdbc.update("DELETE FROM user_tag WHERE owner_id=?", ownerId);
    files.deleteAfterCommit(images);
  }

  private long count(String table, String where, Object... args) {
    Long value =
        jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, args);
    return value == null ? 0 : value;
  }

  private BigDecimal sum(String table, String column, String where, Object... args) {
    BigDecimal value =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(" + column + "),0) FROM " + table + " WHERE " + where,
            BigDecimal.class,
            args);
    return value == null ? BigDecimal.ZERO : value;
  }
}
