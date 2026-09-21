package com.nexus.mes.flash.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.flash.domain.FlashRecord;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.domain.MesContentFile;
import com.nexus.mes.flash.domain.MesFlashType;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.domain.vo.FlashRecordVo;
import com.nexus.mes.flash.mapper.FlashRecordMapper;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.flash.mapper.MesContentFileMapper;
import com.nexus.mes.flash.mapper.MesFlashTypeMapper;
import com.nexus.mes.flash.provider.FlashContent;
import com.nexus.mes.flash.provider.FlashContext;
import com.nexus.mes.flash.provider.FlashTypeProvider;
import com.nexus.mes.flash.provider.FlashTypeRegistry;
import com.nexus.mes.flash.provider.FlashVerifyResult;
import com.nexus.mes.flash.service.IFlashRecordService;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES固件刷写记录 Service 业务层实现
 * <p>
 * M2 刷写作业: 管理员补录(insertByBo)宽松 + 工位作业确认(confirmFlash)严格 + AUTO 设备上报(reportFlashBatch);
 * 记录号走 MES_FLASH_NO 自动编码(FL+yyyyMMdd+日流水), 冗余字段按主数据回填;
 * 同一 SN 允许多条(FAIL 重刷保留历史, 判定取最新一条)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service(IFlashRecordService.SERVICE_NAME)
public class FlashRecordServiceImpl implements IFlashRecordService {

    private final FlashRecordMapper baseMapper;
    private final ProSnMapper proSnMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final FlashTypeRegistry flashTypeRegistry;
    private final MesFlashTypeMapper mesFlashTypeMapper;
    private final FirmwareMapper firmwareMapper;
    private final MesContentFileMapper contentFileMapper;
    private final ProWorkOrderStepMapper workOrderStepMapper;
    private final ProFlowCardStepMapper flowCardStepMapper;
    private final ObjectMapper objectMapper;
    private final IMesResourcePoolService resourcePoolService;

    /**
     * MES-FLASH-04: 同一 (SN, 刷写类型, 内容源) 的最大刷写次数(含历史), 达到即拒绝再次刷写。
     * <p>配置项 {@code mes.flash.max-retry}, 默认 3。</p>
     */
    @Value("${mes.flash.max-retry:3}")
    private int maxRetry = 3;

    /** 结果常量 */
    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";

    /** SN状态-报废(禁止刷写) */
    private static final String SN_STATUS_SCRAPPED = "SCRAPPED";
    /** 流转卡工序状态常量(定位 SN 当前工序用) */
    private static final String STEP_COMPLETED = "COMPLETED";
    private static final String STEP_SKIPPED = "SKIPPED";
    /** 刷写模式-自动/手动 */
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_MANUAL = "MANUAL";

