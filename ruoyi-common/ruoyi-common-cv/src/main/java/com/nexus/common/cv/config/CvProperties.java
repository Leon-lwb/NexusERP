package com.nexus.common.cv.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视觉识别配置属性
 * <p>前缀 nexus.vision,指向 nexus-vision-service 统一视觉服务</p>
 * <p>人脸识别阈值由 vision 服务端 FACE_RECOGNITION_THRESHOLD 单一控制,客户端不重复配置,避免双源漂移</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "nexus.vision")
public class CvProperties {

    /**
     * vision 服务基础地址(如 http://localhost:5001)
     */
    private String baseUrl = "http://localhost:5001";

    /**
     * API key(x-api-key 请求头,生产环境走环境变量注入)
     */
    private String apiKey = "";

    /**
     * HTTP 超时(毫秒)
     */
    private int timeoutMs = 5000;

    /**
     * 客户端上传前图片尺寸校验上限(像素)。超过建议前端压缩;vision 服务端另有 MAX_IMAGE_DIM 兜底
     */
    private int maxImageDim = 4096;

    /**
     * 是否启用视觉服务调用。false 时 VisionClient 直接降级返回空结果/抛出禁用异常,业务方走兜底
     */
    private boolean enabled = true;

    /**
     * 健康检查失败降级阈值:连续失败 N 次视为服务不可用
     */
    private int healthFailThreshold = 3;
}
