package com.nexus.common.ai.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.ai.domain.bo.AiProviderBo;
import com.nexus.common.ai.domain.vo.AiProviderVo;

import java.util.List;

/**
 * AI 厂商配置 Service 接口
 *
 * @author NexusERP
 */
public interface IAiProviderService {

    /**
     * 查询AI厂商配置分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return AI厂商配置分页列表
     */
    TableDataInfo<AiProviderVo> selectPageList(AiProviderBo bo, PageQuery pageQuery);

    /**
     * 查询AI厂商配置列表
     *
     * @param bo 查询参数
     * @return AI厂商配置列表
     */
    List<AiProviderVo> selectList(AiProviderBo bo);

    /**
     * 根据ID查询AI厂商配置
     *
     * @param id AI厂商配置ID
     * @return AI厂商配置信息
     */
    AiProviderVo selectById(Long id);

    /**
     * 新增AI厂商配置
     *
     * @param bo AI厂商配置信息
     * @return 影响行数
     */
    int insertByBo(AiProviderBo bo);

    /**
     * 修改AI厂商配置
     *
     * @param bo AI厂商配置信息
     * @return 影响行数
     */
    int updateByBo(AiProviderBo bo);

    /**
     * 批量删除AI厂商配置
     *
     * @param ids AI厂商配置ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

}
