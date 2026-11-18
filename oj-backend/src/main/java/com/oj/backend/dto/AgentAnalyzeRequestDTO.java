package com.oj.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAnalyzeRequestDTO {

    @JsonProperty("submission_id")
    private Long submissionId;

    @JsonProperty("problem_id")
    private Long problemId;

    @JsonProperty("problem_title")
    private String problemTitle;

    @JsonProperty("problem_description")
    private String problemDescription;

    private String language;

    @JsonProperty("user_code")
    private String userCode;

    @JsonProperty("judge_status")
    private String judgeStatus;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("official_editorial")
    private String officialEditorial;

    @JsonProperty("failed_test_case")
    private FailedTestCaseDTO failedTestCase;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedTestCaseDTO {
        @JsonProperty("input_data")
        private String inputData;

        @JsonProperty("expected_output")
        private String expectedOutput;

        @JsonProperty("user_output")
        private String userOutput;
    }
}