package com.nexus.common.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.flow.domain.bo.FlowBo;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.vo.FlowVo;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.service.IFlowService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 流程定义 Service 业务层处理
 *
 * @author NexusERP
 */
@RequiredArgsConstructor
@Service
public class FlowServiceImpl implements IFlowService {

    private final FlowMapper baseMapper;
    private final FlowInstanceMapper FlowInstanceMapper;
    private final com.nexus.common.flow.mapper.FlowVersionMapper FlowVersionMapper;

    /**
     * 分页查询流程列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 流程分页列表
     */
    @Override
    public TableDataInfo<FlowVo> selectPageList(FlowBo bo, PageQuery pageQuery) {
        Page<FlowVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询流程列表（不分页）
     *
     * @param bo 查询参数
     * @return 流程列表
     */
    @Override
    public List<FlowVo> selectList(FlowBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询流程（实体，含 configJson）
     *
     * @param id 流程ID
     * @return 流程实体
     */
    @Override
    public Flow selectById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 根据ID查询流程详情（VO）
     *
     * @param id 流程ID
     * @return 流程视图对象
     */
    @Override
    public FlowVo selectVoById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 根据流程标识查询流程
     *
     * @param flowKey 流程标识
     * @return 流程实体
     */
    @Override
    public Flow selectByFlowKey(String flowKey) {
        return baseMapper.selectOne(
            new LambdaQueryWrapper<Flow>()
                .eq(Flow::getFlowKey, flowKey)
        );
    }

    /**
     * 新增流程（存草稿）
     *
     * @param bo 流程业务对象
     * @return 新流程ID（雪花主键，供前端跳转设计器）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(FlowBo bo) {
        // flowKey 为空时自动生成: flow_ + 时间戳后缀
        if (StringUtils.isBlank(bo.getFlowKey())) {
            bo.setFlowKey(generateFlowKey());
        } else {
            checkFlowKeyUnique(bo.getFlowKey(), null);
        }
        Flow entity = MapstructUtils.convert(bo, Flow.class);
        if (entity.getStatus() == null) {
            entity.setStatus("DRAFT");
        }
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        if (entity.getMaxRetry() == null) {
            entity.setMaxRetry(1);
        }
        baseMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 修改流程（仅 DRAFT/OFFLINE 可编辑）
     *
     * @param bo 流程业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(FlowBo bo) {
        Flow existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("流程不存在");
        }
        if ("PUBLISHED".equals(existing.getStatus())) {
            throw new ServiceException("流程已发布，请先下线再编辑");
        }
        if (StringUtils.isNotBlank(bo.getFlowKey())) {
            checkFlowKeyUnique(bo.getFlowKey(), bo.getId());
        }
        Flow entity = MapstructUtils.convert(bo, Flow.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除流程（逻辑删除，有运行中实例拒绝）
     *
     * @param ids 流程ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            Long running = FlowInstanceMapper.selectCount(
                new LambdaQueryWrapper<FlowInstance>()
                    .eq(FlowInstance::getFlowId, id)
                    .eq(FlowInstance::getStatus, "RUNNING")
            );
            if (running != null && running > 0) {
                throw new ServiceException("流程存在运行中的实例，无法删除");
            }
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 发布流程（DRAFT→PUBLISHED，版本+1，发布时落版本快照）
     * <p>图合法性校验由引擎层 {@code FlowGraph.validate()} 完成，P1 接入；
     * 发布成功即写入 ai_flow_version 快照（版本历史/恢复用）</p>
     *
     * @param id 流程ID
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long id) {
        Flow flow = baseMapper.selectById(id);
        if (flow == null) {
            throw new ServiceException("流程不存在");
        }
        if (!"DRAFT".equals(flow.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态的流程可发布");
        }
        int newVersion = flow.getVersion() == null ? 1 : flow.getVersion() + 1;
        Flow update = new Flow();
        update.setId(id);
        update.setStatus("PUBLISHED");
        update.setVersion(newVersion);
        int rows = baseMapper.updateById(update);

        // 发布时落版本快照（版本历史/恢复/只读预览数据源）
        com.nexus.common.flow.domain.entity.FlowVersion version = new com.nexus.common.flow.domain.entity.FlowVersion();
        version.setFlowId(id);
        version.setFlowVersion(newVersion);
        version.setFlowName(flow.getFlowName());
        version.setConfigJson(flow.getConfigJson());
        version.setDescription("发布 v" + newVersion);
        FlowVersionMapper.insert(version);
        return rows;
    }

    /**
     * 下线流程（PUBLISHED→OFFLINE）
     *
     * @param id 流程ID
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int offline(Long id) {
        Flow flow = baseMapper.selectById(id);
        if (flow == null) {
            throw new ServiceException("流程不存在");
        }
        if (!"PUBLISHED".equals(flow.getStatus())) {
            throw new ServiceException("仅 PUBLISHED 状态的流程可下线");
        }
        Flow update = new Flow();
        update.setId(id);
        update.setStatus("OFFLINE");
        return baseMapper.updateById(update);
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<Flow> buildQueryWrapper(FlowBo bo) {
        LambdaQueryWrapper<Flow> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getFlowName()), Flow::getFlowName, bo.getFlowName());
        lqw.like(StringUtils.isNotBlank(bo.getFlowKey()), Flow::getFlowKey, bo.getFlowKey());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), Flow::getStatus, bo.getStatus());
        lqw.orderByDesc(Flow::getUpdateTime);
        return lqw;
    }

    /**
     * 校验 flowKey 唯一性
     *
     * @param flowKey 流程标识
     * @param excludeId 排除的流程ID（更新时）
     */
    private void checkFlowKeyUnique(String flowKey, Long excludeId) {
        Long count = baseMapper.selectCount(
            new LambdaQueryWrapper<Flow>()
                .eq(Flow::getFlowKey, flowKey)
                .ne(excludeId != null, Flow::getId, excludeId)
        );
        if (count != null && count > 0) {
            throw new ServiceException("流程标识 {} 已存在", flowKey);
        }
    }

    /**
     * 自动生成流程标识
     *
     * @return 流程标识
     */
    private String generateFlowKey() {
        return "flow_" + System.currentTimeMillis();
    }
}
