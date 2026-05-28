package com.nexus.online.domain.dto;

import lombok.Data;

/**
 * 保存表设计的入参DTO
 */
@Data
public class TableDesignSaveDTO {
    private Long id;
    private String designName;
    private String tableName;
    private String tableComment;
    private String content;
}
