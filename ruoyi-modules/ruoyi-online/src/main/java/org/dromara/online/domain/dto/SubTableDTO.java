package org.dromara.online.domain.dto;

import lombok.Data;

// 子表信息DTO示例
@Data
public class SubTableDTO {
    private String tableName;  // 子表名称
    private String fkField;   // 子表中指向主表的外键字段名
}
