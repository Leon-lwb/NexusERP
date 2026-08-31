package com.nexus.online.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 主子表结构数据提交 DTO，封装主表数据与子表数据集合，用于一次性保存主子表。
 */
@Data
public class MainSubDataDTO {
    /** 主表数据，字段名到字段值的映射。 */
    private Map<String, Object> main;
    /** 子表数据集合，每个元素为一行子表记录。 */
    private List<Map<String, Object>> subs;
}
