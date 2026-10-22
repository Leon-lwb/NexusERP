package com.nexus.common.ai.service.impl;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiProviderBo;
import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.ai.domain.vo.AiProviderVo;
import com.nexus.common.ai.mapper.AiProviderMapper;
import com.nexus.common.ai.service.IAiProviderService;
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
 * AI 厂商配置 Service 业务层处理
 *
 * @author NexusERP
 */
@RequiredArgsConstructor
@Service
public class AiProviderServiceImpl implements IAiProviderService {

    private final AiProviderMapper baseMapper;
    private final StringEncryptor stringEncryptor;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询AI厂商配置分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return AI厂商配置分页列表
     */
    @Override
    public TableDataInfo<AiProviderVo> selectPageList(AiProviderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiProvider> lqw = buildQueryWrapper(bo);
        Page<AiProviderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        maskApiKey(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询AI厂商配置列表
     *
     * @param bo 查询参数
     * @return AI厂商配置列表
     */
    @Override
    public List<AiProviderVo> selectList(AiProviderBo bo) {
        List<AiProviderVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        maskApiKey(list);
        return list;
    }

    /**
     * 根据ID查询AI厂商配置
     *
     * @param id AI厂商配置ID
     * @return AI厂商配置信息
     */
    @Override
    public AiProviderVo selectById(Long id) {
        AiProviderVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            vo.setApiKey(maskApiKey(vo.getApiKey()));
        }
        return vo;
    }

    /**
     * 新增AI厂商配置
     *
     * @param bo AI厂商配置信息
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(AiProviderBo bo) {
        AiProvider entity = MapstructUtils.convert(bo, AiProvider.class);
        encryptApiKey(entity);
        return baseMapper.insert(entity);
    }

    /**
     * 修改AI厂商配置
     *
     * @param bo AI厂商配置信息
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiProviderBo bo) {
        AiProvider entity = MapstructUtils.convert(bo, AiProvider.class);
        // 如果 apiKey 是脱敏格式（****开头），说明前端未修改，不更新该字段
        if (entity.getApiKey() != null && entity.getApiKey().startsWith("****")) {
            entity.setApiKey(null);
        } else {
            encryptApiKey(entity);
        }
        int rows = baseMapper.updateById(entity);
        if (rows > 0) {
            // 厂商凭证/地址变更，已缓存的 ChatModel 持旧凭证需失效（S2-D B 修复）
            eventPublisher.publishEvent(new AiProviderChangeEvent(this, entity.getId()));
        }
        return rows;
    }

    /**
     * 批量删除AI厂商配置
     *
     * @param ids AI厂商配置ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        int rows = baseMapper.deleteBatchIds(List.of(ids));
        if (rows > 0) {
            for (Long id : ids) {
                eventPublisher.publishEvent(new AiProviderChangeEvent(this, id));
            }
        }
        return rows;
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<AiProvider> buildQueryWrapper(AiProviderBo bo) {
        LambdaQueryWrapper<AiProvider> lqw = Wrappers.<AiProvider>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getName()), AiProvider::getName, bo.getName())
            .eq(StringUtils.isNotBlank(bo.getProviderType()), AiProvider::getProviderType, bo.getProviderType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), AiProvider::getStatus, bo.getStatus());
        return lqw;
    }

    /**
     * 批量脱敏 API Key，仅保留最后4位
     *
     * @param list AI厂商配置VO列表
     */
    private void maskApiKey(List<AiProviderVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (AiProviderVo vo : list) {
            vo.setApiKey(maskApiKey(vo.getApiKey()));
        }
    }

    /**
     * 脱敏 API Key，仅保留最后4位
     *
     * @param apiKey 原始 API Key
     * @return 脱敏后的 API Key
     */
    private String maskApiKey(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return apiKey;
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 加密 API Key（Jasypt）
     *
     * @param entity 厂商实体
     */
    private void encryptApiKey(AiProvider entity) {
        if (StrUtil.isNotBlank(entity.getApiKey())) {
            entity.setApiKey(stringEncryptor.encrypt(entity.getApiKey()));
        }
    }

    /**
     * AI 厂商配置变更事件
     *
     * <p>厂商 apiKey/apiHost/providerType 变更后，已缓存的 ChatModel 仍持旧凭证， 监听器据此全量失效缓存（S2-D B 修复）
     */
    public static class AiProviderChangeEvent extends ApplicationEvent {
        private final Long providerId;

        public AiProviderChangeEvent(Object source, Long providerId) {
            super(source);
            this.providerId = providerId;
        }

        public Long getProviderId() {
            return providerId;
        }
    }

}
