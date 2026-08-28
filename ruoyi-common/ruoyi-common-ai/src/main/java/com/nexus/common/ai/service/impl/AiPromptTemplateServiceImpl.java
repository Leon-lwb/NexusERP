package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiPromptTemplateBo;
import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.ai.domain.vo.AiPromptTemplateVo;
import com.nexus.common.ai.mapper.AiPromptTemplateMapper;
import com.nexus.common.ai.service.IAiPromptTemplateService;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI Prompt 模板 Service 实现
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl implements IAiPromptTemplateService {

    private final AiPromptTemplateMapper baseMapper;

    /**
     * 分页查询 Prompt 模板
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<AiPromptTemplateVo> selectPageList(AiPromptTemplateBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiPromptTemplate> lqw = buildQueryWrapper(bo);
        Page<AiPromptTemplateVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询 Prompt 模板列表（不分页）
     *
     * @param bo 查询条件
     * @return 模板列表
     */
    @Override
    public List<AiPromptTemplateVo> selectList(AiPromptTemplateBo bo) {
        LambdaQueryWrapper<AiPromptTemplate> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据 ID 查询 Prompt 模板
     *
     * @param id 主键
     * @return 模板视图对象
     */
    @Override
    public AiPromptTemplateVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 根据模板标识查询模板实体
     *
     * @param templateKey 模板标识
     * @return 模板实体
     */
    @Override
    public AiPromptTemplate selectByTemplateKey(String templateKey) {
        LambdaQueryWrapper<AiPromptTemplate> lqw = Wrappers.lambdaQuery();
        lqw.eq(AiPromptTemplate::getTemplateKey, templateKey);
        lqw.eq(AiPromptTemplate::getStatus, AiModelConfigurationContract.ACTIVE);
        lqw.last("LIMIT 1");
        return baseMapper.selectOne(lqw);
    }

    /**
     * 新增 Prompt 模板
     * <p>自动设置默认值：version=1, status=0（启用，字典 sys_normal_disable）, isDefault=N（字典 sys_yes_no）</p>
     *
     * @param bo 模板业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(AiPromptTemplateBo bo) {
        AiPromptTemplate entity = MapstructUtils.convert(bo, AiPromptTemplate.class);
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(AiModelConfigurationContract.ACTIVE);
        }
        if (entity.getIsDefault() == null) {
            entity.setIsDefault(AiModelConfigurationContract.DEFAULT_NO);
        }
        return baseMapper.insert(entity);
    }

    /**
     * 修改 Prompt 模板
     * <p>修改时版本号基于数据库当前值+1，避免丢失更新</p>
     *
     * @param bo 模板业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiPromptTemplateBo bo) {
        AiPromptTemplate entity = MapstructUtils.convert(bo, AiPromptTemplate.class);
        // 修改时版本号+1（基于数据库当前值，避免丢失更新）
        if (entity.getId() != null) {
            AiPromptTemplate current = baseMapper.selectById(entity.getId());
            if (current != null) {
                entity.setVersion(current.getVersion() + 1);
            }
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除 Prompt 模板
     *
     * @param ids 主键数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteByIds(List.of(ids));
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询条件
     * @return LambdaQueryWrapper
     */
    private LambdaQueryWrapper<AiPromptTemplate> buildQueryWrapper(AiPromptTemplateBo bo) {
        LambdaQueryWrapper<AiPromptTemplate> lqw = Wrappers.lambdaQuery();
        lqw.like(bo.getTemplateName() != null, AiPromptTemplate::getTemplateName, bo.getTemplateName());
        lqw.eq(bo.getTemplateKey() != null, AiPromptTemplate::getTemplateKey, bo.getTemplateKey());
        lqw.eq(bo.getStatus() != null, AiPromptTemplate::getStatus, bo.getStatus());
        lqw.orderByDesc(AiPromptTemplate::getCreateTime);
        return lqw;
    }
}
