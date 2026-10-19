package com.oj.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class JudgeResultDTO {
    private String verdict;

    @JsonProperty("max_time_ms")
    private Integer maxTimeMs;

    @JsonProperty("peak_memory_kb")
    private Integer peakMemoryKb;

    @JsonProperty("compile_log")
    private String compileLog;

    private List<CaseDetail> details;

    @Data
    public static class CaseDetail {
        private Integer id;
        private String verdict;
        @JsonProperty("time_ms")
        private Integer timeMs;
        @JsonProperty("memory_kb")
        private Integer memoryKb;
        @JsonProperty("exit_code")
        private Integer exitCode;
        private Integer signal;
        private String message;
    }
}