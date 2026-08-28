package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI SQL Few-Shot 示例实体
 * <p>对应 ai_sql_example 表，存储供 Agent 参考的 SQL 查询示例</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_sql_example")
public class AiSqlExample extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 用户问题（自然语言） */
    private String question;

    /** SQL 模板（可含 {{variable}} 占位符） */
    private String sqlTemplate;

    /** 业务分类（字典 ai_sql_example_category：SUBMISSION/REVIEW/GENERAL 在用，INVENTORY/SALES/PURCHASE/PRODUCTION 为 ERP 时期历史留档） */
    private String category;

    /** 描述 */
    private String description;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;
}