    /**
     * 分页查询刷写记录
     */
    @Override
    public TableDataInfo<FlashRecordVo> selectPageList(FlashRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FlashRecord> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getFlashNo()), FlashRecord::getFlashNo, bo.getFlashNo())
            .like(StringUtils.isNotBlank(bo.getSnCode()), FlashRecord::getSnCode, bo.getSnCode())
            .like(StringUtils.isNotBlank(bo.getWoNo()), FlashRecord::getWoNo, bo.getWoNo())
            .like(StringUtils.isNotBlank(bo.getEquipmentCode()), FlashRecord::getEquipmentCode, bo.getEquipmentCode())
            .eq(bo.getContentRefId() != null, FlashRecord::getContentRefId, bo.getContentRefId())
            .eq(StringUtils.isNotBlank(bo.getResult()), FlashRecord::getResult, bo.getResult())
            .eq(StringUtils.isNotBlank(bo.getFlashMode()), FlashRecord::getFlashMode, bo.getFlashMode())
            .eq(bo.getOperatorId() != null, FlashRecord::getOperatorId, bo.getOperatorId())
            .ge(bo.getBeginFlashTime() != null, FlashRecord::getFlashTime, bo.getBeginFlashTime())
            .le(bo.getEndFlashTime() != null, FlashRecord::getFlashTime, bo.getEndFlashTime())
            .orderByDesc(FlashRecord::getFlashTime);
        Page<FlashRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID查询刷写记录详情
     */
    @Override
    public FlashRecordVo selectById(Long flashRecordId) {
        return baseMapper.selectVoById(flashRecordId);
    }

    /**
     * 补录刷写记录(宽松校验, 自动编码 + 冗余回填)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FlashRecordBo bo) {
        FlashRecord record = new FlashRecord();
        record.setFlashNo(autoCodeUtil.genSerialCode("MES_FLASH_NO", null));
        record.setSnId(bo.getSnId());
        record.setSnCode(bo.getSnCode());
        record.setMesWoId(bo.getMesWoId());
        record.setWoNo(bo.getWoNo());
        record.setCardId(bo.getCardId());
        record.setStepRecordId(bo.getStepRecordId());
        record.setProcessId(bo.getProcessId());
        record.setProcessCode(bo.getProcessCode());
        record.setProcessName(bo.getProcessName());
        record.setContentRefId(bo.getContentRefId());
        record.setContentChecksum(bo.getContentChecksum());
        record.setEquipmentId(bo.getEquipmentId());
        record.setResult(bo.getResult());
        record.setFailReason(bo.getFailReason());
        record.setRetryCount(bo.getRetryCount() == null ? 0 : bo.getRetryCount());
        record.setFlashMode(StringUtils.isBlank(bo.getFlashMode()) ? MODE_MANUAL : bo.getFlashMode());
        record.setRemark(bo.getRemark());
        record.setFlashTime(bo.getFlashTime() == null ? new Date() : bo.getFlashTime());
        applyOperator(record, bo);
        enrichRedundant(record);
        baseMapper.insert(record);
        log.info("刷写记录补录: flashNo={}, snCode={}, result={}", record.getFlashNo(), record.getSnCode(), record.getResult());
        return true;
    }

    /**
     * 修改刷写记录(纠错, 记录号与SN不可变)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(FlashRecordBo bo) {
        FlashRecord record = baseMapper.selectById(bo.getFlashRecordId());
        if (record == null) {
            throw new ServiceException("刷写记录不存在: " + bo.getFlashRecordId());
        }
        record.setContentRefId(bo.getContentRefId() == null ? record.getContentRefId() : bo.getContentRefId());
        record.setContentChecksum(bo.getContentChecksum());
        record.setEquipmentId(bo.getEquipmentId());
        record.setResult(bo.getResult());
        record.setFailReason(bo.getFailReason());
        record.setFlashTime(bo.getFlashTime() == null ? record.getFlashTime() : bo.getFlashTime());
        record.setRemark(bo.getRemark());
        // 重刷/纠错场景: 更新对应的固件版本/设备编码/工序冗余快照
        if (bo.getProcessId() != null) {
            record.setProcessId(bo.getProcessId());
            record.setProcessCode(bo.getProcessCode());
            record.setProcessName(bo.getProcessName());
        }
        enrichRedundant(record);
        baseMapper.updateById(record);
        log.info("刷写记录纠错: flashRecordId={}, result={}", bo.getFlashRecordId(), bo.getResult());
        return true;
    }

    /**
     * 批量删除刷写记录(仅 MANUAL 补录记录可删, AUTO 作业记录禁止删除)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] flashRecordIds) {
        List<FlashRecord> records = baseMapper.selectBatchIds(Arrays.asList(flashRecordIds));
        for (FlashRecord record : records) {
            if (MODE_AUTO.equals(record.getFlashMode())) {
                throw new ServiceException("AUTO 刷写作业记录禁止删除, 保留追溯: " + record.getFlashNo());
            }
        }
        baseMapper.deleteByIds(Arrays.asList(flashRecordIds));
        log.info("刷写记录删除(仅补录): ids={}", Arrays.toString(flashRecordIds));
        return true;
    }

    /**
     * 刷写作业确认(页面): 严格校验后落单条记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirmFlash(FlashRecordBo bo) {
        FlashRecord record = buildValidatedRecord(bo);
        baseMapper.insert(record);
        log.info("刷写作业确认: flashNo={}, snCode={}, contentRefId={}, result={}", record.getFlashNo(), record.getSnCode(), record.getContentRefId(), record.getResult());
        return true;
    }

    /**
     * AUTO 设备批量上报刷写结果(宽松模式): 逐条校验落库, 失败项收集原因不中断
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportFlashBatch(List<FlashRecordBo> list) {
        if (list == null || list.isEmpty()) {
            throw new ServiceException("上报数据不能为空");
        }
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> failed = new ArrayList<>();
        int success = 0;
        for (FlashRecordBo bo : list) {
            try {
                FlashRecord record = buildValidatedRecord(bo);
                record.setFlashMode(MODE_AUTO);
                baseMapper.insert(record);
                success++;
                log.info("刷写AUTO上报成功: flashNo={}, snCode={}, result={}", record.getFlashNo(), record.getSnCode(), record.getResult());
            } catch (Exception e) {
                Map<String, String> f = new HashMap<>();
                f.put("snCode", bo.getSnCode());
                f.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                failed.add(f);
                log.warn("刷写AUTO上报失败: snCode={}, reason={}", bo.getSnCode(), f.get("reason"));
            }
        }
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /**
     * SN 远程搜索: 按 SN 编码模糊查询 pro_sn
     */
    @Override
    public List<ProSnVo> listSnOptions(String snCode) {
        LambdaQueryWrapper<ProSn> lqw = Wrappers.<ProSn>lambdaQuery()
            .like(StringUtils.isNotBlank(snCode), ProSn::getSnCode, StringUtils.isNotBlank(snCode) ? snCode.trim() : null)
            .orderByDesc(ProSn::getCreateTime)
            .last("LIMIT 20");
        return proSnMapper.selectVoList(lqw);
    }

    /**
     * 工位编排: 查 SN 工单刷写工序待刷清单
     * <p>snCode → ProSn → ProWorkOrderStep(FLASH/content_ref非空) → 解析 step_flash_config/content_ref 得 bindings
     * → 每 binding 查最新 mes_flash_record 结果, 返回产线工位待刷清单(含已刷结果)。</p>
     */
    @Override
    public List<Map<String, Object>> selectStationPending(String snCode) {
        if (StringUtils.isBlank(snCode)) {
            throw new ServiceException("snCode不能为空");
        }
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, snCode.trim()));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + snCode);
        }
        List<ProWorkOrderStep> steps = workOrderStepMapper.selectList(Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, sn.getMesWoId())
            .and(w -> w.eq(ProWorkOrderStep::getStepType, "FLASH")
                .or().isNotNull(ProWorkOrderStep::getContentRefId)
                .or().isNotNull(ProWorkOrderStep::getStepFlashConfig)));
        List<Map<String, Object>> pending = new ArrayList<>();
        for (ProWorkOrderStep step : steps) {
            for (Map<String, Object> b : filterGateEnabledBindings(parseStationBindings(step))) {
                Long refId = (Long) b.get("contentRefId");
                String typeCode = (String) b.get("flashTypeCode");
                FlashRecord latest = baseMapper.selectOne(Wrappers.<FlashRecord>lambdaQuery()
                    .eq(FlashRecord::getSnCode, snCode.trim())
                    .eq(refId != null, FlashRecord::getContentRefId, refId)
                    .eq(StringUtils.isNotBlank(typeCode), FlashRecord::getFlashTypeCode, typeCode)
                    .orderByDesc(FlashRecord::getFlashTime)
                    .orderByDesc(FlashRecord::getFlashRecordId)
                    .last("LIMIT 1"));
                Map<String, Object> row = new HashMap<>();
                row.put("sequenceNum", step.getSequenceNum());
                row.put("processCode", step.getProcessCode());
                row.put("processName", step.getProcessName());
                row.put("stepType", step.getStepType());
                row.put("flashTypeCode", typeCode);
                row.put("contentRefId", refId);
                row.put("latestResult", latest != null ? latest.getResult() : null);
                row.put("flashTime", latest != null ? latest.getFlashTime() : null);
                pending.add(row);
            }
        }
        return pending;
    }

    /**
     * 待刷写任务清单(登录态, 刷写作业页用): 按工单/流转卡聚合在制SN × 工单刷写工序绑定
     * <p>每行: snId/snCode/woNo/cardId/sequenceNum/processName/flashTypeCode/contentRefId/
     * contentVersion/contentChecksum/latestResult/flashTime/pendingFlag。
     * pendingFlag: 该SN该绑定项最新刷写结果非PASS视为待刷(未刷/FAIL重刷)。
     * <p>对齐 {@link #selectStationPending} 的绑定解析逻辑, 但按工单批量聚合在制SN(免逐SN拉取)。</p>
     */
    @Override
    public List<Map<String, Object>> selectPendingTasks(Long mesWoId, Long cardId, String woNo) {
        if (mesWoId == null && cardId == null && StringUtils.isBlank(woNo)) {
            // 无筛选条件: 返回空, 避免全表扫描(产线按工单/流转卡拉取)
            return new ArrayList<>();
        }
        // 1. 在制SN(按工单/流转卡筛选, 排除报废/已发运)
        LambdaQueryWrapper<ProSn> snQuery = Wrappers.<ProSn>lambdaQuery()
            .notIn(ProSn::getSnStatus, SN_STATUS_SCRAPPED, "SHIPPED")
            .orderByAsc(ProSn::getSnCode);
        if (mesWoId != null) {
            snQuery.eq(ProSn::getMesWoId, mesWoId);
        } else if (cardId != null) {
            snQuery.eq(ProSn::getCardId, cardId);
        } else {
            snQuery.like(ProSn::getWoNo, woNo.trim());
        }
        List<ProSn> snList = proSnMapper.selectList(snQuery);
        if (snList.isEmpty()) {
            return new ArrayList<>();
        }
        // SN 去重工单(mesWoId)
        List<Long> woIds = snList.stream().map(ProSn::getMesWoId).filter(java.util.Objects::nonNull).distinct().toList();
        if (woIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 2. 工单刷写工序绑定(step_type=FLASH 或含 content_ref/step_flash_config)
        List<ProWorkOrderStep> flashSteps = workOrderStepMapper.selectList(Wrappers.<ProWorkOrderStep>lambdaQuery()
            .in(ProWorkOrderStep::getMesWoId, woIds)
            .and(w -> w.eq(ProWorkOrderStep::getStepType, "FLASH")
                .or().isNotNull(ProWorkOrderStep::getContentRefId)
                .or().isNotNull(ProWorkOrderStep::getStepFlashConfig))
            .orderByAsc(ProWorkOrderStep::getMesWoId)
            .orderByAsc(ProWorkOrderStep::getSequenceNum));
        if (flashSteps.isEmpty()) {
            return new ArrayList<>();
        }
        // 按 mesWoId 分组工序绑定, 便于按 SN 的工单定位
        Map<Long, List<ProWorkOrderStep>> stepsByWo = flashSteps.stream()
            .collect(java.util.stream.Collectors.groupingBy(ProWorkOrderStep::getMesWoId));
        // 预加载固件/配置文件版本(按 contentRefId 批量查, 减少N+1)
        java.util.Set<Long> firmwareIds = new java.util.HashSet<>();
        java.util.Set<Long> contentFileIds = new java.util.HashSet<>();
        for (ProWorkOrderStep s : flashSteps) {
            for (Map<String, Object> b : parseStationBindings(s)) {
                Object refId = b.get("contentRefId");
                if (refId instanceof Long id) {
                    String tc = (String) b.get("flashTypeCode");
                    if ("config_file".equals(tc)) {
                        contentFileIds.add(id);
                    } else {
                        firmwareIds.add(id);
                    }
                }
            }
        }
        Map<Long, Firmware> firmwareMap = firmwareIds.isEmpty() ? Map.of() :
            firmwareMapper.selectBatchIds(firmwareIds).stream()
                .collect(java.util.stream.Collectors.toMap(Firmware::getFirmwareId, f -> f, (a, b) -> a));
        Map<Long, MesContentFile> contentFileMap = contentFileIds.isEmpty() ? Map.of() :
            contentFileMapper.selectBatchIds(contentFileIds).stream()
                .collect(java.util.stream.Collectors.toMap(MesContentFile::getContentFileId, c -> c, (a, b) -> a));
        // 3. 逐 SN × 绑定项组装待刷行
        List<Map<String, Object>> pending = new ArrayList<>();
        for (ProSn sn : snList) {
            List<ProWorkOrderStep> woSteps = stepsByWo.get(sn.getMesWoId());
            if (woSteps == null || woSteps.isEmpty()) {
                continue;
            }
            for (ProWorkOrderStep step : woSteps) {
                List<Map<String, Object>> bindings = filterGateEnabledBindings(parseStationBindings(step));
                if (bindings.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> b : bindings) {
                    Long refId = (Long) b.get("contentRefId");
                    String typeCode = (String) b.get("flashTypeCode");
                    FlashRecord latest = baseMapper.selectOne(Wrappers.<FlashRecord>lambdaQuery()
                        .eq(FlashRecord::getSnCode, sn.getSnCode())
                        .eq(refId != null, FlashRecord::getContentRefId, refId)
                        .eq(StringUtils.isNotBlank(typeCode), FlashRecord::getFlashTypeCode, typeCode)
                        .orderByDesc(FlashRecord::getFlashTime)
                        .orderByDesc(FlashRecord::getFlashRecordId)
                        .last("LIMIT 1"));
                    String latestResult = latest != null ? latest.getResult() : null;
                    boolean pendingFlag = !"PASS".equals(latestResult);
                    // 版本/校验码回填(固件类)
                    String contentVersion = null;
                    String contentChecksum = null;
                    if (refId != null) {
                        if ("config_file".equals(typeCode)) {
                            MesContentFile cf = contentFileMap.get(refId);
                            if (cf != null) {
                                contentVersion = cf.getVersion();
                                contentChecksum = cf.getFileMd5();
                            }
                        } else {
                            Firmware fw = firmwareMap.get(refId);
                            if (fw != null) {
                                contentVersion = fw.getVersion();
                                contentChecksum = fw.getFileMd5();
                            }
                        }
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("snId", sn.getSnId());
                    row.put("snCode", sn.getSnCode());
                    row.put("mesWoId", sn.getMesWoId());
                    row.put("woNo", sn.getWoNo());
                    row.put("cardId", sn.getCardId());
                    row.put("cardNo", sn.getCardNo());
                    row.put("materialId", sn.getMaterialId());
                    row.put("materialCode", sn.getMaterialCode());
                    row.put("materialName", sn.getMaterialName());
                    row.put("snStatus", sn.getSnStatus());
                    row.put("stepId", step.getStepId());
                    row.put("sequenceNum", step.getSequenceNum());
                    row.put("processId", step.getProcessId());
                    row.put("processCode", step.getProcessCode());
                    row.put("processName", step.getProcessName());
                    row.put("stepType", step.getStepType());
                    row.put("flashTypeCode", typeCode);
                    row.put("contentRefId", refId);
                    row.put("contentVersion", contentVersion);
                    row.put("contentChecksum", contentChecksum);
                    row.put("latestResult", latestResult);
                    row.put("flashTime", latest != null ? latest.getFlashTime() : null);
                    row.put("pendingFlag", pendingFlag);
                    pending.add(row);
                }
            }
        }
        return pending;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * C: 工序绑定一致校验(三层防错之一)
     * <p>SN 当前须处于刷写工序, 且刷写类型须在该工序绑定清单内(step_flash_config/content_ref)。
     * 工序定位依据: pro_flow_card_step(流转卡工序记录), 按 SN.card_id + 最新非完成工序定位当前工序;
     * 不依赖 pro_sn.current_step_seq(无维护逻辑, 不可靠)。
     * 防止: SN不在刷写工序却强行刷写; 工单未绑定该类型内容却刷写。</p>
     */
    private void validateFlashStepBinding(ProSn sn, String typeCode, Long contentRefId) {
        if (sn.getMesWoId() == null || sn.getCardId() == null) {
            // 无流转卡上下文: 无法定位工序, 跳过(放宽, 不阻塞历史 SN)
            return;
        }
        // 找该工单的刷写工序(step_type=FLASH 或含 content_ref/step_flash_config)
        List<ProWorkOrderStep> flashSteps = workOrderStepMapper.selectList(Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, sn.getMesWoId())
            .and(w -> w.eq(ProWorkOrderStep::getStepType, "FLASH")
                .or().isNotNull(ProWorkOrderStep::getContentRefId)
                .or().isNotNull(ProWorkOrderStep::getStepFlashConfig)));
        if (flashSteps.isEmpty()) {
            return;
        }
        // 定位 SN 当前工序(流转卡最新非完成工序); 退回 SN.current_step_seq; 都无则放宽
        Integer curSeq = null;
        ProFlowCardStep curStepRec = flowCardStepMapper.selectOne(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, sn.getCardId())
            .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
            .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED)
            .orderByAsc(ProFlowCardStep::getSequenceNum)
            .last("LIMIT 1"));
        if (curStepRec != null) {
            curSeq = curStepRec.getSequenceNum();
        } else if (sn.getCurrentStepSeq() != null) {
            curSeq = sn.getCurrentStepSeq();
        }
        if (curSeq == null) {
            return;
        }
        final Integer curSeqFinal = curSeq;
        ProWorkOrderStep curStep = flashSteps.stream()
            .filter(s -> s.getSequenceNum().equals(curSeqFinal)).findFirst().orElse(null);
        if (curStep == null) {
            throw new ServiceException("SN当前工序非刷写工序, 禁止刷写: snCode=" + sn.getSnCode()
                + " 当前工序序号=" + curSeq + ", 刷写工序序号=" + flashSteps.stream().map(s -> s.getSequenceNum().toString()).collect(java.util.stream.Collectors.joining(",")));
        }
        // 该工序须绑定当前刷写类型(content_ref 或 step_flash_config 含 typeCode)
        List<Map<String, Object>> bindings = parseStationBindings(curStep);
        boolean matched = bindings.stream().anyMatch(b -> typeCode.equals(b.get("flashTypeCode"))
            && (contentRefId == null || contentRefId.equals(b.get("contentRefId"))));
        if (!matched) {
            throw new ServiceException("当前刷写类型[" + typeCode + "]未绑定在该SN工序, 工序[" + curStep.getProcessName()
                + "]绑定的刷写类型: " + bindings.stream().map(b -> (String) b.get("flashTypeCode")).collect(java.util.stream.Collectors.joining(",")));
        }
    }

    /**
     * 校验并构造刷写记录(确认/上报共用): SN 存在且非报废 -> 固件存在且已发布 -> 设备存在(可选)
     * -> 自动编码 + 冗余回填 + 默认操作人/时间/重试次数
     */
    private FlashRecord buildValidatedRecord(FlashRecordBo bo) {
        if (StringUtils.isBlank(bo.getSnCode())) {
            throw new ServiceException("SN编码不能为空");
        }
        if (bo.getContentRefId() == null && StringUtils.isBlank(bo.getFlashTypeCode())) {
            throw new ServiceException("请选择刷写内容(固件/配置/MAC等)");
        }
        if (StringUtils.isBlank(bo.getResult())) {
            throw new ServiceException("请选择刷写结果");
        }
        // SN 校验: 必须存在且非报废
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, bo.getSnCode().trim()));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + bo.getSnCode());
        }
        if (SN_STATUS_SCRAPPED.equals(sn.getSnStatus()) || "SHIPPED".equals(sn.getSnStatus())) {
            throw new ServiceException("SN已报废或已发运, 禁止刷写: " + bo.getSnCode() + " 状态=" + sn.getSnStatus());
        }
        // 刷写类型路由: flashTypeCode 缺省 firmware_file(向后兼容固件刷写)
        String typeCode = StringUtils.isBlank(bo.getFlashTypeCode()) ? "firmware_file" : bo.getFlashTypeCode();
        // MES-FLASH-05: 停用(DISABLED)类型必须不生效 —— 只有 ENABLED 类型可刷写, 查不到一律拒绝
        MesFlashType flashType = mesFlashTypeMapper.selectOne(Wrappers.<MesFlashType>lambdaQuery()
            .eq(MesFlashType::getTypeCode, typeCode)
            .eq(MesFlashType::getStatus, "ENABLED"));
        if (flashType == null) {
            throw new ServiceException("刷写类型已停用或不存在: " + typeCode);
        }
        // MES-FLASH-04: 重试次数服务端按历史条数计算(不信任客户端自报), 且超过阈值直接拒绝, 须走 NCR/返工
        int historyCount = countHistory(sn.getSnCode(), typeCode, bo.getContentRefId());
        if (historyCount >= maxRetry) {
            throw new ServiceException("刷写重试次数已达上限(" + maxRetry + "次), 拒绝再次刷写, 请走 NCR/返工流程: snCode="
                + sn.getSnCode() + ", flashType=" + typeCode + ", 历史记录=" + historyCount);
        }
        // MES-FLASH-04: 已存在 PASS 的记录再报 FAIL 必须给出失败原因并留审计日志,
        // 不允许一条无原因的 FAIL 静默把完工门禁打回"待刷"(判定仍取最新一条, 语义不变)
        if (RESULT_FAIL.equals(bo.getResult())) {
            FlashRecord lastRecord = baseMapper.selectOne(Wrappers.<FlashRecord>lambdaQuery()
                .eq(FlashRecord::getSnCode, sn.getSnCode())
                .eq(FlashRecord::getFlashTypeCode, typeCode)
                .eq(bo.getContentRefId() != null, FlashRecord::getContentRefId, bo.getContentRefId())
                .orderByDesc(FlashRecord::getFlashTime)
                .orderByDesc(FlashRecord::getFlashRecordId)
                .last("LIMIT 1"));
            if (lastRecord != null && RESULT_PASS.equals(lastRecord.getResult())) {
                if (StringUtils.isBlank(bo.getFailReason())) {
                    throw new ServiceException("该SN该刷写内容已存在PASS记录, 再报FAIL必须填写失败原因(failReason): snCode="
                        + sn.getSnCode() + ", flashType=" + typeCode);
                }
                log.warn("刷写结果由PASS被打回FAIL(审计留痕): snCode={}, flashType={}, 上一条flashNo={}, 失败原因={}",
                    sn.getSnCode(), typeCode, lastRecord.getFlashNo(), bo.getFailReason());
            }
        }
        // C: 工序绑定一致校验 — SN当前工序须为刷写工序, 且刷写内容须在该工序绑定清单内
        validateFlashStepBinding(sn, typeCode, bo.getContentRefId());
        FlashTypeProvider provider = flashTypeRegistry.getProvider(typeCode);
        FlashContext ctx = FlashContext.builder()
            .snId(sn.getSnId()).snCode(sn.getSnCode())
            .mesWoId(sn.getMesWoId()).woNo(sn.getWoNo()).cardId(sn.getCardId())
            .processId(bo.getProcessId()).processCode(bo.getProcessCode()).processName(bo.getProcessName())
            .stepRecordId(bo.getStepRecordId())
            .flashTypeId(flashType.getFlashTypeId()).flashTypeCode(typeCode)
            .snMaterialId(sn.getMaterialId())
            .contentRefId(bo.getContentRefId())
            .build();
        // Provider 解析应刷内容(固件类: 查 mes_firmware 校验 RELEASED + 取 version/md5)
        FlashContent expected = provider.resolveContent(ctx);
        // 设备校验(可选)
        if (bo.getEquipmentId() != null && equipmentMapper.selectById(bo.getEquipmentId()) == null) {
            throw new ServiceException("设备不存在: " + bo.getEquipmentId());
        }
        // Provider 校验上报结果(固件类: MD5 匹配); 上报 PASS 但校验 FAIL -> 拦截
        FlashVerifyResult verify = provider.verify(ctx, expected, bo);
        if ("PASS".equals(bo.getResult()) && !verify.isPass()) {
            throw new ServiceException(verify.getFailReason());
        }
        FlashRecord record = new FlashRecord();
        record.setFlashNo(autoCodeUtil.genSerialCode("MES_FLASH_NO", null));
        record.setFlashTypeId(flashType.getFlashTypeId());
        record.setFlashTypeCode(typeCode);
        record.setSnId(sn.getSnId());
        record.setSnCode(sn.getSnCode());
        record.setMesWoId(sn.getMesWoId());
        record.setWoNo(sn.getWoNo());
        record.setCardId(sn.getCardId());
        record.setContentRefType(expected.getContentRefType());
        record.setContentRefId(expected.getContentRefId());
        record.setContentVersion(verify.getContentVersion() != null ? verify.getContentVersion() : expected.getContentVersion());
        record.setContentChecksum(verify.getContentChecksum() != null ? verify.getContentChecksum() : expected.getContentChecksum());
        record.setContentPayload(verify.getContentPayload() != null ? verify.getContentPayload() : expected.getContentPayload());
        record.setEquipmentId(bo.getEquipmentId());
        record.setResult(bo.getResult());
        record.setFailReason(bo.getFailReason());
        // MES-FLASH-04: retry_count 服务端计算(历史条数), 客户端传入值一律忽略
        record.setRetryCount(historyCount);
        record.setFlashMode(StringUtils.isBlank(bo.getFlashMode()) ? MODE_AUTO : bo.getFlashMode());
        record.setProcessId(bo.getProcessId());
        record.setProcessCode(bo.getProcessCode());
        record.setProcessName(bo.getProcessName());
        record.setStepRecordId(bo.getStepRecordId());
        record.setRemark(bo.getRemark());
        record.setFlashTime(bo.getFlashTime() == null ? new Date() : bo.getFlashTime());
        applyOperator(record, bo);
        enrichRedundant(record);
        // MES-FLASH-06: 池类刷写结果回写资源项 —— PASS 置 USED(已消耗) / FAIL 回收 AVAILABLE, 解决"只分配不回收"
        // (与记录落库同事务: 记录插入失败会一并回滚, 不会出现"资源已回收但记录未落库")
        String poolResourceType = provider.getPoolResourceType();
        if (StringUtils.isNotBlank(poolResourceType)) {
            resourcePoolService.applyFlashResult(sn.getSnId(), poolResourceType, RESULT_PASS.equals(record.getResult()),
                "刷写" + record.getResult() + ": flashNo=" + record.getFlashNo()
                    + (StringUtils.isNotBlank(record.getFailReason()) ? ", 原因=" + record.getFailReason() : ""));
        }
        return record;
    }

    /**
     * MES-FLASH-04: 统计同一 (snCode, flashTypeCode, contentRefId) 的历史刷写条数, 作为服务端 retry_count。
     * <p>contentRefId 为空时按 (snCode, flashTypeCode) 统计, 与门禁/待刷清单的"最新一条"守卫口径一致
     * (无条件拼 content_ref_id = NULL 会永不匹配)。</p>
     */
    private int countHistory(String snCode, String typeCode, Long contentRefId) {
        Long count = baseMapper.selectCount(Wrappers.<FlashRecord>lambdaQuery()
            .eq(FlashRecord::getSnCode, snCode)
            .eq(FlashRecord::getFlashTypeCode, typeCode)
            .eq(contentRefId != null, FlashRecord::getContentRefId, contentRefId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 冗余字段回填: SN(pro_sn)、固件版本(mes_firmware)、设备编码(eq_equipment)
     */
    /**
     * MES-FLASH-05: 按刷写类型的 {@code gate_enabled} 过滤工序绑定项, 让该列真正生效。
     * <p>列定义为 {@code NOT NULL DEFAULT '1'}, 故显式 {@code '0'}(不参与完工门禁)才排除;
     * 类型行缺失时按"参与门禁"处理(待刷/门禁清单是只读视图, 保持向后兼容, 不因主数据缺失而少列任务)。</p>
     * <p><b>已对齐(2026-09-20)</b>: 权威门禁 {@code ProFlowCardServiceImpl.checkFlashStepGate}
     * 已通过同类过滤逻辑对齐 {@code gate_enabled}(见其 {@code filterGateEnabledBindings} 调用)，两处口径一致。</p>
     */
    private List<Map<String, Object>> filterGateEnabledBindings(List<Map<String, Object>> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return bindings == null ? new ArrayList<>() : bindings;
        }
        List<String> typeCodes = bindings.stream()
            .map(b -> (String) b.get("flashTypeCode"))
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
        if (typeCodes.isEmpty()) {
            return bindings;
        }
        Map<String, String> gateByType = mesFlashTypeMapper.selectList(Wrappers.<MesFlashType>lambdaQuery()
                .in(MesFlashType::getTypeCode, typeCodes))
            .stream()
            .collect(java.util.stream.Collectors.toMap(MesFlashType::getTypeCode,
                t -> t.getGateEnabled() == null ? "" : t.getGateEnabled(), (a, b) -> a));
        return bindings.stream()
            .filter(b -> !"0".equals(gateByType.get((String) b.get("flashTypeCode"))))
            .toList();
    }

    /**
     * 解析工序刷写绑定(工位编排用): step_flash_config 多类型优先, 否则单类型 content_ref_id(固件类)
     */
    private List<Map<String, Object>> parseStationBindings(ProWorkOrderStep step) {
        List<Map<String, Object>> bindings = new ArrayList<>();
        if (StringUtils.isNotBlank(step.getStepFlashConfig())) {
            try {
                JsonNode arr = objectMapper.readTree(step.getStepFlashConfig());
                for (JsonNode b : arr) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("flashTypeCode", b.has("flashTypeCode") ? b.get("flashTypeCode").asText() : "firmware_file");
                    m.put("contentRefId", b.has("contentRefId") && b.get("contentRefId").canConvertToLong() ? b.get("contentRefId").asLong() : null);
                    bindings.add(m);
                }
            } catch (Exception e) {
                log.warn("解析 step_flash_config 失败: stepId={}, err={}", step.getStepId(), e.getMessage());
            }
        }
        if (bindings.isEmpty() && step.getContentRefId() != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("flashTypeCode", "firmware_file");
            m.put("contentRefId", step.getContentRefId());
            bindings.add(m);
        }
        return bindings;
    }

    /**
     * 操作人赋值: 登录态优先(PC 端); 免登录设备通道**不信任**客户端传入的 operatorId/operatorName
     * (MES-FLASH-09: 该通道仅共享 API-Key, 可被冒名), 仅把上报工号/姓名以"未验证"来源标注落 operator_name 供展示,
     * operator_id 一律留空(不落客户端用户ID)。
     */
    private void applyOperator(FlashRecord record, FlashRecordBo bo) {
        if (LoginHelper.isLogin()) {
            record.setOperatorId(LoginHelper.getUserId());
            record.setOperatorName(LoginHelper.getUsername());
            return;
        }
        String reported = StringUtils.isNotBlank(bo.getOperatorCode()) ? bo.getOperatorCode().trim()
            : (StringUtils.isNotBlank(bo.getOperatorName()) ? bo.getOperatorName().trim() : null);
        if (reported != null) {
            record.setOperatorName(markDeviceReported(reported));
        }
    }

    /** 设备通道上报的操作人标识: 明确标注来源与"未验证", 与登录态可信身份区分 */
    private String markDeviceReported(String reported) {
        String text = "设备上报:" + reported + "(未验证)";
        return text.length() > 64 ? text.substring(0, 64) : text;
    }

    private void enrichRedundant(FlashRecord record) {
        if (record.getSnId() != null && StringUtils.isBlank(record.getSnCode())) {
            ProSn sn = proSnMapper.selectById(record.getSnId());
            if (sn != null) {
                record.setSnCode(sn.getSnCode());
                if (record.getMesWoId() == null) {
                    record.setMesWoId(sn.getMesWoId());
                    record.setWoNo(sn.getWoNo());
                    record.setCardId(sn.getCardId());
                }
            }
        }
        // 内容字段(version/checksum/payload)由 FlashTypeProvider.resolveContent/verify 提供, 此处不再回填固件
        if (record.getEquipmentId() != null && StringUtils.isBlank(record.getEquipmentCode())) {
            EqEquipment eq = equipmentMapper.selectById(record.getEquipmentId());
            if (eq != null) {
                record.setEquipmentCode(eq.getEquipmentCode());
            }
        }
    }
}