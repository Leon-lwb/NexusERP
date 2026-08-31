package com.nexus.common.ai.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;

import java.util.List;

/**
 * AI知识库Service接口
 * <p>提供知识库的 CRUD 操作，删除时级联删除关联的文档和片段</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
public interface IAiKnowledgeService {

    /**
     * 分页查询知识库列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKnowledgeVo> selectPageList(AiKnowledgeBo bo, PageQuery pageQuery);

    /**
     * 查询知识库列表
     *
     * @param bo 查询条件
     * @return 知识库列表
     */
    List<AiKnowledgeVo> selectList(AiKnowledgeBo bo);

    /**
     * 根据ID查询知识库
     *
     * @param id 知识库ID
     * @return 知识库视图对象
     */
    AiKnowledgeVo selectById(Long id);

    /**
     * 新增知识库
     *
     * @param bo 知识库业务对象
     * @return 影响行数
     */
    int insertByBo(AiKnowledgeBo bo);

    /**
     * 修改知识库
     *
     * @param bo 知识库业务对象
     * @return 影响行数
     */
    int updateByBo(AiKnowledgeBo bo);

    /**
     * 批量删除知识库（级联删除文档和片段）
     *
     * @param ids 知识库ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
