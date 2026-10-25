package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.RedisConstants;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.ProblemCreateDTO;
import com.oj.backend.dto.ProblemQueryDTO;
import com.oj.backend.dto.ProblemUpdateDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.entity.Submission;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.mapper.SubmissionMapper;
import com.oj.backend.service.ProblemService;
import com.oj.backend.vo.ProblemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class ProblemServiceImpl implements ProblemService {

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${oj.judge.problems-dir}")
    private String problemsDir;

    // 2.1 多条件分页查询 + 用户做题状态批量填充
    @Override
    public PageResult<ProblemVO> listProblems(ProblemQueryDTO queryDTO) {
        Page<Problem> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        boolean isAdmin = isAdminOrRoot();

        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                // 列表查询排除 description 大字段，提升 I/O 效率
                .select(Problem::getId, Problem::getTitle, Problem::getTimeLimitMs,
                        Problem::getMemoryLimitMb, Problem::getAuthor, Problem::getIsPublic, Problem::getCreatedAt)
                // 多条件独立查询：题号精准匹配、标题模糊匹配、作者模糊匹配
                .eq(queryDTO.getId() != null, Problem::getId, queryDTO.getId())
                .like(StringUtils.hasText(queryDTO.getTitle()), Problem::getTitle, queryDTO.getTitle())
                .like(StringUtils.hasText(queryDTO.getAuthor()), Problem::getAuthor, queryDTO.getAuthor())
                .orderByAsc(Problem::getId);

        // 权限过滤：普通用户/游客强制只能看公开题 (isPublic = 1)
        if (!isAdmin) {
            wrapper.eq(Problem::getIsPublic, 1);
        } else if (queryDTO.getIsPublic() != null) {
            wrapper.eq(Problem::getIsPublic, queryDTO.getIsPublic());
        }

        problemMapper.selectPage(page, wrapper);
        List<Problem> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(page.getTotal(), Collections.emptyList());
        }

        // 批量查询当前登录用户在这些题目上的做题状态 (拒绝循环查库)
        List<Long> problemIds = records.stream().map(Problem::getId).toList();
        Map<Long, String> statusMap = getUserProblemStatusMap(UserContext.getUserId(), problemIds);

        List<ProblemVO> voList = records.stream().map(problem -> {
            ProblemVO vo = ProblemVO.fromEntity(problem);
            vo.setUserStatus(statusMap.getOrDefault(problem.getId(), "NOT_STARTED"));
            return vo;
        }).toList();

        return new PageResult<>(page.getTotal(), voList);
    }

    // 2.2 查单题详情
    @Override
    public ProblemVO getProblemById(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + id + ")");
        }
        // 防直接通过 URL 访问比赛保密题
        if (problem.getIsPublic() != null && problem.getIsPublic() == 0 && !isAdminOrRoot()) {
            throw new BusinessException(403, "该题目属于比赛保密题，暂未对公众开放！");
        }

        ProblemVO vo = ProblemVO.fromEntity(problem);
        Long userId = UserContext.getUserId();
        if (userId != null) {
            Map<Long, String> statusMap = getUserProblemStatusMap(userId, List.of(id));
            vo.setUserStatus(statusMap.getOrDefault(id, "NOT_STARTED"));
        }
        return vo;
    }

    // 2.3 创建题目 (保底从 1001 发号 + 解压测试点)
    @Override
    public ProblemVO createProblem(ProblemCreateDTO dto) {
        checkAdminOrRoot();
        validateZipFile(dto.getFile());

        Long newProblemId = generateNextProblemId();
        Path targetDir = Path.of(problemsDir, String.valueOf(newProblemId));

        extractZipToDir(dto.getFile(), targetDir);

        Problem problem = new Problem();
        problem.setId(newProblemId);
        problem.setTitle(dto.getTitle());
        problem.setDescription(dto.getDescription());
        problem.setTimeLimitMs(dto.getTimeLimitMs());
        problem.setMemoryLimitMb(dto.getMemoryLimitMb());
        problem.setIsPublic(dto.getIsPublic() != null && dto.getIsPublic() == 0 ? 0 : 1);
        problem.setAuthor(UserContext.getUsername() != null ? UserContext.getUsername() : "Admin");
        problem.setCreatedAt(LocalDateTime.now());
        problem.setUpdatedAt(LocalDateTime.now());

        problemMapper.insert(problem);
        log.info(">>> [Problem] 管理员 {} 创建了新题目 #{}: {}", problem.getAuthor(), newProblemId, problem.getTitle());
        return ProblemVO.fromEntity(problem);
    }

    // 2.4 更新题目 (支持重新上传并彻底清理覆盖旧测试点)
    @Override
    public ProblemVO updateProblem(Long id, ProblemUpdateDTO dto) {
        checkAdminOrRoot();

        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + id + ")");
        }

        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            validateZipFile(dto.getFile());
            Path targetDir = Path.of(problemsDir, String.valueOf(id));
            extractZipToDir(dto.getFile(), targetDir);
            log.info(">>> [Problem] 题目 #{} 的测试用例已被彻底清理并重新覆盖更新！", id);
        }

        problem.setTitle(dto.getTitle());
        problem.setDescription(dto.getDescription());
        problem.setTimeLimitMs(dto.getTimeLimitMs());
        problem.setMemoryLimitMb(dto.getMemoryLimitMb());
        if (dto.getIsPublic() != null) {
            problem.setIsPublic(dto.getIsPublic() == 0 ? 0 : 1);
        }
        problem.setUpdatedAt(LocalDateTime.now());

        problemMapper.updateById(problem);
        return ProblemVO.fromEntity(problem);
    }

    // 2.5 逻辑软删除题目
    @Override
    public void deleteProblem(Long id) {
        checkAdminOrRoot();
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在或已被删除！(ID: " + id + ")");
        }
        // MyBatis-Plus @TableLogic 会自动将其转为 UPDATE tb_problem SET is_deleted = 1 WHERE id = ?
        problemMapper.deleteById(id);
        log.info(">>> [Problem] 管理员 {} 逻辑删除了题目 #{}: {}", UserContext.getUsername(), id, problem.getTitle());
    }

    @Override
    public void updateProblemVisibility(Long id, Integer isPublic) {
        checkAdminOrRoot();
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + id + ")");
        }
        problem.setIsPublic(isPublic != null && isPublic == 0 ? 0 : 1);
        problem.setUpdatedAt(LocalDateTime.now());
        problemMapper.updateById(problem);
        log.info(">>> [Problem] 管理员 {} 更新了题目 #{} 的可见性为 {}", UserContext.getUsername(), id, problem.getIsPublic());
    }

    // ==================== 内部私有辅助方法 ====================

    private boolean isAdminOrRoot() {
        String role = UserContext.getRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROOT".equalsIgnoreCase(role);
    }

    private void checkAdminOrRoot() {
        if (!isAdminOrRoot()) {
            throw new BusinessException(403, "权限不足！只有管理员才允许进行题目管理操作。");
        }
    }

    /**
     * 批量计算用户在指定题目列表上的做题状态
     * 返回值 Key: problemId, Value: "SOLVED" (已通过) / "ATTEMPTED" (尝试过未通过)
     */
    private Map<Long, String> getUserProblemStatusMap(Long userId, List<Long> problemIds) {
        if (userId == null || problemIds == null || problemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 只查 problem_id 和 verdict 两个轻量字段，且仅统计日常练习提交 (contest_id IS NULL，或者统计全量提交皆可)
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .select(Submission::getProblemId, Submission::getVerdict)
                        .eq(Submission::getUserId, userId)
                        .in(Submission::getProblemId, problemIds)
        );

        Map<Long, String> statusMap = new HashMap<>();
        for (Submission sub : submissions) {
            Long pid = sub.getProblemId();
            String verdict = sub.getVerdict();
            // 兼容 "AC" 或 "Accepted" 两种常见判题状态码命名
            boolean isAccepted = "AC".equalsIgnoreCase(verdict) || "Accepted".equalsIgnoreCase(verdict);

            if (isAccepted) {
                statusMap.put(pid, "SOLVED"); // 一旦有一次 AC，永久定格为 SOLVED
            } else {
                statusMap.putIfAbsent(pid, "ATTEMPTED"); // 还没有 AC 记录时，标记为 ATTEMPTED
            }
        }
        return statusMap;
    }

    /**
     * 安全生成下一个题目 ID（保底从 1001 开始，即使 Redis 重启也不会与 MySQL 冲突）
     */
    private Long generateNextProblemId() {
        String seqKey = RedisConstants.PROBLEM_ID_SEQ;
        // 查出当前数据库中的最大 ID (使用原生或忽略 @TableLogic 的最大 ID，防止软删除的 ID 被重复分配)
        Problem maxProblem = problemMapper.selectOne(
                new LambdaQueryWrapper<Problem>()
                        .select(Problem::getId)
                        .orderByDesc(Problem::getId)
                        .last("LIMIT 1")
        );
        long dbMaxId = (maxProblem != null && maxProblem.getId() != null) ? maxProblem.getId() : 1000L;
        long baseId = Math.max(dbMaxId, 1000L); // 保底基数为 1000，因此 increment 出来的第一个号必 >= 1001

        // 如果 Redis 中不存在该 Key，初始化为 baseId
        stringRedisTemplate.opsForValue().setIfAbsent(seqKey, String.valueOf(baseId));

        Long nextId = stringRedisTemplate.opsForValue().increment(seqKey);
        // 双重保险：如果 Redis 里的旧值比 baseId 还小（例如之前从 1 开始发的号），强制纠正跳到 baseId + 1
        if (nextId != null && nextId <= baseId) {
            nextId = baseId + 1;
            stringRedisTemplate.opsForValue().set(seqKey, String.valueOf(nextId));
        }
        return nextId;
    }

    private void validateZipFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传测试用例压缩包文件！");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException("测试用例必须是 .zip 压缩包格式！");
        }
    }

    /**
     * 先清空目标目录下的旧测试点，再解压新 Zip 包
     */
    private void extractZipToDir(MultipartFile file, Path targetDir) {
        try {
            if (Files.exists(targetDir)) {
                try (Stream<Path> paths = Files.list(targetDir)) {
                    for (Path p : paths.toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            } else {
                Files.createDirectories(targetDir);
            }

            int fileCount = 0;
            try (InputStream is = file.getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;

                    String entryName = entry.getName();
                    // 过滤 macOS 隐藏目录与隐藏文件
                    if (entryName.contains("__MACOSX") || Path.of(entryName).getFileName().toString().startsWith(".")) {
                        continue;
                    }

                    Path pureFileName = Path.of(entryName).getFileName();
                    if (pureFileName == null) continue;

                    Path filePath = targetDir.resolve(pureFileName).normalize();
                    if (!filePath.startsWith(targetDir)) {
                        throw new BusinessException("恶意压缩包：检测到路径穿越攻击！");
                    }

                    try (OutputStream os = Files.newOutputStream(filePath)) {
                        zis.transferTo(os);
                    }
                    zis.closeEntry();
                    fileCount++;
                }
            }
            if (fileCount == 0) {
                throw new BusinessException("压缩包内没有有效的测试用例文件！");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            log.error("测试用例解压失败, targetDir={}", targetDir, e);
            throw new BusinessException("测试用例解压失败: " + e.getMessage());
        }
    }
}