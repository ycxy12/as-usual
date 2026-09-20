package com.richangji.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final OwnerInterceptor owner;

  public WebConfig(OwnerInterceptor owner) {
    this.owner = owner;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(owner)
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns("/api/v1/auth/**");
  }
}
