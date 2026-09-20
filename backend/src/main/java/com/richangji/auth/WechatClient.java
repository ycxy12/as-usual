package com.richangji.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WechatClient {
  private final RestClient client = RestClient.create("https://api.weixin.qq.com");
  private final String appId;
  private final String secret;

  public WechatClient(
      @Value("${app.wechat.app-id}") String appId,
      @Value("${app.wechat.app-secret}") String secret) {
    this.appId = appId;
    this.secret = secret;
  }

  public String exchange(String code) {
    if (appId.isBlank() || secret.isBlank())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "尚未配置微信小程序身份信息");
    try {
      SessionResponse result =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/sns/jscode2session")
                          .queryParam("appid", appId)
                          .queryParam("secret", secret)
                          .queryParam("js_code", code)
                          .queryParam("grant_type", "authorization_code")
                          .build())
              .retrieve()
              .body(SessionResponse.class);
      if (result == null || result.openid() == null || result.openid().isBlank())
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "微信登录失败，请重试");
      return result.openid();
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信登录服务暂不可用");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SessionResponse(String openid, @JsonProperty("errcode") Integer errorCode) {}
}
