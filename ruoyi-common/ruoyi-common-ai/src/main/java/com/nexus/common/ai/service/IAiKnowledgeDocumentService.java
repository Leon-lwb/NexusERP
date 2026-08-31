package com.nexus.common.ai.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.ai.domain.bo.AiKnowledgeDocumentBo;
import com.nexus.common.ai.domain.vo.AiKnowledgeDocumentVo;

import java.util.List;

/**
 * AI知识库文档Service接口
 * <p>提供文档的 CRUD 操作，删除时级联删除关联的片段</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
public interface IAiKnowledgeDocumentService {

    /**
     * 分页查询文档列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKnowledgeDocumentVo> selectPageList(AiKnowledgeDocumentBo bo, PageQuery pageQuery);

    /**
     * 根据知识库ID查询文档列表
     *
     * @param knowledgeId 知识库ID
     * @return 文档列表
     */
    List<AiKnowledgeDocumentVo> selectByKnowledgeId(Long knowledgeId);

    /**
     * 根据ID查询文档
     *
     * @param id 文档ID
     * @return 文档视图对象
     */
    AiKnowledgeDocumentVo selectById(Long id);

    /**
     * 新增文档
     *
     * @param bo 文档业务对象
     * @return 影响行数
     */
    int insertByBo(AiKnowledgeDocumentBo bo);

    /**
     * 修改文档
     *
     * @param bo 文档业务对象
     * @return 影响行数
     */
    int updateByBo(AiKnowledgeDocumentBo bo);

    /**
     * 批量删除文档（级联删除片段）
     *
     * @param ids 文档ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
