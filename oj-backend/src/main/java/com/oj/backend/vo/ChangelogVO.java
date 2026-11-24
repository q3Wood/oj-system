package com.oj.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oj.backend.entity.Changelog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "系统更新日志视图对象")
public class ChangelogVO {

    @Schema(description = "日志唯一主键 ID")
    private Long id;

    @Schema(description = "语义化版本号")
    private String version;

    @Schema(description = "更新标题")
    private String title;

    @Schema(description = "更新类型: FEAT / OPTIMIZE / FIX / NOTICE")
    private String type;

    @Schema(description = "Markdown 更新正文")
    private String content;

    @Schema(description = "发布展示时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime releaseDate;

    @Schema(description = "发布者用户 ID")
    private Long authorId;

    @Schema(description = "发布者用户名")
    private String authorName;

    @Schema(description = "入库创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static ChangelogVO fromEntity(Changelog entity) {
        if (entity == null) return null;
        ChangelogVO vo = new ChangelogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}