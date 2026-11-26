package com.oj.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.BusinessException;
import com.oj.backend.dto.GenVerifyRequestDTO;
import com.oj.backend.dto.GenVerifyResultDTO;
import com.oj.backend.dto.JudgeResultDTO;
import com.oj.backend.service.JudgeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalProcessJudgeServiceImpl implements JudgeService {

    @Value("${oj.judge.binary-path}")
    private String judgeBinaryPath;

    @Value("${oj.judge.problems-dir}")
    private String problemsDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JudgeResultDTO executeJudge(Long problemId, String language, String code,
                                       Integer timeLimitMs, Integer memoryLimitMb) {
        // 1. 创建高并发安全的临时代码文件
        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        Path tempSrcPath = Path.of(System.getProperty("java.io.tmpdir"), "sub_" + uniqueId + "." + language);

        try {
            // 写入源代码
            Files.writeString(tempSrcPath, code);

            // 2. 组装命令参数
            String problemPath = problemsDir + "/" + problemId;
            ProcessBuilder processBuilder = new ProcessBuilder(
                    judgeBinaryPath,
                    "--prob-dir", problemPath,
                    "--src", tempSrcPath.toAbsolutePath().toString(),
                    "--lang", language,
                    "--time", String.valueOf(timeLimitMs),
                    "--mem", String.valueOf(memoryLimitMb),
                    "--json"
            );

            // 3. 启动子进程并等待执行
            Process process = processBuilder.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0 && stdout.trim().isEmpty()) {
                String stderr = new String(process.getErrorStream().readAllBytes());
                throw new BusinessException("评测机系统错误: " + stderr);
            }

            // 4. 解析 JSON 输出
            return objectMapper.readValue(stdout, JudgeResultDTO.class);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("评测执行失败: " + e.getMessage());
        } finally {
            // 5. 必须销毁临时源码，不留垃圾
            try {
                Files.deleteIfExists(tempSrcPath);
            } catch (Exception ignored) {}
        }
    }


    @Override
    public GenVerifyResultDTO executeGenVerify(GenVerifyRequestDTO req) {
        // 校验 taskId 格式防止路径穿越
        if (req.getTaskId() == null || !req.getTaskId().matches("^[a-zA-Z0-9_-]{1,64}$")) {
            throw new BusinessException("非法的 task_id 格式");
        }

        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path genPath = tmpDir.resolve("gen_" + uniqueId + ".cpp");
        Path stdPath = tmpDir.resolve("std_" + uniqueId + ".cpp");
        Path brutePath = tmpDir.resolve("brute_" + uniqueId + ".cpp");
        Path planPath = tmpDir.resolve("plan_" + uniqueId + ".txt");

        // 草稿隔离目录: {problemsDir}/_drafts/{taskId}
        Path draftProbDir = Path.of(problemsDir, "_drafts", req.getTaskId());

        try {
            Files.createDirectories(draftProbDir);
            // 清理该草稿目录上一轮重试残留的旧 .in/.out 文件，确保每次验证产物纯净
            try (var stream = Files.list(draftProbDir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
            Files.writeString(genPath, req.getGenCode());
            Files.writeString(stdPath, req.getStdCode());
            Files.writeString(brutePath, req.getBruteCode());

            // 组装行协议 plan.txt
            StringBuilder planSb = new StringBuilder();
            for (GenVerifyRequestDTO.TestPlanItemDTO item : req.getTestPlan()) {
                planSb.append(item.getId()).append(" ")
                        .append(Boolean.TRUE.equals(item.getCheckBrute()) ? 1 : 0).append(" ")
                        .append(Boolean.TRUE.equals(item.getExpectBruteFail()) ? 1 : 0);
                if (item.getArgs() != null) {
                    for (String arg : item.getArgs()) {
                        planSb.append(" ").append(arg);
                    }
                }
                planSb.append("\n");
            }
            Files.writeString(planPath, planSb.toString());

            ProcessBuilder pb = new ProcessBuilder(
                    judgeBinaryPath,
                    "--mode", "gen-verify",
                    "--prob-dir", draftProbDir.toAbsolutePath().toString(),
                    "--gen-src", genPath.toAbsolutePath().toString(),
                    "--std-src", stdPath.toAbsolutePath().toString(),
                    "--brute-src", brutePath.toAbsolutePath().toString(),
                    "--plan", planPath.toAbsolutePath().toString(),
                    "--time", String.valueOf(req.getTimeLimitMs()),
                    "--mem", String.valueOf(req.getMemoryLimitMb()),
                    "--json"
            );

            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0 && stdout.trim().isEmpty()) {
                String stderr = new String(process.getErrorStream().readAllBytes());
                throw new BusinessException("造题对拍沙箱错误: " + stderr);
            }

            return objectMapper.readValue(stdout, GenVerifyResultDTO.class);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("造题对拍执行失败: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(genPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(stdPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(brutePath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(planPath); } catch (Exception ignored) {}
        }
    }

}