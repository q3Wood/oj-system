package com.oj.backend.dto;

import com.oj.backend.common.PageQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户多条件分页查询请求参数")
public class UserQueryDTO extends PageQueryDTO {

    @Schema(description = "用户名搜索关键字（支持模糊匹配）")
    private String keyword;

    @Schema(description = "角色筛选 (USER / ADMIN / ROOT)")
    private String role;
}