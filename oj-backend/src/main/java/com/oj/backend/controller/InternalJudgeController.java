package com.oj.backend.controller;

import com.oj.backend.common.Result;
import com.oj.backend.dto.GenVerifyRequestDTO;
import com.oj.backend.dto.GenVerifyResultDTO;
import com.oj.backend.service.JudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/judge")
@RequiredArgsConstructor
public class InternalJudgeController {

    private final JudgeService judgeService;

    @PostMapping("/gen-verify")
    public Result<GenVerifyResultDTO> genVerify(@RequestBody GenVerifyRequestDTO requestDTO) {
        return Result.success(judgeService.executeGenVerify(requestDTO));
    }
}