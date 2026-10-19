package com.oj.backend.config;

import com.oj.backend.interceptors.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    // 1. 配置全局跨域支持 (CORS)
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许对所有接口跨域
                .allowedOriginPatterns("*") // 允许任何来源的前端 (如 localhost:5173, localhost:3000)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的 HTTP 方法
                .allowedHeaders("*") // 允许携带任何请求头 (包括 Authorization)
                .allowCredentials(true) // 允许携带 Cookie/凭证
                .maxAge(3600); // 预检请求 (OPTIONS) 缓存 1 小时，避免频繁发预检包
    }

    // 2. 拦截器配置
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register",
                        // "/api/problems/**",
                        // "/api/problems",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html"
                );
    }
}