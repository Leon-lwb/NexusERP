package com.nexus.common.flow.service;

import com.nexus.common.flow.domain.entity.FlowVersion;
import com.nexus.common.flow.domain.vo.FlowVersionVo;

import java.util.List;

/**
 * AI 流程版本快照 Service 接口
 *
 * @author NexusERP
 */
public interface IFlowVersionService {

    /**
     * 新增版本快照（发布时调用，写入发布后的版本快照）
     *
     * @param version 版本快照
     * @return 影响行数
     */
    int insert(FlowVersion version);

    /**
     * 查询流程的全部版本快照（按版本号降序）
     *
     * @param flowId 流程ID
     * @return 版本快照列表（不含 configJson，列表接口体积控制）
     */
    List<FlowVersionVo> selectListByFlowId(Long flowId);

    /**
     * 查询版本快照详情（含 configJson，供只读预览）
     *
     * @param id 版本快照ID
     * @return 版本快照视图对象
     */
    FlowVersionVo selectVoById(Long id);

    /**
     * 恢复版本：将快照 config_json 写回流程定义，状态置 DRAFT
     * <p>对齐 n8n 版本恢复语义：恢复后需重新发布生效（不直接改线上流程）。
     * 恢复流程原状态被丢弃——由调用方先确认。</p>
     *
     * @param versionId 版本快照ID
     * @return 恢复后的流程ID
     */
    Long restore(Long versionId);
}
