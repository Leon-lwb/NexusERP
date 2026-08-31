package com.nexus.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文生图请求 DTO
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Data
public class ImageGenerateRequest {

    /** 提示词（必填） */
    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /** 尺寸，可选，默认 1280*1280 */
    private String size;

    /** 图像模型ID（可选，为空使用默认 IMAGE 模型） */
    private Long modelId;
}
