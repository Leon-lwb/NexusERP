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
     * 连接测试（会产生实际计费，调用方必须先取得用户二次确认）
     * <p>与 {@link #generateImage(ImageContext)} 相同的上游请求链路，但校验厂商返回后立即丢弃
     * 产物——不持久化 OSS、不写历史记录。默认抛出不支持，厂商实现按需覆写。</p>
     *
     * @param ctx 图片生成上下文（含模型配置、prompt、尺寸等）
     * @throws UnsupportedOperationException 厂商未实现连接测试
     */
    default void testConnection(ImageContext ctx) {
        throw new UnsupportedOperationException("该文生图厂商暂不支持连接测试");
    }

    /**
     * 厂商名称（ImageServiceFactory 注册键；工厂另支持厂商类型码别名直查，如 DASHSCOPE）
     */
    String getProviderName();
}
