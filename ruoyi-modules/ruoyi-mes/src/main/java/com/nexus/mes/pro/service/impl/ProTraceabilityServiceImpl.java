package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.vo.ProWorkOrderBomLineVo;
import com.nexus.mes.pro.domain.vo.TraceabilityResult;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProTraceabilityService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 组件追溯服务实现
 * <p>
 * backward(反向): 成品工单/SN -> 所需组件清单(MBOM快照) + 实际装配的组件SN
 * forward(正向): 组件物料/SN -> 消费该组件的成品工单 + 父SN链
 */
@RequiredArgsConstructor
@DS("mes")
@Service
public class ProTraceabilityServiceImpl implements IProTraceabilityService {

    private final ProWorkOrderMapper workOrderMapper;
    private final ProWorkOrderBomLineMapper woBomLineMapper;
    private final ProSnMapper snMapper;

    @Override
    public TraceabilityResult backward(Long mesWoId, String snCode) {
        // 读取工单
        ProWorkOrder wo = workOrderMapper.selectById(mesWoId);
        if (wo == null) {
            return null;
        }
        TraceabilityResult result = new TraceabilityResult();
        result.setMesWoId(wo.getMesWoId());
        result.setWoNo(wo.getWoNo());
        result.setMaterialId(wo.getMaterialId());
        result.setMaterialCode(wo.getMaterialCode());
        result.setMaterialName(wo.getMaterialName());
        result.setWoStatus(wo.getWoStatus());

        // 所需组件清单(MBOM快照)
        List<ProWorkOrderBomLineVo> bomLines = woBomLineMapper.selectVoList(
            Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
                .eq(ProWorkOrderBomLine::getMesWoId, mesWoId)
                .orderByAsc(ProWorkOrderBomLine::getSortOrder));
        result.setRequiredComponents(bomLines != null ? bomLines : Collections.emptyList());

        // 实际装配的组件SN: 找到该工单下、parent_sn_id关联到成品SN的组件SN
        if (snCode != null) {
            // 先找到成品SN
            ProSn finishedSn = snMapper.selectOne(Wrappers.lambdaQuery(ProSn.class)
                .eq(ProSn::getSnCode, snCode)
                .eq(ProSn::getMesWoId, mesWoId));
            if (finishedSn != null) {
                // 查找parent_sn_id指向成品SN的组件SN
                List<ProSn> componentSns = snMapper.selectList(Wrappers.lambdaQuery(ProSn.class)
                    .eq(ProSn::getParentSnId, finishedSn.getSnId()));
                result.setAssembledComponentSns(componentSns == null ? Collections.emptyList() :
                    componentSns.stream().map(this::toSnInfo).collect(Collectors.toList()));
            } else {
                result.setAssembledComponentSns(Collections.emptyList());
            }
        } else {
            // 无SN时查该工单所有组件SN(parent_sn_id不为空的SN)
            List<ProSn> componentSns = snMapper.selectList(Wrappers.lambdaQuery(ProSn.class)
                .eq(ProSn::getMesWoId, mesWoId)
                .isNotNull(ProSn::getParentSnId));
            result.setAssembledComponentSns(componentSns == null ? Collections.emptyList() :
                componentSns.stream().map(this::toSnInfo).collect(Collectors.toList()));
        }
        return result;
    }

    @Override
    public List<TraceabilityResult> forward(Long componentMaterialId, String componentSnCode) {
        List<Long> woIds;
        if (componentSnCode != null) {
            // 通过组件SN找工单
            ProSn componentSn = snMapper.selectOne(Wrappers.lambdaQuery(ProSn.class)
                .eq(ProSn::getSnCode, componentSnCode));
            if (componentSn == null || componentSn.getMesWoId() == null) {
                return Collections.emptyList();
            }
            woIds = List.of(componentSn.getMesWoId());
        } else if (componentMaterialId != null) {
            // 通过MBOM快照child_material_id查找消费该组件的工单
            List<ProWorkOrderBomLine> matchingLines = woBomLineMapper.selectList(
                Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
                    .eq(ProWorkOrderBomLine::getChildMaterialId, componentMaterialId)
                    .select(ProWorkOrderBomLine::getMesWoId)
                    .groupBy(ProWorkOrderBomLine::getMesWoId));
            woIds = matchingLines == null ? Collections.emptyList() :
                matchingLines.stream().map(ProWorkOrderBomLine::getMesWoId).distinct().collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }

        List<TraceabilityResult> results = new ArrayList<>();
        for (Long woId : woIds) {
            TraceabilityResult result = backward(woId, null);
            if (result != null) {
                // 附加父SN链: 找到该工单成品SN的parent_sn_id链
                List<ProSn> finishedSns = snMapper.selectList(Wrappers.lambdaQuery(ProSn.class)
                    .eq(ProSn::getMesWoId, woId)
                    .isNull(ProSn::getParentSnId));
                List<TraceabilityResult.SnInfo> parentChain = new ArrayList<>();
                if (finishedSns != null) {
                    for (ProSn sn : finishedSns) {
                        parentChain.add(toSnInfo(sn));
                    }
                }
                result.setParentSnChain(parentChain);
                results.add(result);
            }
        }
        return results;
    }

    private TraceabilityResult.SnInfo toSnInfo(ProSn sn) {
        TraceabilityResult.SnInfo info = new TraceabilityResult.SnInfo();
        info.setSnId(sn.getSnId());
        info.setSnCode(sn.getSnCode());
        info.setMesWoId(sn.getMesWoId());
        info.setMaterialId(sn.getMaterialId());
        info.setMaterialCode(sn.getMaterialCode());
        info.setMaterialName(sn.getMaterialName());
        info.setSnStatus(sn.getSnStatus());
        info.setParentSnId(sn.getParentSnId());
        info.setParentSnCode(sn.getParentSnCode());
        return info;
    }
}
