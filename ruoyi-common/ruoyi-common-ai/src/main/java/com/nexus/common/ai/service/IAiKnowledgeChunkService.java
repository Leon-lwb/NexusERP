package com.nexus.common.ai.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.ai.domain.bo.AiKnowledgeChunkBo;
import com.nexus.common.ai.domain.vo.AiKnowledgeChunkVo;

import java.util.List;

/**
 * AI知识库文档片段Service接口
 * <p>提供片段的查询和新增操作，支持按文档ID和知识库ID筛选</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
public interface IAiKnowledgeChunkService {

    /**
     * 分页查询片段列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKnowledgeChunkVo> selectPageList(AiKnowledgeChunkBo bo, PageQuery pageQuery);

    /**
     * 根据文档ID查询片段列表
     *
     * @param documentId 文档ID
     * @return 片段列表
     */
    List<AiKnowledgeChunkVo> selectByDocumentId(Long documentId);

    /**
     * 根据知识库ID查询片段列表
     *
     * @param knowledgeId 知识库ID
     * @return 片段列表
     */
    List<AiKnowledgeChunkVo> selectByKnowledgeId(Long knowledgeId);

    /**
     * 新增片段
     *
     * @param bo 片段业务对象
     * @return 影响行数
     */
    int insertByBo(AiKnowledgeChunkBo bo);

    /**
     * 根据文档ID删除片段
     *
     * @param documentId 文档ID
     * @return 影响行数
     */
    int deleteByDocumentId(Long documentId);
}
