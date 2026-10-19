package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.SolutionCreateDTO;
import com.oj.backend.dto.SolutionUpdateDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.entity.Solution;
import com.oj.backend.entity.User;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.mapper.SolutionMapper;
import com.oj.backend.mapper.UserMapper;
import com.oj.backend.service.SolutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SolutionServiceImpl implements SolutionService {

    @Autowired
    private SolutionMapper solutionMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<Solution> listSolutions(Long problemId, Integer pageNum, Integer pageSize) {
        Page<Solution> page = new Page<>(pageNum, pageSize);

        // 列表页排除长文本 content，按热度/最新降序
        LambdaQueryWrapper<Solution> queryWrapper = new LambdaQueryWrapper<Solution>()
                .select(Solution::getId, Solution::getProblemId, Solution::getUserId,
                        Solution::getTitle, Solution::getLikes, Solution::getCreatedAt)
                .eq(Solution::getProblemId, problemId)
                .orderByDesc(Solution::getId);

        solutionMapper.selectPage(page, queryWrapper);
        List<Solution> records = page.getRecords();

        // 性能优化：批量查出作者名字，避免循环查库 (N+1 问题)
        if (!records.isEmpty()) {
            Set<Long> userIds = records.stream().map(Solution::getUserId).collect(Collectors.toSet());
            List<User> users = userMapper.selectBatchIds(userIds);
            Map<Long, String> userMap = users.stream().collect(Collectors.toMap(User::getId, User::getUsername));

            records.forEach(s -> s.setAuthorName(userMap.getOrDefault(s.getUserId(), "未知用户")));
        }

        return new PageResult<>(page.getTotal(), records);
    }

    @Override
    public Solution getSolutionById(Long id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }

        // 补齐作者名字
        User author = userMapper.selectById(solution.getUserId());
        solution.setAuthorName(author != null ? author.getUsername() : "未知用户");
        return solution;
    }

    @Override
    public Solution createSolution(SolutionCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 检查题目是否存在
        Problem problem = problemMapper.selectById(dto.getProblemId());
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + dto.getProblemId() + ")");
        }

        // 2. 落库
        Solution solution = new Solution();
        solution.setProblemId(dto.getProblemId());
        solution.setUserId(userId);
        solution.setTitle(dto.getTitle());
        solution.setContent(dto.getContent());
        solution.setLikes(0);
        solution.setCreatedAt(LocalDateTime.now());
        solution.setUpdatedAt(LocalDateTime.now());

        solutionMapper.insert(solution);
        solution.setAuthorName(UserContext.getUsername());
        return solution;
    }

    @Override
    public Solution updateSolution(Long id, SolutionUpdateDTO dto) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }

        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();
        if (!solution.getUserId().equals(currentUserId) && !"ADMIN".equalsIgnoreCase(currentRole)) {
            throw new BusinessException("无权限修改该题解！");
        }

        solution.setTitle(dto.getTitle());
        solution.setContent(dto.getContent());
        solution.setUpdatedAt(LocalDateTime.now());
        solutionMapper.updateById(solution);
        return solution;
    }

    @Override
    public void deleteSolution(Long id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在！(ID: " + id + ")");
        }
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();
        if (!solution.getUserId().equals(currentUserId) && !"ADMIN".equalsIgnoreCase(currentRole)) {
            throw new BusinessException("无权限删除该题解！");
        }
        solutionMapper.deleteById(id);
    }
}