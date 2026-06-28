package com.nexus.common.ai.service.image;

import com.nexus.common.ai.domain.dto.image.ImageContext;

/**
 * AI 文生图服务接口（放 ruoyi-common-ai 供 Phase 6 工作流 ImageNode 复用）
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface IImageGenerationService {

    /**
     * 生成图片
     *
     * @param ctx 图片生成上下文（含模型配置、prompt、尺寸等）
     * @return 图片 URL
     */
    String generateImage(ImageContext ctx);

    /**
     * 厂商名称（对应 AiProperties.image.defaultProvider）
     */
    String getProviderName();
}
