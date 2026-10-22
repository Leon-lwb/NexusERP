package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.ai.domain.vo.AiModelVo;
import com.nexus.common.ai.enums.AiModelCapabilityEnum;
import com.nexus.common.ai.enums.AiModelTypeEnum;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.mapper.AiProviderMapper;
import com.nexus.common.ai.service.AiApiKeyDecryptor;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.ApplicationEvent;
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
                .eq(AiModel::getStatus, AiModelConfigurationContract.ACTIVE)
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
        // 文本默认优先；无文本默认时回退视觉默认（视觉模型可对话，是文本能力超集）
        AiModel model = selectDefaultByType(AiModelTypeEnum.TEXT.getCode());
        if (model == null) {
            model = selectDefaultByType(AiModelTypeEnum.VISION.getCode());
        }
        return model;
    }

    @Override
    public AiModel selectDefaultVisionModel() {
        return selectDefaultByType(AiModelTypeEnum.VISION.getCode());
    }

    @Override
    public AiModel selectDefaultImageModel() {
        return selectDefaultByType(AiModelTypeEnum.IMAGE.getCode());
    }

    /**
     * 按类型取默认启用模型（含厂商信息）
     */
    private AiModel selectDefaultByType(String modelType) {
        AiModel model = baseMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelType, modelType)
                .eq(AiModel::getIsDefault, AiModelConfigurationContract.DEFAULT_YES)
                .eq(AiModel::getStatus, AiModelConfigurationContract.ACTIVE)
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
        normalizeAndValidate(bo);
        AiModel entity = MapstructUtils.convert(bo, AiModel.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiModelBo bo) {
        normalizeAndValidate(bo);
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
        // 查询目标模型的类型，只取消同类型的默认模型
        AiModel target = baseMapper.selectById(id);
        if (target == null) {
            return 0;
        }
        String modelType = target.getModelType();
        AiModel unset = new AiModel();
        unset.setIsDefault(AiModelConfigurationContract.DEFAULT_NO);
        baseMapper.update(unset, new LambdaQueryWrapper<AiModel>()
            .eq(AiModel::getModelType, modelType)
            .eq(AiModel::getIsDefault, AiModelConfigurationContract.DEFAULT_YES));
        // 设置目标模型为默认
        AiModel set = new AiModel();
        set.setId(id);
        set.setIsDefault(AiModelConfigurationContract.DEFAULT_YES);
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
                .eq(AiModel::getModelType, AiModelTypeEnum.EMBEDDING.getCode())
                .eq(AiModel::getIsDefault, AiModelConfigurationContract.DEFAULT_YES)
                .eq(AiModel::getStatus, AiModelConfigurationContract.ACTIVE)
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
        // modelType 支持逗号分隔多类型（如聊天选择器传 TEXT,VISION），单值仍走 eq
        if (StringUtils.isNotBlank(bo.getModelType())) {
            if (bo.getModelType().contains(",")) {
                List<String> types = StringUtils.splitList(bo.getModelType(), ",");
                lqw.in(AiModel::getModelType, types);
            } else {
                lqw.eq(AiModel::getModelType, bo.getModelType());
            }
        }
        lqw.eq(bo.getProviderId() != null, AiModel::getProviderId, bo.getProviderId());
        lqw.eq(StringUtils.isNotBlank(bo.getIsDefault()), AiModel::getIsDefault, bo.getIsDefault());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), AiModel::getStatus, bo.getStatus());
        // 能力列（逗号分隔 CSV）过滤：走跨方言可移植的 find_in_set 表达式。
        // 注意用 if 守卫而非 apply(condition, expr)：apply 的表达式参数会被无条件求值，
        // 空筛选时会白白触发一次数据库方言探测
        if (StringUtils.isNotBlank(bo.getRequiredInputCapability())) {
            validateCapabilityFilter(bo.getRequiredInputCapability());
            lqw.apply(AiModelConfigurationContract.capabilityExpression(bo.getRequiredInputCapability(), "input_capabilities"));
        }
        if (StringUtils.isNotBlank(bo.getRequiredOutputCapability())) {
            validateCapabilityFilter(bo.getRequiredOutputCapability());
            lqw.apply(AiModelConfigurationContract.capabilityExpression(bo.getRequiredOutputCapability(), "output_capabilities"));
        }
        return lqw;
    }

    /**
     * 校验能力筛选参数
     * <p>能力筛选仅允许单个合法能力码（字典 ai_model_capability，如 TEXT）；
     * 未知值与逗号多值直接拒绝，防止请求参数原样拼入 find_in_set 表达式
     * 造成 SQL 语法错误（500）。</p>
     *
     * @param capability 能力筛选值
     */
    private void validateCapabilityFilter(String capability) {
        if (!AiModelCapabilityEnum.isKnown(capability)) {
            throw new ServiceException("非法的模型能力参数: " + capability);
        }
    }

    /**
     * 落库前规范化能力串并校验参数约束
     * <p>能力串按枚举声明顺序去重规范化（如 "image,text" → "TEXT,IMAGE"，空值原样保留），
     * 未知能力值直接拒绝；maxTokens 不允许超过 contextWindow。</p>
     *
     * @param bo 模型业务对象
     */
    private void normalizeAndValidate(AiModelBo bo) {
        try {
            bo.setInputCapabilities(AiModelCapabilityEnum.canonicalize(bo.getInputCapabilities()));
            bo.setOutputCapabilities(AiModelCapabilityEnum.canonicalize(bo.getOutputCapabilities()));
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
        if (bo.getMaxTokens() != null && bo.getContextWindow() != null
            && bo.getMaxTokens() > bo.getContextWindow()) {
            throw new ServiceException("最大输出 Token（" + bo.getMaxTokens()
                + "）不能超过上下文窗口（" + bo.getContextWindow() + "）");
        }
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
            model.setApiKey(AiApiKeyDecryptor.decrypt(stringEncryptor, provider.getApiKey()));
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

    /** AI 模型配置变更事件（用于通知缓存清除等后续操作） */
    public static class AiModelChangeEvent extends ApplicationEvent {
        private final Long modelId;

        public AiModelChangeEvent(Object source, Long modelId) {
            super(source);
            this.modelId = modelId;
        }

        public Long getModelId() {
            return modelId;
        }
    }
}
