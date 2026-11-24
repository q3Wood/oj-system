package com.oj.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GenVerifyResultDTO {
    private Boolean passed;
    private String stage;

    @JsonProperty("failed_target")
    @JsonAlias("failed_target")
    private String failedTarget;

    @JsonProperty("failed_case_id")
    @JsonAlias("failed_case_id")
    private Integer failedCaseId;

    private String verdict;
    private String message;

    @JsonProperty("compile_log")
    @JsonAlias("compile_log")
    private String compileLog;

    @JsonProperty("input_snippet")
    @JsonAlias("input_snippet")
    private String inputSnippet;

    @JsonProperty("std_output_snippet")
    @JsonAlias("std_output_snippet")
    private String stdOutputSnippet;

    @JsonProperty("brute_output_snippet")
    @JsonAlias("brute_output_snippet")
    private String bruteOutputSnippet;

    @JsonProperty("max_time_ms")
    @JsonAlias("max_time_ms")
    private Integer maxTimeMs;

    @JsonProperty("peak_memory_kb")
    @JsonAlias("peak_memory_kb")
    private Integer peakMemoryKb;

    @JsonProperty("brute_blocked")
    @JsonAlias("brute_blocked")
    private Boolean bruteBlocked;
}