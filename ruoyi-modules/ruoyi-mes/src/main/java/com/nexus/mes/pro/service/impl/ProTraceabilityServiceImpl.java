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
 * forward(正向): 组件物料/SN -> 消费该组件的成品工单 + 上行父SN链
 * <p>pro_sn.parent_sn_id 方向: 组件SN 指向成品SN(见 ry-mes.sql 表注释), 故正向追溯须由组件SN上行取成品SN的工单。</p>
 */
@RequiredArgsConstructor
@DS("mes")
@Service
public class ProTraceabilityServiceImpl implements IProTraceabilityService {

    /** 父SN链上行最大深度(限深防环), 与 ProSnServiceImpl.trace 口径一致 */
    private static final int MAX_PARENT_DEPTH = 10;

    private final ProWorkOrderMapper workOrderMapper;
    private final ProWorkOrderBomLineMapper woBomLineMapper;
    private final ProSnMapper snMapper;

    @Override
    /**
     * backward 操作
     * @param mesWoId MES工单ID
     * @param snCode SNCode
     * @return 返回结果
     */
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
    /**
     * forward 操作
     * @param componentMaterialId ID
     * @param componentSnCode 编码
     * @return 结果列表
     */
    public List<TraceabilityResult> forward(Long componentMaterialId, String componentSnCode) {
        List<Long> woIds;
        // 组件SN: 其自身 mesWoId 是"组件所在工单", 不是"消费该组件的成品工单";
        // 必须沿 parent_sn_id 上行到成品SN, 取成品SN的 mesWoId (MES-PRO-06)
        ProSn componentSn = null;
        ProSn finishedSn = null;
        if (componentSnCode != null) {
            componentSn = snMapper.selectOne(Wrappers.lambdaQuery(ProSn.class)
                .eq(ProSn::getSnCode, componentSnCode));
            if (componentSn == null) {
                return Collections.emptyList();
            }
            finishedSn = resolveTopLevelSn(componentSn);
            if (finishedSn == null || finishedSn.getMesWoId() == null) {
                return Collections.emptyList();
            }
            woIds = List.of(finishedSn.getMesWoId());
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
                // 上行链: 组件SN -> 父SN -> ... -> 成品SN(parent_sn_id 为空), 限深防环
                result.setParentSnChain(buildParentChain(componentSn));
                // assembledComponentSns 由 backward 按 parent_sn_id 装配方向填充(成品SN的下行组件件), 保持不动
                results.add(result);
            }
        }
        return results;
    }

    /**
     * 沿 parent_sn_id 上行解析链顶SN(成品SN, MES-PRO-06)
     * <p>组件SN未挂父SN时其自身即链顶(顶层SN, 直接以自身工单归属)。限深防环, 与
     * {@code ProSnServiceImpl.trace} 的 MAX_PARENT_DEPTH 口径一致。</p>
     */
    private ProSn resolveTopLevelSn(ProSn sn) {
        ProSn current = sn;
        int depth = 0;
        while (current.getParentSnId() != null && depth < MAX_PARENT_DEPTH) {
            ProSn parent = snMapper.selectById(current.getParentSnId());
            if (parent == null) {
                break;
            }
            current = parent;
            depth++;
        }
        return current;
    }

    /**
     * 构建上行父SN链(不含起始组件SN本身), 末位为成品SN
     */
    private List<TraceabilityResult.SnInfo> buildParentChain(ProSn sn) {
        List<TraceabilityResult.SnInfo> chain = new ArrayList<>();
        if (sn == null) {
            return chain;
        }
        Long currentParentId = sn.getParentSnId();
        int depth = 0;
        while (currentParentId != null && depth < MAX_PARENT_DEPTH) {
            ProSn parent = snMapper.selectById(currentParentId);
            if (parent == null) {
                break;
            }
            chain.add(toSnInfo(parent));
            currentParentId = parent.getParentSnId();
            depth++;
        }
        return chain;
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
