package com.oj.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.BusinessException;
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
}