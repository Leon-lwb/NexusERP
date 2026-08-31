package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiKgRelationBo;
import com.nexus.common.ai.domain.vo.AiKgRelationVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI 知识图谱关系 Service 接口
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface IAiKgRelationService {

    /**
     * 分页查询关系列表（回填 sourceEntityName/targetEntityName）
     *
     * @param bo        查询条件（graphId/relationType）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKgRelationVo> selectPageList(AiKgRelationBo bo, PageQuery pageQuery);

    /**
     * 根据图谱ID查询全部关系
     *
     * @param graphId 图谱ID
     * @return 关系列表
     */
    List<AiKgRelationVo> selectByGraphId(Long graphId);
}
