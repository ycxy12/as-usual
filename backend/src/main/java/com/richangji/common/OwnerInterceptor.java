package com.richangji.common;

import com.richangji.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OwnerInterceptor implements HandlerInterceptor {
  private final AuthService auth;

  public OwnerInterceptor(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute("ownerId", auth.owner(request.getHeader("Authorization")));
    return true;
  }
}
