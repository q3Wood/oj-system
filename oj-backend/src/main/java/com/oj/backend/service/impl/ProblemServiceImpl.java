package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.PageResult;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.ProblemCreateDTO;
import com.oj.backend.dto.ProblemUpdateDTO;
import com.oj.backend.entity.Problem;
import com.oj.backend.mapper.ProblemMapper;
import com.oj.backend.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProblemServiceImpl implements ProblemService {

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${oj.judge.problems-dir}")
    private String problemsDir;

    // 1. 【核心合一】：单接口服务全站！智能判断权限
    @Override
    public PageResult<Problem> listProblems(Integer pageNum, Integer pageSize, String keyword, Integer isPublic) {
        Page<Problem> page = new Page<>(pageNum, pageSize);
        String role = UserContext.getRole();
        boolean isAdminOrRoot = "ADMIN".equalsIgnoreCase(role) || "ROOT".equalsIgnoreCase(role);

        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .select(Problem::getId, Problem::getTitle, Problem::getTimeLimitMs,
                        Problem::getMemoryLimitMb, Problem::getAuthor, Problem::getIsPublic, Problem::getCreatedAt)
                .orderByAsc(Problem::getId);

        // 如果不是管理员，强制只查公开题目！
        if (!isAdminOrRoot) {
            wrapper.eq(Problem::getIsPublic,1);
        } else if (isPublic != null) {
            // 管理员可按公开状态筛选
            wrapper.eq(Problem::getIsPublic, isPublic);
        }

        // 多关键词灵活搜索 (支持按标题或出题人搜索)
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Problem::getTitle, kw).or().like(Problem::getAuthor, kw));
        }

        problemMapper.selectPage(page, wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    // 2. 查单题详情
    @Override
    public Problem getProblemById(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + id + ")");
        }
        String role = UserContext.getRole();
        boolean isAdminOrRoot = "ADMIN".equalsIgnoreCase(role) || "ROOT".equalsIgnoreCase(role);
        if (problem.getIsPublic() != null && problem.getIsPublic() == 0 && !isAdminOrRoot) {
            throw new BusinessException("该题目属于比赛保密题，暂未对公众开放！");
        }
        return problem;
    }

    // 3. 创建题目 (自动发号 + 解压)
    @Override
    public Problem createProblem(ProblemCreateDTO dto) {
        checkAdminOrRoot();

        if (dto.getFile().isEmpty() || !dto.getFile().getOriginalFilename().endsWith(".zip")) {
            throw new BusinessException("测试用例必须是 .zip 压缩包格式！");
        }

        Long newProblemId = stringRedisTemplate.opsForValue().increment("oj:problem:id:seq");
        Path targetDir = Path.of(problemsDir, String.valueOf(newProblemId));

        try {
            Files.createDirectories(targetDir);
            int fileCount = 0;
            try (InputStream is = dto.getFile().getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    Path pureFileName = Path.of(entry.getName()).getFileName();
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
        } catch (Exception e) {
            throw new BusinessException("测试用例解压失败: " + e.getMessage());
        }

        Problem problem = new Problem();
        problem.setId(newProblemId);
        problem.setTitle(dto.getTitle());
        problem.setDescription(dto.getDescription());
        problem.setTimeLimitMs(dto.getTimeLimitMs());
        problem.setMemoryLimitMb(dto.getMemoryLimitMb());
        problem.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : 1);
        problem.setAuthor(UserContext.getUsername() != null ? UserContext.getUsername() : "Admin");
        problem.setCreatedAt(LocalDateTime.now());
        problem.setUpdatedAt(LocalDateTime.now());

        problemMapper.insert(problem);
        return problem;
    }

    // 4. 统一更新题目
    @Override
    public Problem updateProblem(Long id, ProblemUpdateDTO dto) {
        checkAdminOrRoot();

        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在！(ID: " + id + ")");
        }

        // 【核心新增】：如果上传了新的测试用例压缩包，重新覆盖解压！
        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            if (!dto.getFile().getOriginalFilename().endsWith(".zip")) {
                throw new BusinessException("测试用例必须是 .zip 压缩包格式！");
            }
            Path targetDir = Path.of(problemsDir, String.valueOf(id));
            try {
                // 清理并重新解压
                extractZipToDir(dto.getFile(), targetDir);
                System.out.println(">>> [Problem] 题目 #" + id + " 的测试用例已被重新覆盖更新！");
            } catch (Exception e) {
                throw new BusinessException("测试用例更新解压失败: " + e.getMessage());
            }
        }
        problem.setTitle(dto.getTitle());
        problem.setDescription(dto.getDescription());
        problem.setTimeLimitMs(dto.getTimeLimitMs());
        problem.setMemoryLimitMb(dto.getMemoryLimitMb());
        if (dto.getIsPublic() != null) {
            problem.setIsPublic(dto.getIsPublic() == 1 ? 1 : 0);
        }
        problem.setUpdatedAt(LocalDateTime.now());

        problemMapper.updateById(problem);
        return problem;
    }

    private void checkAdminOrRoot() {
        String role = UserContext.getRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"ROOT".equalsIgnoreCase(role)) {
            throw new BusinessException("权限不足！只有管理员才允许进行题目管理操作。");
        }
    }

    // 抽离出来的通用解压私有方法 (createProblem 和 updateProblem 共用)
    private void extractZipToDir(MultipartFile file, Path targetDir) throws Exception {
        Files.createDirectories(targetDir);
        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path pureFileName = Path.of(entry.getName()).getFileName();
                if (pureFileName == null) continue;
                Path filePath = targetDir.resolve(pureFileName).normalize();
                if (!filePath.startsWith(targetDir)) {
                    throw new BusinessException("恶意压缩包：检测到路径穿越攻击！");
                }
                try (OutputStream os = Files.newOutputStream(filePath)) {
                    zis.transferTo(os);
                }
                zis.closeEntry();
            }
        }
    }
}


