package com.nexus.common.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.flow.domain.bo.FlowInstanceBo;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.entity.FlowNodeRun;
import com.nexus.common.flow.domain.vo.FlowInstanceVo;
import com.nexus.common.flow.domain.vo.FlowNodeRunVo;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.flow.service.IFlowInstanceService;
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
public class FlowInstanceServiceImpl implements IFlowInstanceService {

    private final FlowInstanceMapper baseMapper;
    private final FlowNodeRunMapper FlowNodeRunMapper;
    private final FlowMapper FlowMapper;

    /**
     * 分页查询运行实例列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 实例分页列表
     */
    @Override
    public TableDataInfo<FlowInstanceVo> selectPageList(FlowInstanceBo bo, PageQuery pageQuery) {
        Page<FlowInstanceVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
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
    public FlowInstance selectById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 根据ID查询运行实例详情（VO，含流程名称）
     *
     * @param id 实例ID
     * @return 实例视图对象
     */
    @Override
    public FlowInstanceVo selectVoById(Long id) {
        FlowInstanceVo vo = baseMapper.selectVoById(id);
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
    public List<FlowNodeRunVo> selectNodeRuns(Long instanceId) {
        return FlowNodeRunMapper.selectVoList(
            new LambdaQueryWrapper<FlowNodeRun>()
                .eq(FlowNodeRun::getInstanceId, instanceId)
                .orderByAsc(FlowNodeRun::getStartTime)
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
    public int insert(FlowInstance instance) {
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
    public int update(FlowInstance instance) {
        return baseMapper.updateById(instance);
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<FlowInstance> buildQueryWrapper(FlowInstanceBo bo) {
        LambdaQueryWrapper<FlowInstance> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getFlowId() != null, FlowInstance::getFlowId, bo.getFlowId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), FlowInstance::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getTriggerType()), FlowInstance::getTriggerType, bo.getTriggerType());
        // 关键词：模糊匹配流程名称（经 ai_flow 关联）或实例ID
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String kw = bo.getKeyword().trim();
            // 流程名称匹配 → 收集匹配的流程ID
            List<Long> matchedFlowIds = FlowMapper.selectList(
                Wrappers.<Flow>lambdaQuery().like(Flow::getFlowName, kw)
            ).stream().map(Flow::getId).toList();
            // 纯数字且能解析为 Long（雪花ID 19 位内）时按实例ID精确匹配
            Long kwId = kw.matches("\\d{1,19}") ? tryParseLong(kw) : null;
            lqw.and(w -> {
                if (!matchedFlowIds.isEmpty()) {
                    w.in(FlowInstance::getFlowId, matchedFlowIds);
                }
                if (kwId != null) {
                    if (!matchedFlowIds.isEmpty()) {
                        w.or();
                    }
                    w.eq(FlowInstance::getId, kwId);
                }
                if (matchedFlowIds.isEmpty() && kwId == null) {
                    // 无匹配：永不命中（保证空结果而非全量）
                    w.eq(FlowInstance::getId, -1);
                }
            });
        }
        // 日期范围（startTime 落在 [beginTime, endTime]）
        lqw.ge(bo.getBeginTime() != null, FlowInstance::getStartTime, bo.getBeginTime());
        lqw.le(bo.getEndTime() != null, FlowInstance::getStartTime, bo.getEndTime());
        lqw.orderByDesc(FlowInstance::getCreateTime);
        return lqw;
    }

    /**
     * 宽松解析 Long（超范围返回 null，避免雪花ID溢出）
     */
    private Long tryParseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 填充流程名称（来自 ai_flow 关联查询）
     *
     * @param list 实例 VO 列表
     */
    private void fillFlowName(List<FlowInstanceVo> list) {
        for (FlowInstanceVo vo : list) {
            if (vo.getFlowId() != null) {
                Flow flow = FlowMapper.selectById(vo.getFlowId());
                if (flow != null) {
                    vo.setFlowName(flow.getFlowName());
                }
            }
        }
    }
}
