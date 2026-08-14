package com.nexus.common.flow.service;

import com.nexus.common.flow.domain.bo.FlowInstanceBo;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.vo.FlowInstanceVo;
import com.nexus.common.flow.domain.vo.FlowNodeRunVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI 流程运行实例 Service 接口
 *
 * @author NexusERP
 */
public interface IFlowInstanceService {

    /**
     * 分页查询运行实例列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 实例分页列表
     */
    TableDataInfo<FlowInstanceVo> selectPageList(FlowInstanceBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询运行实例
     *
     * @param id 实例ID
     * @return 实例实体
     */
    FlowInstance selectById(Long id);

    /**
     * 根据ID查询运行实例详情（VO，含流程名称）
     *
     * @param id 实例ID
     * @return 实例视图对象
     */
    FlowInstanceVo selectVoById(Long id);

    /**
     * 查询实例的节点运行记录列表（按开始时间升序）
     *
     * @param instanceId 实例ID
     * @return 节点运行记录列表
     */
    List<FlowNodeRunVo> selectNodeRuns(Long instanceId);

    /**
     * 新增运行实例
     *
     * @param instance 运行实例
     * @return 影响行数
     */
    int insert(FlowInstance instance);

    /**
     * 更新运行实例
     *
     * @param instance 运行实例
     * @return 影响行数
     */
    int update(FlowInstance instance);
}
