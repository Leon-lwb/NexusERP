package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.bo.AiFlowBo;
import com.nexus.common.ai.domain.entity.AiFlow;
import com.nexus.common.ai.domain.vo.AiFlowVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * AI 流程定义 Service 接口
 *
 * @author NexusERP
 */
public interface IAiFlowService {

    /**
     * 分页查询流程列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 流程分页列表
     */
    TableDataInfo<AiFlowVo> selectPageList(AiFlowBo bo, PageQuery pageQuery);

    /**
     * 查询流程列表（不分页）
     *
     * @param bo 查询参数
     * @return 流程列表
     */
    List<AiFlowVo> selectList(AiFlowBo bo);

    /**
     * 根据ID查询流程（实体，含 configJson）
     *
     * @param id 流程ID
     * @return 流程实体
     */
    AiFlow selectById(Long id);

    /**
     * 根据ID查询流程详情（VO）
     *
     * @param id 流程ID
     * @return 流程视图对象
     */
    AiFlowVo selectVoById(Long id);

    /**
     * 根据流程标识查询流程
     *
     * @param flowKey 流程标识
     * @return 流程实体
     */
    AiFlow selectByFlowKey(String flowKey);

    /**
     * 新增流程（存草稿）
     *
     * @param bo 流程业务对象
     * @return 影响行数
     */
    int insertByBo(AiFlowBo bo);

    /**
     * 修改流程（仅 DRAFT/OFFLINE 可编辑）
     *
     * @param bo 流程业务对象
     * @return 影响行数
     */
    int updateByBo(AiFlowBo bo);

    /**
     * 批量删除流程（逻辑删除，有运行中实例拒绝）
     *
     * @param ids 流程ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 发布流程（DRAFT→PUBLISHED，校验图合法性后版本+1）
     *
     * @param id 流程ID
     * @return 影响行数
     */
    int publish(Long id);

    /**
     * 下线流程（PUBLISHED→OFFLINE）
     *
     * @param id 流程ID
     * @return 影响行数
     */
    int offline(Long id);
}
