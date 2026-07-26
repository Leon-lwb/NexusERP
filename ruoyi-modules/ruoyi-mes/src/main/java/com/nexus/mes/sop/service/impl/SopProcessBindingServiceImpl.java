package com.nexus.mes.sop.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.sop.domain.SopProcessBinding;
import com.nexus.mes.sop.domain.bo.SopProcessBindingBo;
import com.nexus.mes.sop.domain.vo.SopDocumentVo;
import com.nexus.mes.sop.domain.vo.SopProcessBindingVo;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopProcessBindingMapper;
import com.nexus.mes.sop.service.ISopDocumentService;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工序SOP绑定 Service 业务层实现
 * <p>绑定到 bd_process.process_id (工序主数据, 跨工艺路线复用)。</p>
 * <p>is_default=1 时同工序其他绑定排他置0 (CAS)。</p>
 * <p>契约方法: getSopForProcess / getSopForStep / getSopForMaterial (供 P0-3 / 工位终端)</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("sopProcessBindingService")
public class SopProcessBindingServiceImpl implements ISopProcessBindingService {

    private final SopProcessBindingMapper baseMapper;
    private final SopDocumentMapper sopDocumentMapper;
    private final ProFlowCardStepMapper proFlowCardStepMapper;
    private final IMesErpBridgeService mesErpBridgeService;

    @Override
    public TableDataInfo<SopProcessBindingVo> selectPageList(SopProcessBindingBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<com.nexus.mes.sop.domain.SopProcessBinding> lqw = buildQueryWrapper(bo);
        Page<SopProcessBindingVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        page.getRecords().forEach(this::fillSopInfo);
        return TableDataInfo.build(page);
    }

