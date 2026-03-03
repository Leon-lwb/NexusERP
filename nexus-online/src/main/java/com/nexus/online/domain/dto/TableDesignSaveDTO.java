package com.nexus.online.domain.dto;

import lombok.Data;

@Data
public class TableDesignSaveDTO {
    private Long id;
    private String designName;
    private String tableName;
    private String tableComment;
    private String content;
}
