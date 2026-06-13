package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiPromptTemplateBo;
import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.ai.domain.vo.AiPromptTemplateVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI Prompt 模板 Service 接口
 *
 * @author NexusERP
 */
public interface IAiPromptTemplateService {

    /**
     * 分页查询 Prompt 模板
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiPromptTemplateVo> selectPageList(AiPromptTemplateBo bo, PageQuery pageQuery);

    /**
     * 查询 Prompt 模板列表
     *
     * @param bo 查询条件
     * @return 模板列表
     */
    List<AiPromptTemplateVo> selectList(AiPromptTemplateBo bo);

    /**
     * 根据 ID 查询 Prompt 模板
     *
     * @param id 主键
     * @return 模板视图对象
     */
    AiPromptTemplateVo selectById(Long id);

    /**
     * 根据模板标识查询模板内容
     *
     * @param templateKey 模板标识
     * @return 模板实体
     */
    AiPromptTemplate selectByTemplateKey(String templateKey);

    /**
     * 新增 Prompt 模板
     *
     * @param bo 模板业务对象
     * @return 影响行数
     */
    int insertByBo(AiPromptTemplateBo bo);

    /**
     * 修改 Prompt 模板
     *
     * @param bo 模板业务对象
     * @return 影响行数
     */
    int updateByBo(AiPromptTemplateBo bo);

    /**
     * 批量删除 Prompt 模板
     *
     * @param ids 主键数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
