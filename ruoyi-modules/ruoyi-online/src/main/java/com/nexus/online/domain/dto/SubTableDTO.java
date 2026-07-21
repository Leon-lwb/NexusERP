package com.nexus.online.domain.dto;

import lombok.Data;

/**
 * 子表信息 DTO，描述主子表场景中子表的名称与外键字段。
 */
@Data
public class SubTableDTO {
    /** 子表名称。 */
    private String tableName;
    /** 子表中指向主表的外键字段名。 */
    private String fkField;
}
