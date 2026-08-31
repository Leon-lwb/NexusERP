package com.nexus.common.ai.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.common.ai.domain.entity.AiProvider;

/**
 * AI 厂商配置业务对象 ai_provider
 *
 * @author NexusERP
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiProvider.class, reverseConvertGenerate = false)
public class AiProviderBo extends BaseEntity {

    /**
     * 主键
     */
    private Long id;

    /**
     * 厂商名称
     */
    @NotBlank(message = "厂商名称不能为空")
    private String name;

    /**
     * API 地址
     */
    @NotBlank(message = "API地址不能为空")
    private String apiHost;

    /**
     * API Key
     */
    @NotBlank(message = "API Key不能为空")
    private String apiKey;

    /**
     * 厂商类型: OPENAI/DASHSCOPE/ZHIPU/OLLAMA
     */
    @NotBlank(message = "厂商类型不能为空")
    private String providerType;

    /**
     * 状态（0启用 1停用，字典 sys_normal_disable）
     */
    private String status;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
