package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.ChangelogCreateDTO;
import com.oj.backend.dto.ChangelogQueryDTO;
import com.oj.backend.dto.ChangelogUpdateDTO;
import com.oj.backend.entity.Changelog;
import com.oj.backend.entity.User;
import com.oj.backend.mapper.ChangelogMapper;
import com.oj.backend.mapper.UserMapper;
import com.oj.backend.service.ChangelogService;
import com.oj.backend.vo.ChangelogLatestVO;
import com.oj.backend.vo.ChangelogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChangelogServiceImpl implements ChangelogService {

    private static final String LATEST_CACHE_KEY = "oj:changelog:latest";
    private static final String EMPTY_PLACEHOLDER = "NONE";

    @Autowired
    private ChangelogMapper changelogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<ChangelogVO> listChangelogs(ChangelogQueryDTO queryDTO) {
        Page<Changelog> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Changelog> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getType() != null && !queryDTO.getType().isBlank()) {
            wrapper.eq(Changelog::getType, queryDTO.getType().trim().toUpperCase());
        }
        // 按自定义发布时间倒序 + ID 倒序，命中复合索引 idx_deleted_release
        wrapper.orderByDesc(Changelog::getReleaseDate)
                .orderByDesc(Changelog::getId);

        Page<Changelog> resultPage = changelogMapper.selectPage(page, wrapper);
        List<Changelog> records = resultPage.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(resultPage.getTotal(), Collections.emptyList());
        }

        // 批量 IN 查询作者用户名，彻底消除 N+1 查询
        Set<Long> authorIds = records.stream()
                .map(Changelog::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> authorNameMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(authorIds);
            for (User u : users) {
                authorNameMap.put(u.getId(), u.getUsername());
            }
        }

        List<ChangelogVO> voList = records.stream().map(entity -> {
            ChangelogVO vo = ChangelogVO.fromEntity(entity);
            vo.setAuthorName(authorNameMap.getOrDefault(entity.getAuthorId(), "root"));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(resultPage.getTotal(), voList);
    }

    @Override
    public ChangelogLatestVO getLatestChangelog() {
        // 1. 优先读取 Redis 缓存
        String cached = stringRedisTemplate.opsForValue().get(LATEST_CACHE_KEY);
        if (cached != null) {
            if (EMPTY_PLACEHOLDER.equals(cached)) {
                return null;
            }
            try {
                return objectMapper.readValue(cached, ChangelogLatestVO.class);
            } catch (Exception e) {
                log.warn("反序列化最新日志缓存失败，降级回源 DB: {}", e.getMessage());
            }
        }

        // 2. 缓存未命中，回源 MySQL 查询最新一条有效日志
        Changelog latest = changelogMapper.selectOne(
                new LambdaQueryWrapper<Changelog>()
                        .orderByDesc(Changelog::getReleaseDate)
                        .orderByDesc(Changelog::getId)
                        .last("LIMIT 1")
        );

        if (latest == null) {
            // 写入空值占位符防缓存穿透 (TTL 1 小时)
            stringRedisTemplate.opsForValue().set(LATEST_CACHE_KEY, EMPTY_PLACEHOLDER, 1, TimeUnit.HOURS);
            return null;
        }

        ChangelogLatestVO vo = ChangelogLatestVO.fromEntity(latest);
        try {
            stringRedisTemplate.opsForValue().set(
                    LATEST_CACHE_KEY,
                    objectMapper.writeValueAsString(vo),
                    24,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("写入最新日志 Redis 缓存失败: {}", e.getMessage());
        }
        return vo;
    }

    @Override
    public ChangelogVO createChangelog(ChangelogCreateDTO dto) {
        User rootUser = requireRootUser("发布");

        Changelog entity = Changelog.builder()
                .version(dto.getVersion().trim())
                .title(dto.getTitle().trim())
                .type(dto.getType().trim().toUpperCase())
                .content(dto.getContent())
                .releaseDate(dto.getReleaseDate() != null ? dto.getReleaseDate() : LocalDateTime.now())
                .authorId(rootUser.getId())
                .isDeleted(0)
                .build();

        changelogMapper.insert(entity);
        evictLatestCache();

        // 回查一次确保 createdAt 等数据库默认时间字段完整
        Changelog saved = changelogMapper.selectById(entity.getId());
        ChangelogVO vo = ChangelogVO.fromEntity(saved != null ? saved : entity);
        vo.setAuthorName(rootUser.getUsername());
        return vo;
    }

    @Override
    public ChangelogVO updateChangelog(Long id, ChangelogUpdateDTO dto) {
        User rootUser = requireRootUser("修改");

        Changelog existing = changelogMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("更新日志不存在或已被删除！(ID: " + id + ")");
        }

        existing.setVersion(dto.getVersion().trim());
        existing.setTitle(dto.getTitle().trim());
        existing.setType(dto.getType().trim().toUpperCase());
        existing.setContent(dto.getContent());
        if (dto.getReleaseDate() != null) {
            existing.setReleaseDate(dto.getReleaseDate());
        }

        changelogMapper.updateById(existing);
        evictLatestCache();

        ChangelogVO vo = ChangelogVO.fromEntity(existing);
        User author = userMapper.selectById(existing.getAuthorId());
        vo.setAuthorName(author != null ? author.getUsername() : rootUser.getUsername());
        return vo;
    }

    @Override
    public void deleteChangelog(Long id) {
        requireRootUser("删除");

        Changelog existing = changelogMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("更新日志不存在或已被删除！(ID: " + id + ")");
        }

        // 触发 MyBatis-Plus @TableLogic 逻辑软删除 (is_deleted = 1)
        changelogMapper.deleteById(id);
        evictLatestCache();
    }

    /**
     * 严格校验当前登录用户必须拥有 ROOT 超级管理员角色
     */
    private User requireRootUser(String actionName) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录凭证已过期，请重新登录！");
        }
        User user = userMapper.selectById(currentUserId);
        if (user == null || !"ROOT".equals(user.getRole())) {
            throw new BusinessException(403, "权限不足！只有 ROOT 超级管理员有权" + actionName + "系统更新日志。");
        }
        return user;
    }

    /**
     * 写操作后主动清除最新日志缓存，保证缓存一致性
     */
    private void evictLatestCache() {
        stringRedisTemplate.delete(LATEST_CACHE_KEY);
    }
}