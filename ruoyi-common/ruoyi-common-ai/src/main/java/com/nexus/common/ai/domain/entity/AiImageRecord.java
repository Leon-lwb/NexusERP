package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 文生图记录实体
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_image_record")
public class AiImageRecord extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 生成用户ID */
    private Long userId;

    /** 提示词 */
    private String prompt;

    /** 图片URL（OSS） */
    private String imageUrl;

    /** 尺寸（如 1280*1280） */
    private String size;

    /** 使用的模型ID */
    private Long modelId;

    /** 模型名称 */
    private String modelName;
}
