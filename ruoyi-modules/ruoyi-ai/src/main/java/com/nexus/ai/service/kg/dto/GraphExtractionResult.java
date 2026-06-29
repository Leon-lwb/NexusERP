package com.nexus.ai.service.kg.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 图谱抽取结果 DTO（{@code GraphExtractionAgent} 输出 JSON 的反序列化目标）
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
public class GraphExtractionResult {

    /** 抽取到的实体列表 */
    private List<EntityDto> entities = new ArrayList<>();

    /** 抽取到的关系列表 */
    private List<RelationDto> relations = new ArrayList<>();

    /** 实体 DTO */
    @Data
    public static class EntityDto {
        /** 实体名称 */
        private String name;
        /** 实体类型 */
        private String type;
        /** 实体描述 */
        private String description;
        /** 置信度（0-1） */
        private Double confidence;
    }

    /** 关系 DTO */
    @Data
    public static class RelationDto {
        /** 头实体 name */
        private String source;
        /** 尾实体 name */
        private String target;
        /** 关系类型 */
        private String type;
        /** 关系描述 */
        private String description;
        /** 关系强度（0-10） */
        private Integer strength;
        /** 置信度（0-1） */
        private Double confidence;
    }
}
