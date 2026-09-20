package com.richangji.record;

import com.richangji.common.RecordSupport;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {
  private static final String UNATTACHED =
      "fragment_id IS NULL AND item_id IS NULL AND garment_id IS NULL"
          + " AND restaurant_id IS NULL AND place_id IS NULL";
  private final JdbcTemplate jdbc;
  private final ImageFileStore files;

  public ImageController(JdbcTemplate jdbc, ImageFileStore files) {
    this.jdbc = jdbc;
    this.files = files;
  }

  public record Uploaded(String id) {}

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Uploaded upload(
      @RequestAttribute String ownerId, @RequestPart("file") MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片大小须在 5MB 内");
    String type;
    try {
      type = detect(file.getBytes());
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取图片");
    }
    if (type == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPEG、PNG 或 WebP 图片");
    cleanupExpiredUploads();
    String id = RecordSupport.id();
    try {
      files.save(id, file);
      jdbc.update(
          "INSERT INTO image_asset(id,owner_id,fragment_id,content_type,created_at) VALUES(?,?,NULL,?,?)",
          id,
          ownerId,
          type,
          RecordSupport.now());
      return new Uploaded(id);
    } catch (Exception ex) {
      files.deleteNow(id);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存图片失败");
    }
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void deleteUpload(@RequestAttribute String ownerId, @PathVariable String id) {
    int deleted =
        jdbc.update(
            "DELETE FROM image_asset WHERE id=? AND owner_id=? AND " + UNATTACHED, id, ownerId);
    if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到可删除的临时图片");
    files.deleteAfterCommit(List.of(id));
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> download(
      @RequestAttribute String ownerId, @PathVariable String id) {
    String type =
        jdbc.query(
            "SELECT content_type FROM image_asset WHERE id=? AND owner_id=?",
            rs -> rs.next() ? rs.getString(1) : null,
            id,
            ownerId);
    if (type == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
    try {
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
          .contentType(MediaType.parseMediaType(type))
          .body(files.read(id));
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片文件不存在");
    }
  }

  private String detect(byte[] bytes) {
    if (bytes.length >= 3
        && (bytes[0] & 0xff) == 0xff
        && (bytes[1] & 0xff) == 0xd8
        && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
    if (bytes.length >= 8
        && (bytes[0] & 0xff) == 0x89
        && bytes[1] == 'P'
        && bytes[2] == 'N'
        && bytes[3] == 'G') return "image/png";
    if (bytes.length >= 12
        && bytes[0] == 'R'
        && bytes[1] == 'I'
        && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W'
        && bytes[9] == 'E'
        && bytes[10] == 'B'
        && bytes[11] == 'P') return "image/webp";
    return null;
  }

  private void cleanupExpiredUploads() {
    var cutoff = RecordSupport.now().minusDays(1);
    List<String> expired =
        jdbc.query(
            "SELECT id FROM image_asset WHERE " + UNATTACHED + " AND created_at<?",
            (rs, n) -> rs.getString(1),
            cutoff);
    for (String id : expired) {
      int deleted = jdbc.update("DELETE FROM image_asset WHERE id=? AND " + UNATTACHED, id);
      if (deleted > 0) files.deleteNow(id);
    }
  }
}
