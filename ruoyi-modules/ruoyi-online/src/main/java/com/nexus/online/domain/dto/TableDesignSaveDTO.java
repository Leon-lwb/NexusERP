package com.nexus.online.domain.dto;

import lombok.Data;

/**
 * 保存表设计的入参DTO
 */
@Data
public class TableDesignSaveDTO {
    /** 设计 ID；更新时传入，新增时为空。 */
    private Long id;
    /** 表单设计名称。 */
    private String designName;
    /** 对应的物理表名。 */
    private String tableName;
    /** 表注释。 */
    private String tableComment;
    /** 表单设计内容（JSON 字符串，含表与字段结构）。 */
    private String content;
}
