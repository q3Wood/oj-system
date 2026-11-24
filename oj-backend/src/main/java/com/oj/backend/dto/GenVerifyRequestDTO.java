package com.oj.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class GenVerifyRequestDTO {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("time_limit_ms")
    private Integer timeLimitMs = 1000;

    @JsonProperty("memory_limit_mb")
    private Integer memoryLimitMb = 256;

    @JsonProperty("gen_code")
    private String genCode;

    @JsonProperty("std_code")
    private String stdCode;

    @JsonProperty("brute_code")
    private String bruteCode;

    @JsonProperty("test_plan")
    private List<TestPlanItemDTO> testPlan;

    @Data
    public static class TestPlanItemDTO {
        private Integer id;
        private List<String> args;

        @JsonProperty("check_brute")
        private Boolean checkBrute = true;

        @JsonProperty("expect_brute_fail")
        private Boolean expectBruteFail = false;
    }
}