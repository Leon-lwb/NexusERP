package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.mapper.AiProviderMapper;
import com.nexus.common.ai.service.IAiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 模型配置 Service 实现
 *
 * @author NexusERP
 */
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements IAiModelService {

    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;

    @Override
    public AiModel selectModelByName(String modelName) {
        AiModel model = aiModelMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelName, modelName)
                .eq(AiModel::getStatus, "1")
        );
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    @Override
    public AiModel selectModelById(Long id) {
        AiModel model = aiModelMapper.selectById(id);
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    @Override
    public AiModel selectDefaultModel() {
        AiModel model = aiModelMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getIsDefault, "1")
                .eq(AiModel::getStatus, "1")
        );
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    /**
     * 填充厂商关联信息（apiHost/apiKey/providerType）
     *
     * @param model 模型配置实体
     */
    private void fillProviderInfo(AiModel model) {
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider != null) {
            model.setApiHost(provider.getApiHost());
            model.setApiKey(provider.getApiKey());
            model.setProviderType(provider.getProviderType());
        }
    }
}
