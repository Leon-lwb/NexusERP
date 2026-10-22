package com.nexus.common.flow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowVersion;
import com.nexus.common.flow.domain.vo.FlowVersionVo;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.mapper.FlowVersionMapper;
import com.nexus.common.flow.service.IFlowVersionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 流程版本快照 Service 业务层处理
 *
 * <p>发布落快照由 {@link FlowServiceImpl#publish} 调用； 恢复版本 = 快照 config_json 写回流程定义 + 状态置 DRAFT（需重新发布生效）
 *
 * @author NexusERP
 */
@RequiredArgsConstructor
@Service
public class FlowVersionServiceImpl implements IFlowVersionService {

    private final FlowVersionMapper baseMapper;
    private final FlowMapper FlowMapper;

    /**
     * 新增版本快照（发布时调用，写入发布后的版本快照）
     *
     * @param version 版本快照
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(FlowVersion version) {
        return baseMapper.insert(version);
    }

    /**
     * 查询流程的全部版本快照（按版本号降序）
     *
     * @param flowId 流程ID
     * @return 版本快照列表（不含 configJson，列表接口体积控制）
     */
    @Override
    public List<FlowVersionVo> selectListByFlowId(Long flowId) {
        List<FlowVersionVo> list = baseMapper.selectVoList(
            Wrappers.<FlowVersion>lambdaQuery()
                .eq(FlowVersion::getFlowId, flowId)
                .orderByDesc(FlowVersion::getFlowVersion)
        );
        // 列表不返回配置快照（体积大，仅详情/恢复接口用）
        list.forEach(vo -> vo.setConfigJson(null));
        return list;
    }

    /**
     * 查询版本快照详情（含 configJson，供只读预览）
     *
     * @param id 版本快照ID
     * @return 版本快照视图对象
     */
    @Override
    public FlowVersionVo selectVoById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 恢复版本：将快照 config_json 写回流程定义，状态置 DRAFT
     * <p>对齐 n8n 版本恢复语义：恢复后需重新发布生效（不直接改线上流程）。
     * 恢复流程原状态被丢弃——由调用方先确认。</p>
     *
     * @param versionId 版本快照ID
     * @return 恢复后的流程ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long restore(Long versionId) {
        FlowVersion version = baseMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException("版本快照不存在");
        }
        Flow flow = FlowMapper.selectById(version.getFlowId());
        if (flow == null) {
            throw new ServiceException("流程不存在");
        }
        Flow update = new Flow();
        update.setId(flow.getId());
        update.setConfigJson(version.getConfigJson());
        // 状态置 DRAFT：恢复后需重新发布生效（n8n 同语义，避免线上流程被静默替换）
        update.setStatus("DRAFT");
        FlowMapper.updateById(update);
        return flow.getId();
    }
}
