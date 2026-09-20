package com.richangji.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final JdbcTemplate jdbc;
  private final WechatClient wechat;

  public AuthService(JdbcTemplate jdbc, WechatClient wechat) {
    this.jdbc = jdbc;
    this.wechat = wechat;
  }

  @Transactional
  public LoginResponse login(String code) {
    String openid = wechat.exchange(code);
    jdbc.update(
        "DELETE FROM app_session WHERE expires_at<?", java.sql.Timestamp.from(Instant.now()));
    String userId =
        jdbc.query(
            "SELECT id FROM app_user WHERE openid=?",
            rs -> rs.next() ? rs.getString(1) : null,
            openid);
    if (userId == null) {
      userId = UUID.randomUUID().toString();
      try {
        jdbc.update(
            "INSERT INTO app_user(id,openid,created_at) VALUES(?,?,?)",
            userId,
            openid,
            java.sql.Timestamp.from(Instant.now()));
      } catch (DuplicateKeyException ex) {
        userId =
            jdbc.queryForObject("SELECT id FROM app_user WHERE openid=?", String.class, openid);
      }
    }
    byte[] random = new byte[32];
    RANDOM.nextBytes(random);
    String token = HexFormat.of().formatHex(random);
    Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
    jdbc.update(
        "INSERT INTO app_session(token_hash,user_id,expires_at) VALUES(?,?,?)",
        hash(token),
        userId,
        java.sql.Timestamp.from(expiry));
    return new LoginResponse(token, expiry.toString());
  }

  public String owner(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer "))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    String token = authorization.substring(7);
    if (!token.matches("[0-9a-f]{64}"))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话无效");
    String id =
        jdbc.query(
            "SELECT user_id FROM app_session WHERE token_hash=? AND expires_at>?",
            rs -> rs.next() ? rs.getString(1) : null,
            hash(token),
            java.sql.Timestamp.from(Instant.now()));
    if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期");
    return id;
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record LoginResponse(String token, String expiresAt) {}
}
