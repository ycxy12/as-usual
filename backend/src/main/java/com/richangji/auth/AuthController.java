package com.richangji.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/wechat")
  public AuthService.LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return auth.login(request.code());
  }

  public record LoginRequest(@NotBlank(message = "缺少微信登录凭证") String code) {}
}
