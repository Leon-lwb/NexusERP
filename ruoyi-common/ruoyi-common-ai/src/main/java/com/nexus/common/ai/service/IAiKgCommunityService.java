package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiKgCommunityBo;
import com.nexus.common.ai.domain.vo.AiKgCommunityVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI 知识图谱社区 Service 接口
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface IAiKgCommunityService {

    /**
     * 分页查询社区列表
     *
     * @param bo        查询条件（graphId/summaryStatus）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<AiKgCommunityVo> selectPageList(AiKgCommunityBo bo, PageQuery pageQuery);

    /**
     * 根据图谱ID查询全部社区
     *
     * @param graphId 图谱ID
     * @return 社区列表
     */
    List<AiKgCommunityVo> selectByGraphId(Long graphId);
}
