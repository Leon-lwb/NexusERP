package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiKgEntityBo;
import com.nexus.common.ai.domain.vo.AiKgEntityVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI 知识图谱实体 Service 接口
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface IAiKgEntityService {

    /**
     * 分页查询实体列表
     *
     * @param bo        查询条件（graphId/entityType/communityId/entityName）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKgEntityVo> selectPageList(AiKgEntityBo bo, PageQuery pageQuery);

    /**
     * 根据图谱ID查询全部实体
     *
     * @param graphId 图谱ID
     * @return 实体列表
     */
    List<AiKgEntityVo> selectByGraphId(Long graphId);
}
