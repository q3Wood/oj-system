package com.oj.backend.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.oj.backend.common.BusinessException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {

    // 密钥 (生产环境建议写在 yml 配置文件中)
    private static final String SECRET_KEY = "oj_backend_jwt_secret_key_2026";
    // Token 有效期：7 天 (毫秒)
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 生成 JWT Token
     */
    public static String createToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);

        return JWT.create()
                .withClaim("user", claims)
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .sign(Algorithm.HMAC256(SECRET_KEY));
    }

    /**
     * 解析并校验 Token，返回载荷
     */
    public static Map<String, Object> parseToken(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(SECRET_KEY))
                    .build()
                    .verify(token);
            return decodedJWT.getClaim("user").asMap();
        } catch (Exception e) {
            throw new BusinessException("登录凭证已过期或非法，请重新登录！");
        }
    }
}