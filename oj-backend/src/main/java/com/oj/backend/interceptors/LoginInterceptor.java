package com.oj.backend.interceptors;

import com.oj.backend.common.BusinessException;
import com.oj.backend.common.RedisConstants;
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
        // 1. 跨域预检请求 (OPTIONS) 直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 2. 尝试从请求头提取并【完整校验】Token (只解析一次！)
        String authHeader = request.getHeader("Authorization");
        boolean isAuthenticated = false;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // 2.1 校验 JWT 签名与本身有效期
                Map<String, Object> claims = JwtUtil.parseToken(token);
                Long userId = Long.valueOf(claims.get("userId").toString());

                // 2.2 校验 Redis 中是否存在且一致 (防止已登出或被踢下线的旧 Token 混入)
                String redisKey = RedisConstants.LOGIN_TOKEN_PREFIX + userId;
                String activeToken = stringRedisTemplate.opsForValue().get(redisKey);

                if (token.equals(activeToken)) {
                    // 2.3 滑动窗口续期：用 HOURS 判断！如果剩余时间不足 24 小时，续期到 7 天
                    // (注意：请确保 JwtUtil 生成 Token 时的过期时间本身足够长，例如 7 天或 30 天，由 Redis 掌控实际生死)
                    Long remainHours = stringRedisTemplate.getExpire(redisKey, TimeUnit.HOURS);
                    if (remainHours != null && remainHours < 24) {
                        stringRedisTemplate.expire(redisKey, RedisConstants.LOGIN_TOKEN_TTL_DAYS, TimeUnit.DAYS);
                    }
                    // 绑定合法上下文
                    UserContext.set(claims);
                    isAuthenticated = true;
                }
            } catch (Exception ignore) {
                // Token 解析失败或过期，视为未登录 (isAuthenticated = false)
            }
        }

        // 3. 判断是否为允许匿名访问的公开 GET 接口 (严禁放行包含 /admin/ 的后台路径！)
        if (isPublicGetApi(method, uri)) {
            // 公开接口：如果上面 Token 合法，UserContext 里就有值(比如题目列表展示“是否已AC”)；
            // 如果没带 Token 或 Token 已失效，UserContext 为空，照样以游客身份放行！
            return true;
        }

        // 4. 非公开接口：必须已通过登录校验
        if (!isAuthenticated) {
            throw new BusinessException("未登录或登录凭证已过期，请重新登录！");
        }

        // 5. (可选增强) 统一拦截 /api/*/admin/** 后台路径，防止普通 USER 越权访问任何模块的后台接口
        if (uri.contains("/admin/")) {
            String role = UserContext.getRole();
            if (!"ADMIN".equalsIgnoreCase(role) && !"ROOT".equalsIgnoreCase(role)) {
                throw new BusinessException("权限不足！禁止访问后台管理接口。");
            }
        }

        return true;
    }

    /**
     * 判断是否为公开可匿名访问的只读接口
     */
    private boolean isPublicGetApi(String method, String uri) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        // 任何带 /admin/ 的后台接口绝不可匿名访问
        if (uri.contains("/admin/")) {
            return false;
        }
        // 允许游客公开浏览：题目列表/公开题详情、比赛列表/排行榜、公开提交大厅
        return uri.startsWith("/api/problem")
                || uri.startsWith("/api/contest/list")
                || uri.startsWith("/api/contest/") && uri.endsWith("/ranklist")
                || uri.startsWith("/api/submission/list")
                || uri.startsWith("/api/solution");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求完毕，必定清理 ThreadLocal，防止 Tomcat 线程池复用导致数据串台
        UserContext.remove();
    }
}