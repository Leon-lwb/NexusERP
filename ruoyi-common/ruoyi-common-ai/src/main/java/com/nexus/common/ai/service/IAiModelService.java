package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.vo.AiModelVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

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

    /**
     * 分页查询模型列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 模型分页列表
     */
    TableDataInfo<AiModelVo> selectPageList(AiModelBo bo, PageQuery pageQuery);

    /**
     * 查询模型列表（不分页）
     *
     * @param bo 查询参数
     * @return 模型列表
     */
    List<AiModelVo> selectList(AiModelBo bo);

    /**
     * 新增模型配置
     *
     * @param bo 模型业务对象
     * @return 影响行数
     */
    int insertByBo(AiModelBo bo);

    /**
     * 修改模型配置
     *
     * @param bo 模型业务对象
     * @return 影响行数
     */
    int updateByBo(AiModelBo bo);

    /**
     * 批量删除模型配置
     *
     * @param ids 模型ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据ID查询模型详细信息（VO，含厂商名称等展示字段）
     *
     * @param id 模型ID
     * @return 模型视图对象
     */
    AiModelVo selectById(Long id);

    /**
     * 设置默认模型（先取消其他默认，再设置当前为默认）
     *
     * @param id 模型ID
     * @return 影响行数
     */
    int setDefaultModel(Long id);
}
