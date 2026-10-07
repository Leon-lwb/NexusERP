package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 知识图谱实例视图对象 ai_knowledge_graph
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiKnowledgeGraph.class)
public class AiKnowledgeGraphVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "主键")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 知识库ID */
    @ExcelProperty(value = "知识库ID")
    private Long knowledgeId;

    /** 图谱名称 */
    @ExcelProperty(value = "图谱名称")
    private String graphName;

    /** 状态（0未构建 1构建中 2已完成 3失败） */
    @ExcelProperty(value = "状态")
    private String graphStatus;

    /** 实体节点数 */
    @ExcelProperty(value = "实体数")
    private Integer nodeCount;

    /** 关系数 */
    @ExcelProperty(value = "关系数")
    private Integer relationshipCount;

    /** 社区数 */
    @ExcelProperty(value = "社区数")
    private Integer communityCount;

    /** 抽取用的LLM模型ID */
    private Long modelId;

    /** 实体类型（逗号分隔） */
    private String entityTypes;

    /** 关系类型（逗号分隔） */
    private String relationTypes;

    /** 图谱配置（JSON） */
    private String config;

    /** 错误信息 */
    private String errorMessage;
}
