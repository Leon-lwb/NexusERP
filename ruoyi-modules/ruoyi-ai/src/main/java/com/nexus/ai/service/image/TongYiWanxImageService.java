package com.nexus.ai.service.image;

import cn.hutool.core.io.IoUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.nexus.common.ai.domain.dto.image.ImageContext;
import com.nexus.common.ai.service.image.IImageGenerationService;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.entity.UploadResult;
import com.nexus.common.oss.factory.OssFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;

/**
 * 通义万相（DashScope）文生图实现
 * <p>使用 langchain4j-community-dashscope 传递依赖的 dashscope SDK 原生 API（ImageSynthesis）</p>
 * <p>生成图片后自动下载临时 URL 并上传到 OSS 长期保存（通义万相 URL 有时效）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Service
public class TongYiWanxImageService implements IImageGenerationService {

    @Override
    public String generateImage(ImageContext ctx) {
        try {
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                .apiKey(ctx.getModelConfig().getApiKey())
                .model(ctx.getModelConfig().getModelName())
                .prompt(ctx.getPrompt())
                .size(ctx.getSize() != null ? ctx.getSize() : "1280*1280")
                .n(1)
                .build();

            ImageSynthesisResult result = new ImageSynthesis().call(param);
            String tempUrl = result.getOutput().getResults().get(0).get("url");
            log.info("[文生图] 通义万相生成成功: prompt={}, tempUrl={}", ctx.getPrompt(), tempUrl);

            // 持久化到 OSS（临时 URL 有时效）
            return persistToOss(tempUrl, ctx.getPrompt());
        } catch (Exception e) {
            log.error("[文生图] 通义万相调用失败: prompt={}", ctx.getPrompt(), e);
            throw new RuntimeException("图片生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "Tongyiwanx";
    }

    /**
     * 下载临时图片 URL 并上传到 OSS 长期保存
     * <p>OSS 失败时兜底返回临时 URL + 告警</p>
     */
    private String persistToOss(String tempUrl, String prompt) {
        try {
            OssClient ossClient = OssFactory.instance();
            InputStream is = new URL(tempUrl).openStream();
            byte[] bytes = IoUtil.readBytes(is);
            IoUtil.close(is);

            UploadResult uploadResult = ossClient.uploadSuffix(bytes, ".png", "image/png");
            String ossUrl = uploadResult.getUrl();
            log.info("[文生图] 图片已保存到 OSS: {}", ossUrl);
            return ossUrl;
        } catch (Exception e) {
            log.warn("[文生图] OSS 保存失败，使用临时 URL（可能有时效）: {}", e.getMessage());
            return tempUrl;
        }
    }
}
