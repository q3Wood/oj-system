package com.oj.backend.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Redis 高可用缓存与安全分布式锁工具组件
 * - 空值占位防缓存穿透 (Cache Penetration)
 * - 随机 TTL 抖动防缓存雪崩 (Cache Avalanche)
 * - 互斥锁 Double-Check 防热点缓存击穿 (Cache Breakdown)
 * - UUID 凭证 + Lua 脚本原子校验防误删分布式锁
 */
@Slf4j
@Component
public class RedisCacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Lua 原子解锁脚本：仅当锁中的 Value 与当前线程持有的 UUID token 一致时才执行删除，防止误删他人锁
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "    return redis.call('del', KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    // ==================== 1. 安全分布式锁 (UUID + Lua) ====================

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey 锁 Key
     * @param ttl     过期时间
     * @param unit    时间单位
     * @return 成功则返回唯一锁凭证 token (用于安全解锁)；失败返回 null
     */
    public String tryLock(String lockKey, long ttl, TimeUnit unit) {
        String token = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl, unit);
        return Boolean.TRUE.equals(success) ? token : null;
    }

    /**
     * 安全释放分布式锁 (Lua 脚本原子校验锁归属权)
     *
     * @param lockKey 锁 Key
     * @param token   加锁时返回的唯一凭证
     */
    public void unlock(String lockKey, String token) {
        if (lockKey == null || token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("释放 Redis 分布式锁异常, lockKey={}: {}", lockKey, e.getMessage());
        }
    }

    // ==================== 2. 缓存写入 (带随机抖动防雪崩) ====================

    /**
     * 序列化对象并写入 Redis，自动附加 0~10% 的随机 TTL 抖动防止缓存雪崩
     */
    public void setWithJitter(String key, Object value, long time, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            long baseSeconds = Math.max(1L, unit.toSeconds(time));
            long jitterSeconds = baseSeconds >= 10
                    ? ThreadLocalRandom.current().nextLong(0, baseSeconds / 10 + 1)
                    : 0L;
            stringRedisTemplate.opsForValue().set(key, json, baseSeconds + jitterSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 Redis 缓存失败, key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 主动删除指定缓存 Key
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    // ==================== 3. 防穿透 + 防雪崩查询 ====================

    /**
     * 无参单 Key 查询（内置空值占位防穿透 + 随机 TTL 防雪崩，适用于 oj:changelog:latest 等全局摘要）
     */
    public <R> R queryWithPassThrough(
            String key,
            Class<R> type,
            Supplier<R> dbFallback,
            long time,
            TimeUnit unit
    ) {
        // 1. 优先查询 Redis 缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            if (RedisConstants.CACHE_NULL_VALUE.equals(json)) {
                return null;
            }
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                log.warn("反序列化缓存失败，降级回源 DB, key={}: {}", key, e.getMessage());
            }
        }

        // 2. 缓存未命中，执行数据库回源函数
        R result = dbFallback.get();
        if (result == null) {
            // 写入空值占位符防穿透
            stringRedisTemplate.opsForValue().set(
                    key,
                    RedisConstants.CACHE_NULL_VALUE,
                    RedisConstants.CACHE_NULL_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
            return null;
        }

        // 3. 写入真实结果（带随机抖动防雪崩）
        setWithJitter(key, result, time, unit);
        return result;
    }

    /**
     * 带 ID 参数的防穿透查询（适用于 prefix + id 结构的实体缓存）
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            long time,
            TimeUnit unit
    ) {
        return queryWithPassThrough(keyPrefix + id, type, () -> dbFallback.apply(id), time, unit);
    }

    // ==================== 4. 互斥锁防热点缓存击穿查询 ====================

    /**
     * 互斥锁 + Double-Check 防热点 Key 缓存击穿查询（适用于比赛题目详情等高并发热点读场景）
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            long time,
            TimeUnit unit
    ) {
        String key = keyPrefix + id;
        // 1. 一级缓存检查
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            if (RedisConstants.CACHE_NULL_VALUE.equals(json)) {
                return null;
            }
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                log.warn("反序列化热点缓存失败, key={}: {}", key, e.getMessage());
            }
        }

        // 2. 缓存未命中，尝试获取重建互斥锁
        String lockKey = RedisConstants.CACHE_MUTEX_LOCK_PREFIX + key;
        String lockToken = tryLock(lockKey, 10, TimeUnit.SECONDS);
        if (lockToken == null) {
            // 未抢到锁，短暂休眠 50ms 后重试读取缓存
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String retryJson = stringRedisTemplate.opsForValue().get(key);
            if (retryJson != null) {
                if (RedisConstants.CACHE_NULL_VALUE.equals(retryJson)) {
                    return null;
                }
                try {
                    return objectMapper.readValue(retryJson, type);
                } catch (Exception ignore) {
                }
            }
            // 兜底回源
            return dbFallback.apply(id);
        }

        try {
            // 3. 抢锁成功后执行 Double-Check 二次检查缓存
            String doubleCheckJson = null;
            try {
                doubleCheckJson = stringRedisTemplate.opsForValue().get(key);
                if (doubleCheckJson != null) {
                    if (RedisConstants.CACHE_NULL_VALUE.equals(doubleCheckJson)) {
                        return null;
                    }
                    return objectMapper.readValue(doubleCheckJson, type);
                }
            } catch (Exception e) {
                log.warn("Double-Check 读取或反序列化缓存异常, key={}: {}", key, e.getMessage());
            }

            // 4. 回源数据库重建缓存（如果这里 DB 报错，直接往外抛，不要在 catch 里再查一次）
            R result = dbFallback.apply(id);
            if (result == null) {
                try {
                    stringRedisTemplate.opsForValue().set(
                            key,
                            RedisConstants.CACHE_NULL_VALUE,
                            RedisConstants.CACHE_NULL_TTL_MINUTES,
                            TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.warn("写入空值缓存失败, key={}: {}", key, e.getMessage());
                }
                return null;
            }
            setWithJitter(key, result, time, unit);
            return result;
        } finally {
            unlock(lockKey, lockToken);
        }
    }
}