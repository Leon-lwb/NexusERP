package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.mes.trace.*;
import com.nexus.common.trace.TraceHashes;
import org.springframework.transaction.annotation.Propagation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.bo.ProSnBo;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.IProSnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES SN序列号 Service 业务层实现
 * <p>
 * 状态机: IN_PRODUCTION -> COMPLETED -> SHIPPED
 *                          └-> SCRAPPED (终态)
 *          COMPLETED -> SCRAPPED
 * 追溯: 前向(SN->流转卡工序链->操作员 + 子SN) + 后向(沿 parent_sn_id 父SN链)
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proSnService")
public class ProSnServiceImpl implements IProSnService {

    /**
     * 后向追溯最大递归深度(防环)
     */
    private static final int MAX_PARENT_DEPTH = 10;

    private final ProSnMapper baseMapper;
    private final ProFlowCardStepMapper stepMapper;
    private final MesSerialOrchestrator serialOrchestrator;
    private final MesSnLifecycleWriter lifecycle;
    private final MesMaterialTraceWriter componentTrace;
    private final MesSerialReferenceService serialReferences;

    @Override
    /**
     * 查询SN列表(不分页, 导出用)
     * @param bo bo
     * @return 列表
     */
    public List<ProSnVo> selectList(ProSnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    /**
     * 查询
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<ProSnVo> selectPageList(ProSnBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProSn> lqw = buildQueryWrapper(bo);
        Page<ProSnVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param snId SNID
     * @return 返回视图对象
     */
    public ProSnVo selectById(Long snId) {
        return baseMapper.selectVoById(snId);
    }

    @Override
    /**
     * 查询
     * @param snCode SNCode
     * @return 返回视图对象
     */
    public ProSnVo selectByCode(String snCode) {
        var reference = serialReferences.resolve(snCode);
        if (reference == null) {
            return null;
        }
        var view = baseMapper.selectVoById(reference.localSnId());
        if (view != null) {
            view.setLocalSnCode(reference.localSnCode());
            view.setCanonicalSnCode(reference.canonicalSnCode());
            view.setSnCode(reference.canonicalSnCode());
        }
        return view;
    }

    /**
     * 从流转卡批量生成SN
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public List<ProSnVo> generateSnForCard(
            Long cardId, Integer quantity, String initialStatus, Long lotId) {
        return generateSnForCard("MES:CARD:" + cardId, cardId, quantity, initialStatus, lotId);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public List<ProSnVo> generateSnForCard(
            String commandId, Long cardId, Integer quantity, String initialStatus, Long lotId) {
        return createdViews(
                serialOrchestrator.forCard(commandId, cardId, quantity, initialStatus, lotId));
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public List<ProSnVo> generateSnForSubcontract(
            Long id,
            String no,
            Long materialId,
            String materialCode,
            String materialName,
            int quantity) {
        return generateSnForSubcontract(
                "MES:SUBCONTRACT:" + id, id, no, materialId, materialCode, materialName, quantity);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public List<ProSnVo> generateSnForSubcontract(
            String commandId,
            Long id,
            String no,
            Long materialId,
            String materialCode,
            String materialName,
            int quantity) {
        return createdViews(
                serialOrchestrator.forSubcontract(commandId, id, no, materialId, quantity));
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ProSnVo createSn(
            Long wo, Long card, Long material, String code, Long parent, String remark, Long lot) {
        return createSn(
                "MES:MANUAL:" + TraceHashes.fingerprint(wo, card, material, code, parent),
                wo,
                card,
                material,
                code,
                parent,
                remark,
                lot);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ProSnVo createSn(
            String commandId,
            Long wo,
            Long card,
            Long material,
            String code,
            Long parent,
            String remark,
            Long lot) {
        return createdViews(
                        serialOrchestrator.manual(
                                commandId, wo, card, material, code, parent, remark, lot))
                .get(0);
    }

    private List<ProSnVo> createdViews(List<Long> ids) {
        return baseMapper.selectVoList(
                Wrappers.<ProSn>lambdaQuery().in(ProSn::getSnId, ids).orderByAsc(ProSn::getSnCode));
    }

    /**
     * 完工(IN_PRODUCTION -> COMPLETED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeSn(Long snId) {
        lifecycle.complete(snId);
    }

    /**
     * 报废(IN_PRODUCTION/COMPLETED -> SCRAPPED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scrapSn(Long snId, String remark) {
        lifecycle.scrap(snId, remark);
    }

    /**
     * 发货(COMPLETED -> SHIPPED)
     */
    @Override
    public void shipSn(Long snId) {
        throw new com.nexus.common.core.exception.ServiceException("发货状态只由ERP已过账发货事件投影，不能手动标记");
    }

    /**
     * 追溯: 前向(SN->工序->操作员 + 子SN) + 后向(父SN链)
     */
    @Override
    public Map<String, Object> trace(Long snId) {
        Map<String, Object> result = new HashMap<>();
        ProSnVo sn = baseMapper.selectVoById(snId);
        if (sn == null) {
            throw new RuntimeException("SN不存在: " + snId);
        }
        result.put("sn", sn);
        result.put("componentConsumptions", componentTrace.entriesForSn(snId));

        // 前向: 流转卡工序链(若SN关联了流转卡)
        List<Map<String, Object>> forwardSteps = new ArrayList<>();
        if (sn.getCardId() != null) {
            List<ProFlowCardStepVo> steps =
                    stepMapper.selectVoList(
                            Wrappers.<com.nexus.mes.pro.domain.ProFlowCardStep>lambdaQuery()
                                    .eq(
                                            com.nexus.mes.pro.domain.ProFlowCardStep::getCardId,
                                            sn.getCardId())
                                    .orderByAsc(
                                            com.nexus.mes.pro.domain.ProFlowCardStep
                                                    ::getSequenceNum));
            for (ProFlowCardStepVo step : steps) {
                Map<String, Object> m = new HashMap<>();
                m.put("stepRecordId", step.getStepRecordId());
                m.put("sequenceNum", step.getSequenceNum());
                m.put("processCode", step.getProcessCode());
                m.put("processName", step.getProcessName());
                m.put("workcenterCode", step.getWorkcenterCode());
                m.put("stepStatus", step.getStepStatus());
                m.put("operatorId", step.getOperatorId());
                m.put("operatorName", step.getOperatorName());
                m.put("goodQty", step.getGoodQty());
                m.put("defectQty", step.getDefectQty());
                m.put("scrapQty", step.getScrapQty());
                m.put("actualStartTime", step.getActualStartTime());
                m.put("actualEndTime", step.getActualEndTime());
                forwardSteps.add(m);
            }
        }
        result.put("forwardSteps", forwardSteps);

        // 前向: 子SN列表(当前SN作为父SN的下级组装件)
        List<ProSnVo> children =
                baseMapper.selectVoList(
                        Wrappers.<ProSn>lambdaQuery()
                                .eq(ProSn::getParentSnId, snId)
                                .orderByAsc(ProSn::getSnCode));
        result.put("children", children);

        // 后向: 沿 parent_sn_id 向上递归父SN链(限深防环)
        List<ProSnVo> parentChain = new ArrayList<>();
        Long currentParentId = sn.getParentSnId();
        int depth = 0;
        while (currentParentId != null && depth < MAX_PARENT_DEPTH) {
            ProSnVo parent = baseMapper.selectVoById(currentParentId);
            if (parent == null) {
                break;
            }
            parentChain.add(parent);
            currentParentId = parent.getParentSnId();
            depth++;
        }
        result.put("parentChain", parentChain);

        return result;
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<ProSn> buildQueryWrapper(ProSnBo bo) {
        return Wrappers.<ProSn>lambdaQuery()
                .eq(StringUtils.isNotBlank(bo.getSnCode()), ProSn::getSnCode, bo.getSnCode())
                .eq(StringUtils.isNotBlank(bo.getSnStatus()), ProSn::getSnStatus, bo.getSnStatus())
                .eq(StringUtils.isNotBlank(bo.getSource()), ProSn::getSource, bo.getSource())
                .eq(
                        StringUtils.isNotBlank(bo.getSubcontractNo()),
                        ProSn::getSubcontractNo,
                        bo.getSubcontractNo())
                .eq(bo.getMesWoId() != null, ProSn::getMesWoId, bo.getMesWoId())
                .eq(bo.getCardId() != null, ProSn::getCardId, bo.getCardId())
                .eq(bo.getMaterialId() != null, ProSn::getMaterialId, bo.getMaterialId())
                .eq(bo.getParentSnId() != null, ProSn::getParentSnId, bo.getParentSnId())
                .orderByDesc(ProSn::getCreateTime);
    }
}
