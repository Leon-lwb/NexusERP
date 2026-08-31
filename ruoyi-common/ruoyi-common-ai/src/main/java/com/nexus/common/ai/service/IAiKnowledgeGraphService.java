package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiKnowledgeGraphBo;
import com.nexus.common.ai.domain.vo.AiKnowledgeGraphVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

/**
 * AI 知识图谱实例 Service 接口
 * <p>提供图谱实例的查询操作（构建/重建/删除由业务层 KnowledgeGraphService 负责）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface IAiKnowledgeGraphService {

    /**
     * 根据知识库ID查询图谱实例
     *
     * @param knowledgeId 知识库ID
     * @return 图谱实例视图对象（不存在返回 null）
     */
    AiKnowledgeGraphVo selectByKnowledgeId(Long knowledgeId);

    /**
     * 分页查询图谱列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKnowledgeGraphVo> selectPageList(AiKnowledgeGraphBo bo, PageQuery pageQuery);
}
