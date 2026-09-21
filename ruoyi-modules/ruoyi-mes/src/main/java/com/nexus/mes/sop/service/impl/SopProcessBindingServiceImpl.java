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

    /**
     * 分页查询绑定列表(含 SOP 编码/名称/状态)
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<SopProcessBindingVo> selectPageList(SopProcessBindingBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<com.nexus.mes.sop.domain.SopProcessBinding> lqw = buildQueryWrapper(bo);
        Page<SopProcessBindingVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        page.getRecords().forEach(this::fillSopInfo);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取绑定详情(含 SOP 信息)
     *
     * @param bindingId 绑定ID
     * @return 绑定详情
     */
    @Override
    public SopProcessBindingVo selectById(Long bindingId) {
        SopProcessBindingVo vo = baseMapper.selectVoById(bindingId);
        if (vo != null) {
            fillSopInfo(vo);
        }
        return vo;
    }

    /**
     * 新增绑定(is_default=1 时同工序排他置0; CAS 更新)
     * <p>前端选择器带入 process_code/name/category 冗余字段。
     * is_default 排他: 同为1时其他绑定 CAS 置0。</p>
     *
     * @param bo 绑定业务对象
     * @return 新增成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(SopProcessBindingBo bo) {
        // MES-SOP-06: 绑定前必须校验 SOP 状态 —— 此前只落 sopId, 可把 DRAFT/ARCHIVED 绑到工序,
        // 绑定列表"明明绑了"而工位 getSopForProcess 因非 PUBLISHED 返回 null → 作业指导书静默不可用
        SopDocumentVo sop = sopDocumentMapper.selectVoById(bo.getSopId());
        if (sop == null) {
            throw new RuntimeException("SOP不存在: " + bo.getSopId());
        }
        if (!ISopDocumentService.STATUS_PUBLISHED.equals(sop.getStatus())) {
            throw new RuntimeException("仅已发布(PUBLISHED)的SOP可绑定工序,当前状态: " + sop.getStatus()
                + "(草稿/审批中/已归档的SOP绑定后工位侧取不到作业指导书)");
        }
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

    /**
     * 批量删除绑定
     *
     * @param ids 绑定ID数组
     * @return 删除成功返回 true
     */
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

    /**
     * 契约: 查询工序绑定的SOP列表(is_default 优先 + sort_order 升序)
     *
     * @param processId 工序ID(bd_process)
     * @return 绑定列表(含SOP信息)
     */
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
     * 契约: 推送当前工序SOP到工位终端(P0-3 进站通过后调用)
     * <p>SSE 推送 SOP 到指定用户;推送失败不抛异常,无绑定SOP返回 null。</p>
     */
    @Override
    public SopDocumentVo pushSopToWorkstation(Long stepRecordId, Long userId) {
        SopDocumentVo sop = getSopForStep(stepRecordId);
        if (sop == null) {
            return null;
        }
        if (com.nexus.common.sse.utils.SseMessageUtils.isEnable()) {
            try {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("type", "SOP");
                payload.put("action", "PUSH");
                payload.put("data", sop);
                com.nexus.common.sse.dto.SseMessageDto dto = new com.nexus.common.sse.dto.SseMessageDto();
                dto.setUserIds(java.util.Collections.singletonList(userId));
                dto.setMessage(com.nexus.common.json.utils.JsonUtils.toJsonString(payload));
                com.nexus.common.sse.utils.SseMessageUtils.publishMessage(dto);
            } catch (Exception e) {
                log.warn("SOP SSE推送失败(不阻断,可主动拉取): stepRecordId={}, userId={}", stepRecordId, userId, e);
            }
        }
        return sop;
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

    /**
     * 安全类型转换: Object -> Long (支持 Number 和 String)
     */
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

    /**
     * 构建查询条件: processId/sopId 精确, processName 模糊, isDefault 精确; 按 is_default 倒序 + sort_order 升序
     */
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
