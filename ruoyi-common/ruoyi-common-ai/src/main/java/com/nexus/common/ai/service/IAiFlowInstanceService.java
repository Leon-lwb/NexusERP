package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiFlowInstanceBo;
import com.nexus.common.ai.domain.entity.AiFlowInstance;
import com.nexus.common.ai.domain.vo.AiFlowInstanceVo;
import com.nexus.common.ai.domain.vo.AiFlowNodeRunVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI 流程运行实例 Service 接口
 *
 * @author NexusERP
 */
public interface IAiFlowInstanceService {

    /**
     * 分页查询运行实例列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 实例分页列表
     */
    TableDataInfo<AiFlowInstanceVo> selectPageList(AiFlowInstanceBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询运行实例
     *
     * @param id 实例ID
     * @return 实例实体
     */
    AiFlowInstance selectById(Long id);

    /**
     * 根据ID查询运行实例详情（VO，含流程名称）
     *
     * @param id 实例ID
     * @return 实例视图对象
     */
    AiFlowInstanceVo selectVoById(Long id);

    /**
     * 查询实例的节点运行记录列表（按开始时间升序）
     *
     * @param instanceId 实例ID
     * @return 节点运行记录列表
     */
    List<AiFlowNodeRunVo> selectNodeRuns(Long instanceId);

    /**
     * 新增运行实例
     *
     * @param instance 运行实例
     * @return 影响行数
     */
    int insert(AiFlowInstance instance);

    /**
     * 更新运行实例
     *
     * @param instance 运行实例
     * @return 影响行数
     */
    int update(AiFlowInstance instance);
}
