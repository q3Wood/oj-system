package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oj.backend.entity.Changelog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "最新更新日志摘要对象 (用于导航栏 NEW 提示)")
public class ChangelogLatestVO {

    @Schema(description = "最新日志唯一主键 ID")
    private Long id;

    @Schema(description = "最新版本号")
    private String version;

    @Schema(description = "最新更新标题")
    private String title;

    @Schema(description = "更新类型")
    private String type;

    @Schema(description = "发布时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime releaseDate;

    public static ChangelogLatestVO fromEntity(Changelog entity) {
        if (entity == null) return null;
        ChangelogLatestVO vo = new ChangelogLatestVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}