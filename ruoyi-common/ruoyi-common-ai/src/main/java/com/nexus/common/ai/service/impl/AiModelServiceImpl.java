package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.ai.domain.vo.AiModelVo;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.mapper.AiProviderMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 模型配置 Service 实现
 *
 * @author NexusERP
 */
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements IAiModelService {

    private final AiModelMapper baseMapper;
    private final AiProviderMapper aiProviderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StringEncryptor stringEncryptor;

    @Override
    public AiModel selectModelByName(String modelName) {
        AiModel model = baseMapper.selectOne(
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
        AiModel model = baseMapper.selectById(id);
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    @Override
    public AiModel selectDefaultModel() {
        AiModel model = baseMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getIsDefault, "1")
                .eq(AiModel::getStatus, "1")
        );
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    @Override
    public TableDataInfo<AiModelVo> selectPageList(AiModelBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiModel> lqw = buildQueryWrapper(bo);
        Page<AiModelVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillProviderInfoForVoList(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<AiModelVo> selectList(AiModelBo bo) {
        List<AiModelVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillProviderInfoForVoList(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(AiModelBo bo) {
        AiModel entity = MapstructUtils.convert(bo, AiModel.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiModelBo bo) {
        AiModel entity = MapstructUtils.convert(bo, AiModel.class);
        int rows = baseMapper.updateById(entity);
        if (rows > 0 && entity.getId() != null) {
            eventPublisher.publishEvent(new AiModelChangeEvent(this, entity.getId()));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        int rows = baseMapper.deleteBatchIds(List.of(ids));
        if (rows > 0) {
            for (Long id : ids) {
                eventPublisher.publishEvent(new AiModelChangeEvent(this, id));
            }
        }
        return rows;
    }

    @Override
    public AiModelVo selectById(Long id) {
        AiModelVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillProviderInfoForVo(vo);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setDefaultModel(Long id) {
        // 先取消所有默认模型
        AiModel unset = new AiModel();
        unset.setIsDefault("0");
        baseMapper.update(unset, new LambdaQueryWrapper<AiModel>().eq(AiModel::getIsDefault, "1"));
        // 设置目标模型为默认
        AiModel set = new AiModel();
        set.setId(id);
        set.setIsDefault("1");
        int rows = baseMapper.updateById(set);
        if (rows > 0) {
            eventPublisher.publishEvent(new AiModelChangeEvent(this, id));
        }
        return rows;
    }

    /**
     * 查询默认 Embedding 模型配置（含厂商信息）
     */
    @Override
    public AiModel selectDefaultEmbeddingModel() {
        AiModel model = baseMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelType, "EMBEDDING")
                .eq(AiModel::getIsDefault, "1")
                .eq(AiModel::getStatus, "1")
        );
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<AiModel> buildQueryWrapper(AiModelBo bo) {
        LambdaQueryWrapper<AiModel> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getModelName()), AiModel::getModelName, bo.getModelName());
        lqw.eq(StringUtils.isNotBlank(bo.getModelType()), AiModel::getModelType, bo.getModelType());
        lqw.eq(bo.getProviderId() != null, AiModel::getProviderId, bo.getProviderId());
        lqw.eq(StringUtils.isNotBlank(bo.getIsDefault()), AiModel::getIsDefault, bo.getIsDefault());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), AiModel::getStatus, bo.getStatus());
        return lqw;
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
            model.setApiKey(decryptApiKey(provider.getApiKey()));
            model.setProviderType(provider.getProviderType());
        }
    }

    /**
     * 为单个 VO 填充厂商关联信息（providerName/apiHost/providerType）
     *
     * @param vo 模型视图对象
     */
    private void fillProviderInfoForVo(AiModelVo vo) {
        if (vo.getProviderId() != null) {
            AiProvider provider = aiProviderMapper.selectById(vo.getProviderId());
            if (provider != null) {
                vo.setProviderName(provider.getName());
                vo.setApiHost(provider.getApiHost());
                vo.setProviderType(provider.getProviderType());
            }
        }
    }

    /**
     * 为 VO 列表填充厂商关联信息（providerName/apiHost/providerType）
     *
     * @param list 模型 VO 列表
     */
    private void fillProviderInfoForVoList(List<AiModelVo> list) {
        for (AiModelVo vo : list) {
            if (vo.getProviderId() != null) {
                AiProvider provider = aiProviderMapper.selectById(vo.getProviderId());
                if (provider != null) {
                    vo.setProviderName(provider.getName());
                    vo.setApiHost(provider.getApiHost());
                    vo.setProviderType(provider.getProviderType());
                }
            }
        }
    }

    /**
     * AI 模型配置变更事件（用于通知缓存清除等后续操作）
     */
    public static class AiModelChangeEvent extends org.springframework.context.ApplicationEvent {
        private final Long modelId;

        public AiModelChangeEvent(Object source, Long modelId) {
            super(source);
            this.modelId = modelId;
        }

        public Long getModelId() {
            return modelId;
        }
    }

    /**
     * 解密 API Key（Jasypt）
     * <p>数据库中存储的是 Jasypt 加密后的密文，构建 ChatModel 时需要明文</p>
     *
     * @param encryptedApiKey 加密的 API Key
     * @return 解密后的 API Key
     */
    private String decryptApiKey(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isEmpty()) {
            return encryptedApiKey;
        }
        try {
            return stringEncryptor.decrypt(encryptedApiKey);
        } catch (Exception e) {
            // 如果解密失败，说明可能是明文（兼容旧数据）
            return encryptedApiKey;
        }
    }
}
