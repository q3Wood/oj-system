package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oj.backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户信息视图对象 (脱敏)")
public class UserVO {

    @Schema(description = "用户唯一主键 ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户角色 (USER / ADMIN / ROOT)")
    private String role;

    @Schema(description = "注册时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 实体转 VO 静态工厂方法
     */
    public static UserVO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}