    @Override
    public SopProcessBindingVo selectById(Long bindingId) {
        SopProcessBindingVo vo = baseMapper.selectVoById(bindingId);
        if (vo != null) {
            fillSopInfo(vo);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(SopProcessBindingBo bo) {
        com.nexus.mes.sop.domain.SopProcessBinding b = new com.nexus.mes.sop.domain.SopProcessBinding();
        b.setProcessId(bo.getProcessId());
        b.setProcessCode(bo.getProcessCode());
        b.setProcessName(bo.getProcessName());
        b.setProcessCategory(bo.getProcessCategory());
        b.setSopId(bo.getSopId());
        b.setVersionNo(bo.getVersionNo());
        b.setIsDefault(bo.getIsDefault() == null ? "0" : bo.getIsDefault());
        b.setSortOrder(bo.getSortOrder() == null ? 0 : bo.getSortOrder());
        b.setRemark(bo.getRemark());
        // is_default=1 时同工序其他绑定排他置0 (CAS)
        if ("1".equals(b.getIsDefault())) {
            baseMapper.update(null, Wrappers.<com.nexus.mes.sop.domain.SopProcessBinding>lambdaUpdate()
                .set(com.nexus.mes.sop.domain.SopProcessBinding::getIsDefault, "0")
                .eq(com.nexus.mes.sop.domain.SopProcessBinding::getProcessId, bo.getProcessId())
                .eq(com.nexus.mes.sop.domain.SopProcessBinding::getIsDefault, "1"));
        }
        int rows = baseMapper.insert(b);
        log.info("新增工序SOP绑定: processId={}, sopId={}, isDefault={}",
            bo.getProcessId(), bo.getSopId(), b.getIsDefault());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除工序SOP绑定: count={}", ids.length);
        return rows > 0;
    }

    @Override
    public List<SopProcessBindingVo> selectByProcess(Long processId) {
        List<SopProcessBindingVo> list = baseMapper.selectVoList(
            Wrappers.<com.nexus.mes.sop.domain.SopProcessBinding>lambdaQuery()
                .eq(com.nexus.mes.sop.domain.SopProcessBinding::getProcessId, processId)
                .orderByDesc(com.nexus.mes.sop.domain.SopProcessBinding::getIsDefault)
                .orderByAsc(com.nexus.mes.sop.domain.SopProcessBinding::getSortOrder));
        list.forEach(this::fillSopInfo);
        return list;
    }

    /**
     * 契约: 获取工序的默认SOP(is_default=1 优先, 否则 sort_order 首条)
     */
    @Override
    public SopDocumentVo getSopForProcess(Long processId) {
        List<SopProcessBindingVo> bindings = selectByProcess(processId);
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        // is_default=1 优先 (selectByProcess 已按 is_default desc 排序, 首条即默认)
        Long sopId = bindings.get(0).getSopId();
        SopDocumentVo vo = sopDocumentMapper.selectVoById(sopId);
        // 仅返回已发布的 SOP (DRAFT/ARCHIVED 不推送)
        if (vo != null && !ISopDocumentService.STATUS_PUBLISHED.equals(vo.getStatus())) {
            return null;
        }
        return vo;
    }

    /**
     * 契约: 按流转卡工序记录查SOP(经 ProFlowCardStep.processId, MES库内不跨库)
     */
    @Override
    public SopDocumentVo getSopForStep(Long stepRecordId) {
        ProFlowCardStepVo step = proFlowCardStepMapper.selectVoById(stepRecordId);
        if (step == null || step.getProcessId() == null) {
            return null;
        }
        return getSopForProcess(step.getProcessId());
    }

    /**
     * 契约: 按物料查全部工序SOP(工单开工预加载)
     * <p>物料 -> ERP 当前工艺路线 -> 路线步骤 -> 各 processId -> 绑定SOP。
     * 仅含已绑定SOP的工序。防御: ERP 查询异常时返回空 Map, 不阻断主流程。</p>
     */
    @Override
    public Map<Long, SopDocumentVo> getSopForMaterial(Long materialId) {
        Map<Long, SopDocumentVo> result = new LinkedHashMap<>();
        if (materialId == null) {
            return result;
        }
        try {
            Long routingId = mesErpBridgeService.getDefaultRoutingId(materialId);
            if (routingId == null) {
                log.warn("物料 {} 无默认工艺路线, 跳过 SOP 预加载", materialId);
                return result;
            }
            List<Map<String, Object>> steps = mesErpBridgeService.getRoutingSteps(routingId);
            if (steps == null || steps.isEmpty()) {
                return result;
            }
            for (Map<String, Object> step : steps) {
                Object pidObj = step.get("process_id");
                if (pidObj == null) {
                    continue;
                }
                Long processId = toLong(pidObj);
                if (processId == null) {
                    continue;
                }
                SopDocumentVo sop = getSopForProcess(processId);
                if (sop != null) {
                    result.put(processId, sop);
                }
            }
        } catch (Exception e) {
            log.error("按物料预加载SOP失败 materialId={}", materialId, e);
        }
        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 填充 SOP 编码/名称/状态 (联表查询, 列表 N+1 可接受 - 绑定记录通常较少)
     */
    private void fillSopInfo(SopProcessBindingVo vo) {
        if (vo == null || vo.getSopId() == null) {
            return;
        }
        SopDocumentVo sop = sopDocumentMapper.selectVoById(vo.getSopId());
        if (sop != null) {
            vo.setSopCode(sop.getSopCode());
            vo.setSopName(sop.getSopName());
            vo.setSopStatus(sop.getStatus());
        }
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LambdaQueryWrapper<com.nexus.mes.sop.domain.SopProcessBinding> buildQueryWrapper(SopProcessBindingBo bo) {
        return Wrappers.<com.nexus.mes.sop.domain.SopProcessBinding>lambdaQuery()
            .eq(bo.getProcessId() != null, com.nexus.mes.sop.domain.SopProcessBinding::getProcessId, bo.getProcessId())
            .eq(bo.getSopId() != null, com.nexus.mes.sop.domain.SopProcessBinding::getSopId, bo.getSopId())
            .like(StringUtils.isNotBlank(bo.getProcessName()), com.nexus.mes.sop.domain.SopProcessBinding::getProcessName, bo.getProcessName())
            .eq(StringUtils.isNotBlank(bo.getIsDefault()), com.nexus.mes.sop.domain.SopProcessBinding::getIsDefault, bo.getIsDefault())
            .orderByDesc(com.nexus.mes.sop.domain.SopProcessBinding::getIsDefault)
            .orderByAsc(com.nexus.mes.sop.domain.SopProcessBinding::getSortOrder);
    }
}
