package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.entity.AiModel;

/**
 * AI 模型配置 Service 接口
 *
 * @author NexusERP
 */
public interface IAiModelService {

    /**
     * 根据模型名称查询模型配置（含厂商信息）
     *
     * @param modelName 模型名称
     * @return 模型配置（含厂商 apiHost/apiKey/providerType）
     */
    AiModel selectModelByName(String modelName);

    /**
     * 根据ID查询模型配置（含厂商信息）
     *
     * @param id 模型ID
     * @return 模型配置（含厂商 apiHost/apiKey/providerType）
     */
    AiModel selectModelById(Long id);

    /**
     * 查询默认模型配置（含厂商信息）
     *
     * @return 默认模型配置
     */
    AiModel selectDefaultModel();
}
