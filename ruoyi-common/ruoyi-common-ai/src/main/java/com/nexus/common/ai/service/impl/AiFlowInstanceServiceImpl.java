package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiFlowInstanceBo;
import com.nexus.common.ai.domain.entity.AiFlow;
import com.nexus.common.ai.domain.entity.AiFlowInstance;
import com.nexus.common.ai.domain.entity.AiFlowNodeRun;
import com.nexus.common.ai.domain.vo.AiFlowInstanceVo;
import com.nexus.common.ai.domain.vo.AiFlowNodeRunVo;
import com.nexus.common.ai.mapper.AiFlowInstanceMapper;
import com.nexus.common.ai.mapper.AiFlowMapper;
import com.nexus.common.ai.mapper.AiFlowNodeRunMapper;
import com.nexus.common.ai.service.IAiFlowInstanceService;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 流程运行实例 Service 业务层处理
 *
 * @author NexusERP
 */
@RequiredArgsConstructor
@Service
public class AiFlowInstanceServiceImpl implements IAiFlowInstanceService {

    private final AiFlowInstanceMapper baseMapper;
    private final AiFlowNodeRunMapper aiFlowNodeRunMapper;
    private final AiFlowMapper aiFlowMapper;

    /**
     * 分页查询运行实例列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 实例分页列表
     */
    @Override
    public TableDataInfo<AiFlowInstanceVo> selectPageList(AiFlowInstanceBo bo, PageQuery pageQuery) {
        Page<AiFlowInstanceVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        fillFlowName(page.getRecords());
        // 列表不返回配置快照（体积大，仅详情接口用）
        page.getRecords().forEach(vo -> vo.setConfigSnapshot(null));
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID查询运行实例
     *
     * @param id 实例ID
     * @return 实例实体
     */
    @Override
    public AiFlowInstance selectById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 根据ID查询运行实例详情（VO，含流程名称）
     *
     * @param id 实例ID
     * @return 实例视图对象
     */
    @Override
    public AiFlowInstanceVo selectVoById(Long id) {
        AiFlowInstanceVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillFlowName(List.of(vo));
        }
        return vo;
    }

    /**
     * 查询实例的节点运行记录列表（按开始时间升序）
     *
     * @param instanceId 实例ID
     * @return 节点运行记录列表
     */
    @Override
    public List<AiFlowNodeRunVo> selectNodeRuns(Long instanceId) {
        return aiFlowNodeRunMapper.selectVoList(
            new LambdaQueryWrapper<AiFlowNodeRun>()
                .eq(AiFlowNodeRun::getInstanceId, instanceId)
                .orderByAsc(AiFlowNodeRun::getStartTime)
        );
    }

    /**
     * 新增运行实例
     *
     * @param instance 运行实例
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(AiFlowInstance instance) {
        return baseMapper.insert(instance);
    }

    /**
     * 更新运行实例
     *
     * @param instance 运行实例
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(AiFlowInstance instance) {
        return baseMapper.updateById(instance);
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<AiFlowInstance> buildQueryWrapper(AiFlowInstanceBo bo) {
        LambdaQueryWrapper<AiFlowInstance> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getFlowId() != null, AiFlowInstance::getFlowId, bo.getFlowId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), AiFlowInstance::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getTriggerType()), AiFlowInstance::getTriggerType, bo.getTriggerType());
        lqw.orderByDesc(AiFlowInstance::getCreateTime);
        return lqw;
    }

    /**
     * 填充流程名称（来自 ai_flow 关联查询）
     *
     * @param list 实例 VO 列表
     */
    private void fillFlowName(List<AiFlowInstanceVo> list) {
        for (AiFlowInstanceVo vo : list) {
            if (vo.getFlowId() != null) {
                AiFlow flow = aiFlowMapper.selectById(vo.getFlowId());
                if (flow != null) {
                    vo.setFlowName(flow.getFlowName());
                }
            }
        }
    }
}
