package com.oj.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.backend.common.BusinessException;
import com.oj.backend.common.UserContext;
import com.oj.backend.dto.AgentAnalyzeRequestDTO;
import com.oj.backend.entity.*;
import com.oj.backend.mapper.*;
import com.oj.backend.service.AiCoachService;
import com.oj.backend.vo.AiAnalysisReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiCoachServiceImpl implements AiCoachService {

    private static final String LOCK_KEY_PREFIX = "lock:ai:analyze:";
    private static final String DAILY_QUOTA_KEY_PREFIX = "limit:ai:daily:";
    private static final int MAX_DAILY_QUOTA = 20;

//    @Value("${oj.ai.agent-url:http://127.0.0.1:8000/api/v1/analyze}")
//    private String agentAnalyzeUrl;

    // 1. 替换 AiCoachServiceImpl 顶部的 @Value 注解：
    @Value("${oj.agent.base-url:http://127.0.0.1:8000}")
    private String agentBaseUrl;

    @Value("${oj.agent.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${oj.agent.read-timeout-ms:35000}")
    private long readTimeoutMs;


    @Autowired
    private SubmissionMapper submissionMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private ContestMapper contestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SolutionMapper solutionMapper;

    @Autowired
    private AiAnalysisMapper aiAnalysisMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // 复用 JDK 原生 HttpClient，连接超时 5s
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public AiAnalysisReportVO analyzeSubmission(Long submissionId) {
        // ==================== 1. 登录与基础实体校验 ====================
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "请先登录后再使用 AI 代码诊断功能！");
        }

        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在！(ID: " + submissionId + ")");
        }

        User currentUser = userMapper.selectById(currentUserId);
        boolean isAdmin = currentUser != null &&
                ("ADMIN".equals(currentUser.getRole()) || "ROOT".equals(currentUser.getRole()));
        boolean isSelf = submission.getUserId().equals(currentUserId);

        // 规矩 1：触发所有权限制（仅限提交者本人或管理员触发消耗 Token）
        if (!isSelf && !isAdmin) {
            throw new BusinessException(403, "权限不足：仅允许对您本人的提交记录发起 AI 代码诊断！");
        }

        // ==================== 2. 赛事与保密题防作弊红线 ====================
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null) {
            throw new BusinessException("关联题目不存在！(ID: " + submission.getProblemId() + ")");
        }

        if (!isAdmin) {
            if (submission.getContestId() != null) {
                Contest contest = contestMapper.selectById(submission.getContestId());
                if (contest != null && LocalDateTime.now().isBefore(contest.getEndTime())) {
                    throw new BusinessException(403, "比赛正在进行中，严禁使用 AI 代码诊断功能！请待比赛结束后再进行复盘。");
                }
            } else {
                if (problem.getIsPublic() != null && problem.getIsPublic() == 0) {
                    throw new BusinessException(403, "该题目属于未公开的保密题，暂不支持使用 AI 代码诊断！");
                }
            }
        }

        // ==================== 3. 评测终态前置校验 ====================
        String verdict = submission.getVerdict();
        if (verdict == null || "PENDING".equalsIgnoreCase(verdict) || "RUNNING".equalsIgnoreCase(verdict)) {
            throw new BusinessException("代码尚在排队或评测中，请等待评测出结果后再请求 AI 诊断！");
        }

        // ==================== 4. 幂等查询：若已生成过报告则直接返回 ====================
        AiAnalysis existingAnalysis = getBySubmissionId(submissionId);
        if (existingAnalysis != null && existingAnalysis.getRawReport() != null) {
            return parseRawReport(existingAnalysis.getRawReport());
        }

        // ==================== 5. 每日配额前置检查 (普通用户 20 次/天) ====================
        String todayStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String quotaKey = DAILY_QUOTA_KEY_PREFIX + todayStr + ":" + currentUserId;
        if (!isAdmin) {
            String usedCountStr = stringRedisTemplate.opsForValue().get(quotaKey);
            int usedCount = usedCountStr == null ? 0 : Integer.parseInt(usedCountStr);
            if (usedCount >= MAX_DAILY_QUOTA) {
                throw new BusinessException("您今日的 AI 代码诊断次数已达上限 (" + MAX_DAILY_QUOTA + "/" + MAX_DAILY_QUOTA + ")，请明日再来！");
            }
        }

        // ==================== 6. Redis 分布式防重短锁 (防双击并发穿透) ====================
        String lockKey = LOCK_KEY_PREFIX + submissionId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("AI 正在全力诊断该份代码中，请勿重复点击！");
        }

        try {
            // 双重检查锁 (Double-Check)：防止前一个并发请求刚刚落库释放锁
            existingAnalysis = getBySubmissionId(submissionId);
            if (existingAnalysis != null && existingAnalysis.getRawReport() != null) {
                return parseRawReport(existingAnalysis.getRawReport());
            }

            // ==================== 7. 组装发往 Python Agent 的上下文载荷 ====================
            AgentAnalyzeRequestDTO agentReq = buildAgentRequest(submission, problem);
            String requestBodyJson = objectMapper.writeValueAsString(agentReq);

            String fullAnalyzeUrl = agentBaseUrl.endsWith("/")
                    ? agentBaseUrl + "api/v1/analyze"
                    : agentBaseUrl + "/api/v1/analyze";
            // ==================== 8. 调用 Python FastAPI 微服务 ====================
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fullAnalyzeUrl))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(readTimeoutMs)) // 对齐 Python LLM 30s 超时 + 网络开销
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Python Agent 返回非 200 状态码: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException("AI 诊断服务暂时繁忙，请稍后再试！");
            }

            String rawReportJson = response.body();
            // 利用 @JsonAlias 将下划线 JSON 反序列化为标准小驼峰 VO
            AiAnalysisReportVO reportVO = objectMapper.readValue(rawReportJson, AiAnalysisReportVO.class);

            // ==================== 9. 持久化落库 tb_ai_analysis ====================
            AiAnalysis entity = AiAnalysis.builder()
                    .submissionId(submission.getId())
                    .userId(submission.getUserId())
                    .problemId(submission.getProblemId())
                    .judgeStatus(submission.getVerdict())
                    .congratulation(reportVO.getCongratulation())
                    .methodAnalysis(objectMapper.writeValueAsString(reportVO.getMethod()))
                    .efficiencyAnalysis(objectMapper.writeValueAsString(reportVO.getEfficiency()))
                    .styleAnalysis(objectMapper.writeValueAsString(reportVO.getStyle()))
                    .rawReport(rawReportJson)
                    .modelName("deepseek-chat")
                    .tokenCost(0)
                    .build();

            try {
                aiAnalysisMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                // 极端并发兜底：唯一索引冲突说明已有记录落库，直接忽略异常
                log.warn("tb_ai_analysis 唯一索引兜底触发, submissionId={}", submissionId);
            }

            // ==================== 10. 成功后递增用户今日配额计数器 ====================
            if (!isAdmin) {
                Long currentCount = stringRedisTemplate.opsForValue().increment(quotaKey);
                if (currentCount != null && currentCount == 1L) {
                    stringRedisTemplate.expire(quotaKey, 24, TimeUnit.HOURS);
                }
            }

            return reportVO;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 代码诊断微服务异常, submissionId={}", submissionId, e);
            throw new BusinessException("AI 诊断服务连接超时或异常，请稍后重试！（原因：" + e.getMessage() + "）");
        } finally {
            // 无论成功或异常，确保释放分布式锁
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 辅助方法：根据 submissionId 查询已存在的 AI 报告
     */
    private AiAnalysis getBySubmissionId(Long submissionId) {
        return aiAnalysisMapper.selectOne(
                new LambdaQueryWrapper<AiAnalysis>()
                        .eq(AiAnalysis::getSubmissionId, submissionId)
        );
    }

    /**
     * 辅助方法：将数据库中的 raw_report 字符串反序列化为 AiAnalysisReportVO
     */
    private AiAnalysisReportVO parseRawReport(String rawReport) {
        try {
            return objectMapper.readValue(rawReport, AiAnalysisReportVO.class);
        } catch (Exception e) {
            log.error("反序列化历史 AI 报告失败: {}", e.getMessage());
            throw new BusinessException("解析历史 AI 诊断报告失败！");
        }
    }

    /**
     * 辅助方法：组装发往 Python Agent 的请求体 (含报错信息提取与最高赞题解检索)
     */
    private AgentAnalyzeRequestDTO buildAgentRequest(Submission submission, Problem problem) {
        String errorMessage = null;
        String judgeDetails = submission.getJudgeDetails();
        String verdict = submission.getVerdict();

        // 若非 Accepted，提取 judgeDetails 中的编译错误日志或失败测试点摘要供 AI 诊断参考
        if (!"Accepted".equalsIgnoreCase(verdict) && judgeDetails != null && !judgeDetails.isBlank()) {
            try {
                JsonNode rootNode = objectMapper.readTree(judgeDetails);
                if (rootNode.isArray()) {
                    // 遍历测试点数组，找到第一个非 Accepted 的测试点信息（兼容真实评测机的 id / time_ms / memory_kb 字段）
                    for (JsonNode caseNode : rootNode) {
                        String caseVerdict = caseNode.path("verdict").asText("");
                        if (!"Accepted".equalsIgnoreCase(caseVerdict)) {
                            String caseId = caseNode.has("id") ? caseNode.path("id").asText() : caseNode.path("caseId").asText("?");
                            String timeMs = caseNode.has("time_ms") ? caseNode.path("time_ms").asText() : caseNode.path("timeMs").asText("0");
                            String memoryKb = caseNode.has("memory_kb") ? caseNode.path("memory_kb").asText() : caseNode.path("memoryKb").asText("0");
                            String msg = caseNode.path("message").asText("");
                            errorMessage = String.format("在测试点 #%s 触发 [%s] (%s)，耗时 %sms，内存 %sKB",
                                    caseId, caseVerdict, msg, timeMs, memoryKb);
                            break;
                        }
                    }
                } else {
                    errorMessage = judgeDetails;
                }
            } catch (Exception e) {
                // 若 judgeDetails 不是 JSON（如纯文本编译エラー日志 CE），直接原样作为 errorMessage 传入
                errorMessage = judgeDetails.length() > 2000 ? judgeDetails.substring(0, 2000) : judgeDetails;
            }
        }

        // 尝试从题解表 (tb_solution) 中捞取本题点赞数最高的一篇题解作为 RAG 参考思路 (official_editorial)
        String editorial = null;
        try {
            Solution topSolution = solutionMapper.selectOne(
                    new LambdaQueryWrapper<Solution>()
                            .eq(Solution::getProblemId, problem.getId())
                            .orderByDesc(Solution::getLikes)
                            .last("LIMIT 1")
            );
            if (topSolution != null && topSolution.getContent() != null) {
                String content = topSolution.getContent();
                editorial = content.length() > 3000 ? content.substring(0, 3000) : content;
            }
        } catch (Exception e) {
            log.warn("查询题目关联题解作为 AI 参考时忽略异常: {}", e.getMessage());
        }

        return AgentAnalyzeRequestDTO.builder()
                .submissionId(submission.getId())
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .problemDescription(problem.getDescription())
                .language(submission.getLanguage())
                .userCode(submission.getCode())
                .judgeStatus(submission.getVerdict())
                .errorMessage(errorMessage)
                .officialEditorial(editorial)
                .failedTestCase(null)
                .build();
    }
}