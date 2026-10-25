package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.RedisConstants;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.SolutionCreateDTO;
import com.oj.backend.dto.SolutionQueryDTO;
import com.oj.backend.dto.SolutionUpdateDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.entity.Solution;
import com.oj.backend.entity.User;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.mapper.SolutionMapper;
import com.oj.backend.mapper.UserMapper;
import com.oj.backend.service.SolutionService;
import com.oj.backend.vo.SolutionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SolutionServiceImpl implements SolutionService {

    @Autowired
    private SolutionMapper solutionMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<SolutionVO> listSolutions(SolutionQueryDTO queryDTO) {
        Long problemId = queryDTO.getProblemId();
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + problemId + ")");
        }

        // 保密题拦截：非公开题目禁止普通用户查看题解
        if (problem.getIsPublic() != null && problem.getIsPublic() == 0 && !isAdminOrRoot()) {
            throw new BusinessException(403, "该题目属于比赛保密题，暂未开放题解浏览！");
        }

        Page<Solution> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 列表排除大字段 content，按点赞量降序，点赞相同按最新发布降序
        LambdaQueryWrapper<Solution> queryWrapper = new LambdaQueryWrapper<Solution>()
                .select(Solution::getId, Solution::getProblemId, Solution::getUserId,
                        Solution::getTitle, Solution::getLikes, Solution::getCreatedAt)
                .eq(Solution::getProblemId, problemId)
                .eq(queryDTO.getUserId() != null, Solution::getUserId, queryDTO.getUserId())
                .like(StringUtils.hasText(queryDTO.getKeyword()), Solution::getTitle, queryDTO.getKeyword())
                .orderByDesc(Solution::getLikes)
                .orderByDesc(Solution::getId);

        solutionMapper.selectPage(page, queryWrapper);
        List<Solution> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(page.getTotal(), Collections.emptyList());
        }

        // 批量回显作者名字
        Set<Long> userIds = records.stream().map(Solution::getUserId).collect(Collectors.toSet());
        Map<Long, String> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 批量判断当前登录用户是否点赞
        Long currentUserId = UserContext.getUserId();
        List<SolutionVO> voList = records.stream().map(s -> {
            SolutionVO vo = SolutionVO.fromEntity(s);
            vo.setAuthorName(userMap.getOrDefault(s.getUserId(), "未知用户"));
            if (currentUserId != null) {
                String likeKey = RedisConstants.SOLUTION_LIKE_PREFIX + s.getId();
                Boolean isMember = stringRedisTemplate.opsForSet().isMember(likeKey, currentUserId.toString());
                vo.setHasLiked(Boolean.TRUE.equals(isMember));
            }
            return vo;
        }).toList();

        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    public SolutionVO getSolutionById(Long id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }

        // 联动校验题目是否公开
        Problem problem = problemMapper.selectById(solution.getProblemId());
        if (problem != null && problem.getIsPublic() != null && problem.getIsPublic() == 0 && !isAdminOrRoot()) {
            throw new BusinessException(403, "该题目属于比赛保密题，暂未开放题解浏览！");
        }

        SolutionVO vo = SolutionVO.fromEntity(solution);

        User author = userMapper.selectById(solution.getUserId());
        vo.setAuthorName(author != null ? author.getUsername() : "未知用户");

        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            String likeKey = RedisConstants.SOLUTION_LIKE_PREFIX + id;
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(likeKey, currentUserId.toString());
            vo.setHasLiked(Boolean.TRUE.equals(isMember));
        }

        return vo;
    }

    @Override
    public SolutionVO createSolution(SolutionCreateDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再发布题解！");
        }

        Problem problem = problemMapper.selectById(dto.getProblemId());
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + dto.getProblemId() + ")");
        }

        // 保密题拦截：严禁在比赛保密题下提前发布题解
        if (problem.getIsPublic() != null && problem.getIsPublic() == 0 && !isAdminOrRoot()) {
            throw new BusinessException(403, "该题目尚在保密阶段，禁止发布题解！");
        }

        Solution solution = new Solution();
        solution.setProblemId(dto.getProblemId());
        solution.setUserId(userId);
        solution.setTitle(dto.getTitle());
        solution.setContent(dto.getContent());
        solution.setLikes(0);
        solution.setCreatedAt(LocalDateTime.now());
        solution.setUpdatedAt(LocalDateTime.now());

        solutionMapper.insert(solution);

        SolutionVO vo = SolutionVO.fromEntity(solution);
        vo.setAuthorName(UserContext.getUsername());
        return vo;
    }

    @Override
    public SolutionVO updateSolution(Long id, SolutionUpdateDTO dto) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }

        Long currentUserId = UserContext.getUserId();
        // 修复：同时支持本人、ADMIN 与 ROOT
        if (!solution.getUserId().equals(currentUserId) && !isAdminOrRoot()) {
            throw new BusinessException(403, "无权限修改该题解！");
        }

        solution.setTitle(dto.getTitle());
        solution.setContent(dto.getContent());
        solution.setUpdatedAt(LocalDateTime.now());
        solutionMapper.updateById(solution);

        SolutionVO vo = SolutionVO.fromEntity(solution);
        User author = userMapper.selectById(solution.getUserId());
        vo.setAuthorName(author != null ? author.getUsername() : "未知用户");
        return vo;
    }

    @Override
    public void deleteSolution(Long id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }

        Long currentUserId = UserContext.getUserId();
        // 修复：同时支持本人、ADMIN 与 ROOT
        if (!solution.getUserId().equals(currentUserId) && !isAdminOrRoot()) {
            throw new BusinessException(403, "无权限删除该题解！");
        }

        // @TableLogic 自动软删除
        solutionMapper.deleteById(id);
        // 清理 Redis 点赞集合
        stringRedisTemplate.delete(RedisConstants.SOLUTION_LIKE_PREFIX + id);
        log.info(">>> [Solution] 题解 #{} 已被删除 (操作人 ID: {})", id, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLikeSolution(Long id) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "请登录后再进行点赞操作！");
        }

        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }

        String likeKey = RedisConstants.SOLUTION_LIKE_PREFIX + id;
        String uidStr = currentUserId.toString();

        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(likeKey, uidStr);
        if (Boolean.TRUE.equals(isLiked)) {
            // 已点赞 -> 取消点赞
            stringRedisTemplate.opsForSet().remove(likeKey, uidStr);
            solution.setLikes(Math.max(0, solution.getLikes() - 1));
            solutionMapper.updateById(solution);
            return false;
        } else {
            // 未点赞 -> 点赞
            stringRedisTemplate.opsForSet().add(likeKey, uidStr);
            solution.setLikes(solution.getLikes() + 1);
            solutionMapper.updateById(solution);
            return true;
        }
    }

    private boolean isAdminOrRoot() {
        String role = UserContext.getRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROOT".equalsIgnoreCase(role);
    }
}