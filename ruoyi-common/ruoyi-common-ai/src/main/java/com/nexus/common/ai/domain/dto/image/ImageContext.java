package com.nexus.common.ai.domain.dto.image;

import com.nexus.common.ai.domain.entity.AiModel;
import lombok.Builder;
import lombok.Data;

/**
 * 文生图上下文
 *
 * @param modelConfig AI 模型配置（含 apiKey/modelName 等）
 * @param prompt      生成提示词
 * @param size        图片尺寸（如 "1280*1280"，null 使用默认）
 * @param seed        随机种子（null 使用随机）
 * @author NexusERP
 * @since 2.0.0
 */
@Data
@Builder
public class ImageContext {

    /** AI 模型配置 */
    private AiModel modelConfig;

    /** 生成提示词 */
    private String prompt;

    /** 图片尺寸（如 "1280*1280"） */
    private String size;

    /** 随机种子 */
    private Integer seed;
}
