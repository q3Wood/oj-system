package com.oj.backend.common;

import java.util.Map;

public class UserContext {

    private static final ThreadLocal<Map<String, Object>> USER_THREAD_LOCAL = new ThreadLocal<>();

    public static void set(Map<String, Object> userMap) {
        USER_THREAD_LOCAL.set(userMap);
    }

    public static Map<String, Object> get() {
        return USER_THREAD_LOCAL.get();
    }

    // 获取当前登录用户 ID
    public static Long getUserId() {
        Map<String, Object> map = USER_THREAD_LOCAL.get();
        if (map != null && map.get("userId") != null) {
            return Long.valueOf(map.get("userId").toString());
        }
        return null;
    }

    // 获取当前登录用户名
    public static String getUsername() {
        Map<String, Object> map = USER_THREAD_LOCAL.get();
        if (map != null && map.get("username") != null) {
            return map.get("username").toString();
        }
        return null;
    }

    // 获取当前登录用户角色
    public static String getRole() {
        Map<String, Object> map = USER_THREAD_LOCAL.get();
        if (map != null && map.get("role") != null) {
            return map.get("role").toString();
        }
        return null;
    }

    // 严防内存泄漏与线程污染，必须在请求结束后清除！
    public static void remove() {
        USER_THREAD_LOCAL.remove();
    }
}