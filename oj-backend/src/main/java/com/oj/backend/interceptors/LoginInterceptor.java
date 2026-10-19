package com.oj.backend.interceptors;

import com.oj.backend.common.BusinessException;
import com.oj.backend.common.UserContext;
import com.oj.backend.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检请求 (OPTIONS) 直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7); // 剥离 "Bearer "
                Map<String, Object> claims = JwtUtil.parseToken(token);
                UserContext.set(claims);
            } catch (Exception ignore) {
                // 如果解析失败，继续走后续逻辑，最终会返回未登录错误
            }
        }
            // 如果请求头中有 Authorization: Bearer <Token>，则尝试解析 Token
        String uri = request.getRequestURI();
        String method = request.getMethod();
        //  仅对Get  /api/problems 放行，其他接口都需要登录
        if ("GET".equalsIgnoreCase(method) && (uri.startsWith("/api/problems")
            || uri.startsWith("/api/solutions") || uri.startsWith("/api/contests"))) {
            return true;
        }
        // 从请求头获取 Authorization: Bearer <Token>

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("未登录，请先登录！");
        }

        String token = authHeader.substring(7); // 剥离 "Bearer "
        // 解析 Token，如果过期或篡改，内部会抛出 BusinessException 被全局捕获
        Map<String, Object> claims = JwtUtil.parseToken(token);
        Long userId = Long.valueOf(claims.get("userId").toString());
        String redisKey = "login:token:" + userId;
        String activateToken = stringRedisTemplate.opsForValue().get(redisKey);

        if (activateToken == null || !activateToken.equals(token)) {
            throw new BusinessException("登录凭证已过期或非法，请重新登录！");
        }

        Long remainTtl = stringRedisTemplate.getExpire(redisKey, TimeUnit.DAYS);
        if (remainTtl != null && remainTtl < 1) {
            // 如果剩余有效期小于 1 天，则续期 7 天
            stringRedisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
        }
        // 绑定到当前线程
        UserContext.set(claims);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求完毕，必定清理 ThreadLocal，防止 Tomcat 线程池线程复用导致数据串台
        UserContext.remove();
    }
}